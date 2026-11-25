package lab.reliability.analyzer

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// docs/measurement.md 11절(전송 대조 — `analyzer transport`)의 입력(11-1)대로 작은 실행 기록을 손으로 만든다.
// 이 파일은 분석기의 구현을 보지 않고 문서(7절, 11절, docs/experiments/task-05-design.md)만으로 쓴다.
// 정답 줄·DB 행·표본은 TimingRecord 로 쓰고(시각 분석도 같은 폴더를 받게), 그 위에 세 서비스의 설정(transport·grpc),
// transport-counts.json, 칸 넷(transport·deadlineUs·cancelledUs·stopped)이 든 시각 기록을 더한다.

/** 시각 기록 한 줄(7-1 + 11-1의 칸 넷). [transport]가 null 이면 그 칸을 뺀다(옛 기록). */
data class GrpcTiming(
    val layer: String,
    val id: String,
    val startUs: Long,
    val endUs: Long,
    val status: Int = 201,
    val callStartUs: Long? = null,
    val callEndUs: Long? = null,
    val callOutcome: String? = null,
    val transport: String? = "grpc",
    val deadlineUs: Long? = null,
    val cancelledUs: Long? = null,
    val stopped: String? = null,
    /** 키 자체를 뺄 새 칸("deadlineUs"·"cancelledUs"·"stopped") — null 값과 다른 꼴(11-3: 키가 없으면 null 과 같다). */
    val absentKeys: Set<String> = emptySet(),
) {
    fun json(): String {
        fun q(s: String?) = s?.let { "\"$it\"" } ?: "null"
        val base = """"requestId":"$id","startUs":$startUs,"endUs":$endUs,"status":$status,"callStartUs":$callStartUs,"callEndUs":$callEndUs,"callOutcome":${q(callOutcome)}"""
        val extra = if (transport == null) "" else
            ",\"transport\":${q(transport)}" +
                listOf("deadlineUs" to "$deadlineUs", "cancelledUs" to "$cancelledUs", "stopped" to q(stopped))
                    .filter { it.first !in absentKeys }
                    .joinToString("") { ",\"${it.first}\":${it.second}" }
        return "{$base$extra}"
    }
}

class TransportRecord(
    root: Path,
    /** 세 서비스가 고른 전송(T) — 바꾸려면 [chosenOf]. */
    val chosen: String = "grpc",
    propagation: Boolean = chosen == "grpc",
    observe: Boolean = false,
) {
    val timing = TimingRecord(root, "transport-20261125T000000-r1")
    val dir: Path get() = timing.dir

    val chosenOf = LAYERS.associateWith { chosen }.toMutableMap()
    val propagationOf = LAYERS.associateWith { propagation }.toMutableMap()
    val observeOf = LAYERS.associateWith { observe }.toMutableMap()

    /** 값이 있으면 그 서비스의 settings-<서비스>.json 을 이 내용 그대로 쓴다. */
    val settingsText = mutableMapOf<String, String>()
    var countsText: String? = null

    /** 값이 있으면 그 서비스의 timing/timings-<서비스>.json 을 이 내용 그대로 쓴다. */
    val timingsText = mutableMapOf<String, String>()

    /** 기록에서 셈한 수에 더할 값 — 키 "<서비스>.<inbound/outbound>.<http/grpc>". */
    val countDelta = mutableMapOf<String, Long>()

    /** 서비스마다 grpcCancelledBeforeHandler(기본 0). 재고·결제가 gRPC로 받으면 들어온 수에도 더해진다. */
    val cancelledBeforeHandler = mutableMapOf<String, Long>()

    /**
     * 서비스마다 설정의 `retry` 칸(JSON 그대로 — 예 `{"enabled":true}`, `"없음(맨 아래 층)"`). 없으면 그 칸을 쓰지 않는다
     * (11-3 ④: retry 가 객체가 아니거나 enabled 가 없으면 꺼진 층).
     */
    val retryJson = mutableMapOf<String, String>()

    /** 서비스마다 timing/attempts-<서비스>.json 의 내용(JSON 그대로). 없으면 그 파일을 쓰지 않는다. */
    val attemptsText = mutableMapOf<String, String>()

    /** 쓰지 않을 파일: "settings-payment.json", "transport-counts.json", "timing/timings-order.json" 등 */
    val omit = mutableSetOf<String>()

    val records = mutableListOf<GrpcTiming>()

    fun add(r: GrpcTiming): TransportRecord {
        records += r
        return this
    }

    /** 이미 넣은 기록 하나를 바꾼다(층과 id 로 찾는다). */
    fun change(layer: String, id: String, f: (GrpcTiming) -> GrpcTiming): TransportRecord {
        val i = records.indexOfFirst { it.layer == layer && it.id == id }
        check(i >= 0) { "$layer/$id 기록이 없다" }
        records[i] = f(records[i])
        return this
    }

    private val lowerTransport get() = chosen // 재고·결제가 받는 전송(T)

    /**
     * 모두 맞는 사슬 요청 하나(오프셋 ms): order 1~19(call 2~18), inventory 3~17(call 4~16), payment 5~15.
     * gRPC면 재고의 기한 = 주문의 call 시작 + 2000ms, 결제의 기한 = 재고의 call 시작 + 1000ms(결제 − 재고 = −998ms).
     */
    fun okChain(id: String, s: Long): TransportRecord {
        val grpc = lowerTransport == "grpc"
        timing.answer(id, s, 201, "", 20)
        timing.row("order", id, "COMPLETED").row("inventory", id, "RESERVED").row("payment", id, "APPROVED")
        add(GrpcTiming("order", id, us(s + 1), us(s + 19), 201, us(s + 2), us(s + 18), "Completed", "http"))
        add(
            GrpcTiming(
                "inventory", id, us(s + 3), us(s + 17), 201, us(s + 4), us(s + 16), "Completed", lowerTransport,
                deadlineUs = if (grpc) us(s + 2 + 2000) else null,
            ),
        )
        add(
            GrpcTiming(
                "payment", id, us(s + 5), us(s + 15), 201, transport = lowerTransport,
                deadlineUs = if (grpc) us(s + 4 + 1000) else null,
            ),
        )
        return this
    }

    private fun countsJson(): String {
        fun recs(svc: String) = records.filter { it.layer == svc }
        val parts = LAYERS.map { svc ->
            val inbound = mutableMapOf("http" to 0L, "grpc" to 0L)
            val outbound = mutableMapOf("http" to 0L, "grpc" to 0L)
            val cbh = cancelledBeforeHandler[svc] ?: 0L
            if (svc == "order") {
                inbound["http"] = recs(svc).size.toLong()
            } else {
                inbound[chosen] = recs(svc).size.toLong() + (if (chosen == "grpc") cbh else 0L)
            }
            if (svc != "payment") outbound[chosen] = recs(svc).count { it.callOutcome != null }.toLong()
            for (t in listOf("http", "grpc")) {
                inbound[t] = inbound.getValue(t) + (countDelta["$svc.inbound.$t"] ?: 0L)
                outbound[t] = outbound.getValue(t) + (countDelta["$svc.outbound.$t"] ?: 0L)
            }
            """"$svc":{"inbound":{"http":${inbound["http"]},"grpc":${inbound["grpc"]}},"outbound":{"http":${outbound["http"]},"grpc":${outbound["grpc"]}},"grpcCancelledBeforeHandler":$cbh}"""
        }
        return parts.joinToString(",", "{", "}")
    }

    fun settingsJson(svc: String): String {
        val next = if (svc == "payment") "" else
            ""","nextLayer":{"requestTimeoutMs":${if (svc == "order") 2000 else 1000}}"""
        val retry = retryJson[svc]?.let { ""","retry":$it""" } ?: ""
        return """{"service":"$svc"$next,"transport":{"chosen":"${chosenOf[svc]}"},"grpc":{"deadlinePropagation":${propagationOf[svc]},"observeCancellation":${observeOf[svc]}}$retry}"""
    }

    fun write() {
        for (svc in LAYERS) timing.settingsText[svc] = settingsText[svc] ?: settingsJson(svc)
        for (svc in LAYERS) {
            timing.timingsText[svc] = timingsText[svc]
                ?: records.filter { it.layer == svc }.joinToString(",", "[", "]") { it.json() }
        }
        timing.omit += omit.filter { it.startsWith("timing/") || it == "settings-order.json" || it == "settings-inventory.json" }
        timing.write()
        val payment = dir.resolve("settings-payment.json")
        if ("settings-payment.json" !in omit) payment.writeText(timing.settingsText.getValue("payment"))
        if ("transport-counts.json" !in omit) dir.resolve("transport-counts.json").writeText(countsText ?: countsJson())
        for ((svc, text) in attemptsText) dir.resolve("timing/attempts-$svc.json").writeText(text)
    }

    var lastOutput: String = ""

    /** `analyzer transport --run <폴더> [extra]` — 종료 코드 */
    fun runTransport(vararg extra: String): Int {
        write()
        val (code, out) = analyzerCli("transport", "--run", dir.toString(), *extra)
        lastOutput = out
        return code
    }

    val reportFile: Path get() = dir.resolve("report-transport.json")
    val tsvFile: Path get() = dir.resolve("metrics-transport.tsv")

    @Suppress("UNCHECKED_CAST")
    fun report(): Map<String, Any?> = RunRecord.MAPPER.readValue(reportFile.readText(), Map::class.java) as Map<String, Any?>

    /** metrics-transport.tsv 를 이름 → 값(문자열 그대로)으로. */
    fun tsv(): Map<String, String> = tsvFile.readLines().filter { it.isNotEmpty() }.associate {
        val i = it.indexOf('\t')
        check(i > 0) { "탭이 없는 줄: $it" }
        it.substring(0, i) to it.substring(i + 1)
    }

    /** 문서의 입구(11-5) `transportReport(run)` 의 차이 줄. */
    fun differences(): List<String> {
        write()
        return transportReport(dir).second
    }

    /** 종료 코드 [code]를 기대하고 차이 줄을 돌려준다. */
    fun expectCode(code: Int, vararg extra: String): List<String> {
        val actual = runTransport(*extra)
        assertEquals(code, actual, "종료 코드 $code 을 기대했다: $lastOutput")
        return transportReport(dir).second
    }

    /** 입력 오류(11-3): 종료 코드 1, 두 출력 파일을 쓰지 않고 있던 것은 지운다. */
    fun assertInputError(vararg extra: String) {
        dir.resolve("report-transport.json").writeText("""{"old":true}""")
        dir.resolve("metrics-transport.tsv").writeText("old\t1\n")
        val code = runTransport(*extra)
        assertEquals(1, code, "입력 오류는 종료 코드 1: $lastOutput")
        assertFalse(reportFile.exists(), "입력 오류면 report-transport.json 을 쓰지 않고 있던 것은 지운다")
        assertFalse(tsvFile.exists(), "입력 오류면 metrics-transport.tsv 를 쓰지 않고 있던 것은 지운다")
    }
}
