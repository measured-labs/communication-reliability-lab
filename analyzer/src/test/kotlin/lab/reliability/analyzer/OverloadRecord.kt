package lab.reliability.analyzer

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// docs/measurement.md 10절(한도 지표 — `analyzer overload`)의 입력(10-2)대로 작은 실행 기록을 손으로 만든다.
// 이 파일은 분석기의 구현을 보지 않고 문서(10절, docs/experiments/task-04b-design.md)만으로 쓴다.
// 10-2의 입력만 쓴다: answers.jsonl(sentAtMs·phase·attemptFault), timing/timings-payment.json·timings-inventory.json,
// settings-order/inventory/payment.json(overload 칸만), overload-counts.json. DB·fault-counts.json·주문의 시각 기록은 쓰지 않는다.

/** 결제에 든 호출 하나 — 시각(μs)과 상태. */
data class Pay(val startUs: Long, val status: Int)

/** 요청 하나가 결제에 한 번 드는 시각: 보낸 ms + 0.6ms. 든 시각(÷1000, 버림)은 보낸 ms 와 같다(반올림하면 +1). */
fun payAt(sentAtMs: Long): Long = us(sentAtMs) + 600

class OverloadRecord(root: Path, name: String = "overload-20261125T000000-r1") {
    val dir: Path = root.resolve(name).also { it.createDirectories() }

    private val answerLines = mutableListOf<String>()
    private val timings = mapOf("inventory" to mutableListOf<String>(), "payment" to mutableListOf<String>())

    var limit: Any = 3
    var windowMs: Any = 100

    /** 값이 있으면 그 파일을 이 내용 그대로 쓴다. */
    var answersText: String? = null
    val timingsText = mutableMapOf<String, String>()
    val settingsText = mutableMapOf<String, String>()
    var countsText: String? = null

    /** 쓰지 않을 파일: "answers.jsonl", "timing/timings-payment.json", "settings-payment.json", "overload-counts.json" … */
    val omit = mutableSetOf<String>()

    /** overload-counts.json 의 값을 바꾼다(주지 않으면 결제는 시각 기록에서 센다, 주문·재고는 0). */
    val counts = mutableMapOf<Pair<String, String>, Long>()

    private var paymentArrivals = 0L
    private var paymentRejected = 0L

    fun rawAnswer(line: String): OverloadRecord {
        answerLines += line
        return this
    }

    fun answer(id: String, sentAtMs: Long, phase: String, status: Int, elapsedMs: Number): OverloadRecord =
        rawAnswer(
            """{"type":"answer","requestId":"$id","fault":"","amount":${amountFor(id)},"sentAtMs":$sentAtMs,"status":$status,"errorCode":0,"elapsedMs":$elapsedMs,"attemptFault":"","phase":"$phase"}""",
        )

    fun rawTiming(layer: String, json: String): OverloadRecord {
        timings.getValue(layer) += json
        return this
    }

    fun paymentTiming(id: String, p: Pay): OverloadRecord {
        paymentArrivals++
        if (p.status == 503) paymentRejected++
        return rawTiming(
            "payment",
            """{"requestId":"$id","startUs":${p.startUs},"endUs":${p.startUs + 100},"status":${p.status},"callStartUs":null,"callEndUs":null,"callOutcome":null}""",
        )
    }

    /**
     * 사슬 요청 하나. [pays]는 재고→결제의 시도마다 결제에 든 시각과 상태(재시도면 여럿 — 10-2: 결제는 한 requestId 의 기록이 여럿이어도 된다).
     * 마지막 시도가 201 이면 재고 `Completed`·201, 클라이언트 201. 503 이면 재고 `Failed:HTTP_503`·502, 클라이언트 502.
     * 재고의 시각 기록: 첫 결제 −200us ~ 마지막 결제의 끝 +200us, call 칸은 마지막 시도(시작 −100us ~ 끝 +100us).
     * 경과 = 재고의 끝 + 100us − 보낸 시각(ms, 올림). [status]를 주면 클라이언트의 상태만 그것으로 바꾼다.
     */
    fun request(
        id: String,
        sentAtMs: Long,
        phase: String = "base",
        pays: List<Pay> = listOf(Pay(payAt(sentAtMs), 201)),
        status: Int? = null,
        elapsedMs: Number? = null,
    ): OverloadRecord {
        for (p in pays) paymentTiming(id, p)
        val last = pays.last()
        val ok = last.status == 201
        val invStart = pays.first().startUs - 200
        val invEnd = last.startUs + 100 + 200
        rawTiming(
            "inventory",
            """{"requestId":"$id","startUs":$invStart,"endUs":$invEnd,"status":${if (ok) 201 else 502},"callStartUs":${last.startUs - 100},"callEndUs":${last.startUs + 200},"callOutcome":"${if (ok) "Completed" else "Failed:HTTP_${last.status}"}"}""",
        )
        val elapsed = elapsedMs ?: ((invEnd + 100 - us(sentAtMs) + 999) / 1000)
        return answer(id, sentAtMs, phase, status ?: (if (ok) 201 else 502), elapsed)
    }

    /** 결제에서 한 번 거절된 요청(재시도 없음). */
    fun rejected(id: String, sentAtMs: Long, phase: String = "base"): OverloadRecord =
        request(id, sentAtMs, phase, listOf(Pay(payAt(sentAtMs), 503)))

    fun settingsJson(service: String): String =
        if (service == "payment") {
            """{"service":"payment","overload":{"enabled":true,"limit":$limit,"windowMs":$windowMs,"rule":"sliding-window"}}"""
        } else {
            """{"service":"$service","overload":{"enabled":false}}"""
        }

    fun write() {
        if ("answers.jsonl" !in omit) {
            dir.resolve("answers.jsonl").writeText(answersText ?: answerLines.joinToString("\n", postfix = "\n"))
        }
        val timingDir = dir.resolve("timing").createDirectories()
        for ((layer, list) in timings) {
            if ("timing/timings-$layer.json" !in omit) {
                timingDir.resolve("timings-$layer.json").writeText(timingsText[layer] ?: list.joinToString(",", "[", "]"))
            }
        }
        for (s in LAYERS) {
            if ("settings-$s.json" !in omit) dir.resolve("settings-$s.json").writeText(settingsText[s] ?: settingsJson(s))
        }
        if ("overload-counts.json" !in omit) {
            fun c(s: String, k: String, default: Long) = counts[s to k] ?: default
            val text = countsText ?: LAYERS.joinToString(",", "{", "}") { s ->
                val a = c(s, "arrivals", if (s == "payment") paymentArrivals else 0)
                val r = c(s, "rejected", if (s == "payment") paymentRejected else 0)
                """"$s":{"arrivals":$a,"rejected":$r}"""
            }
            dir.resolve("overload-counts.json").writeText(text)
        }
    }

    var lastOutput: String = ""

    /** `analyzer overload --run <폴더> [extra]` — 종료 코드 */
    fun runOverload(vararg extra: String): Int {
        write()
        val (code, out) = analyzerCli("overload", "--run", dir.toString(), *extra)
        lastOutput = out
        return code
    }

    val reportFile: Path get() = dir.resolve("report-overload.json")
    val metricsTsv: Path get() = dir.resolve("metrics-overload.tsv")

    @Suppress("UNCHECKED_CAST")
    fun report(): Map<String, Any?> = RunRecord.MAPPER.readValue(reportFile.readText(), Map::class.java) as Map<String, Any?>

    /** 종료 코드 0(불일치 없음)이어야 한다. */
    fun analyze(vararg extra: String): Map<String, Any?> {
        val code = runOverload(*extra)
        check(code == 0) { "종료 코드 0을 기대했는데 $code: $lastOutput" }
        return report()
    }

    /** 불일치(종료 코드 2)여야 한다 — 보고를 돌려준다. */
    fun analyzeMismatch(vararg extra: String): Map<String, Any?> {
        val code = runOverload(*extra)
        check(code == 2) { "종료 코드 2를 기대했는데 $code: $lastOutput" }
        return report()
    }

    /** 입력 오류(10-4): 종료 코드 1, report-overload.json·metrics-overload.tsv 를 쓰지 않는다. */
    fun assertInputError(vararg extra: String) {
        val code = runOverload(*extra)
        assertEquals(1, code, "입력 오류는 종료 코드 1: $lastOutput")
        assertFalse(reportFile.exists(), "입력 오류면 report-overload.json 을 쓰지 않는다")
        assertFalse(metricsTsv.exists(), "입력 오류면 metrics-overload.tsv 를 쓰지 않는다")
    }
}

/**
 * 테스트 여럿이 함께 쓰는 기준 기록 — 한도 L 3, 창 W 100ms(Q = 200ms). 시각은 T 기준 ms, 결제에 드는 시각은 보낸 ms + 0.6ms.
 * 손계산(창 (t − 100, t], 거절 포함):
 * - 평소 b1(0) b2(120) b3(240): 창에 자기와 앞의 하나 이하 → 받음.
 * - 구간 s1~s6(300~305, 1ms 간격): 300 → {240, 300} 2, 301 → 3, 302 → 4 거절, 303 → 5, 304 → 6, 305 → 7 거절.
 * - 평소 a1(360): 창 (260, 360] = 구간 여섯(거절 넷 포함) + 자기 = 7 → 거절(되먹임 — 받은 것만 세면 3 으로 받았을 것).
 * - 평소 a2(480): 창 (380, 480] = 자기 → 받음. a3(600) a4(720) a5(840) 받음.
 * 정답 14(구간 6, 평소 8), 완료 9, 실패 5(502). 결제 든 수 14, 거절 5(302, 303, 304, 305, 360).
 * 구간 300~305, lastSent 840. 결제 lastRejected 360 → 멈춤 55ms, 360 ≤ 840 − 200 → 회복. 클라이언트 lastBad 360 → 55ms.
 */
fun goodOverloadRecord(root: Path, after: List<Long> = listOf(480, 600, 720, 840)): OverloadRecord {
    val r = OverloadRecord(root)
    r.request("b1", T).request("b2", T + 120).request("b3", T + 240)
    r.request("s1", T + 300, "spike").request("s2", T + 301, "spike")
    for (i in 3..6) r.rejected("s$i", T + 299 + i, "spike")
    r.rejected("a1", T + 360)
    for ((i, t) in after.withIndex()) r.request("a${i + 2}", T + t)
    return r
}
