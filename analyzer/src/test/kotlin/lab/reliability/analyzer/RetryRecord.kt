package lab.reliability.analyzer

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// docs/measurement.md 8절(재시도 지표 — `analyzer retry`)의 입력(8-2)대로 작은 실행 기록을 손으로 만든다.
// 이 파일은 분석기의 구현을 보지 않고 문서(8절, docs/retry.md, docs/experiments/task-03-design.md)만으로 쓴다.
// 1·2절의 정답 줄·DB 행은 RunRecord 로 쓰고, 그 위에 sentAtMs·attemptFault·시각 기록·시도 기록·넣은 실패의 수를 더한다.
// 8-2의 입력이 아닌 파일(표본, 조회, settings-payment.json)은 쓰지 않는다. 설정은 settings-order.json·settings-inventory.json 의
// `retry.enabled`(8-2 — 그 층의 재시도 감싸개가 떠 있었나)만 쓴다. 기본: 주문 꺼짐, 재고 켜짐.

val ATTEMPT_KINDS = listOf("fail-first", "slow-first", "outage")

/** 재시도 감싸개의 시도 하나(8-2, docs/retry.md 5절). */
data class Attempt(
    val requestId: String,
    val callId: Long,
    val attempt: Int,
    val startUs: Long,
    val endUs: Long,
    val outcome: String,
    val delayBeforeMs: Long,
    val decision: String,
) {
    fun json(): String =
        """{"requestId":"$requestId","callId":$callId,"attempt":$attempt,"startUs":$startUs,"endUs":$endUs,"outcome":"$outcome","delayBeforeMs":$delayBeforeMs,"decision":"$decision"}"""
}

private data class ChainEnd(val invStatus: Int, val invRow: String, val orderOutcome: String, val orderStatus: Int, val orderRow: String)

class RetryRecord(root: Path, name: String = "retry-20261124T000000-r1") {
    val base = RunRecord(root, name)
    val dir: Path get() = base.dir

    private val timings = LAYERS.associateWith { mutableListOf<String>() }

    /** 시도 기록(주문·재고). 테스트가 원소를 바꿔 한 칸만 틀리게 만들 수 있다. */
    val attempts = mapOf("order" to mutableListOf<Attempt>(), "inventory" to mutableListOf<Attempt>())

    /** 값이 있으면 그 파일을 이 내용 그대로 쓴다. */
    val timingsText = mutableMapOf<String, String>()
    val attemptsText = mutableMapOf<String, String>()
    var attemptFaultsText: String? = null
    val settingsText = mutableMapOf<String, String>()

    /** 층마다 `retry.enabled`(settings-<층>.json). */
    val retryEnabled = mutableMapOf("order" to false, "inventory" to true)

    /** 쓰지 않을 파일: "timing/attempts-order.json", "attempt-faults.json", "timing/timings-payment.json", "settings-order.json" … */
    val omit = mutableSetOf<String>()

    private val counted = mutableMapOf<Pair<String, String>, Int>()
    private val callIds = mutableMapOf("order" to 0L, "inventory" to 0L)

    fun nextCallId(layer: String): Long {
        val id = callIds.getValue(layer) + 1
        callIds[layer] = id
        return id
    }

    fun answer(
        id: String,
        sentAtMs: Long,
        status: Int = 201,
        attemptFault: String = "",
        elapsedMs: Number = 20,
        amount: Long = amountFor(id),
    ): RetryRecord {
        base.rawAnswer(
            """{"type":"answer","requestId":"$id","fault":"","amount":$amount,"sentAtMs":$sentAtMs,"status":$status,"errorCode":0,"elapsedMs":$elapsedMs,"attemptFault":"$attemptFault"}""",
        )
        return this
    }

    fun rawAnswer(line: String): RetryRecord {
        base.rawAnswer(line)
        return this
    }

    fun row(layer: String, id: String, status: String): RetryRecord {
        base.row(layer, id, status)
        return this
    }

    fun timing(
        layer: String,
        id: String,
        startUs: Long,
        endUs: Long,
        status: Int = 201,
        callStartUs: Long? = null,
        callEndUs: Long? = null,
        callOutcome: String? = null,
    ): RetryRecord {
        val outcome = callOutcome?.let { "\"$it\"" } ?: "null"
        timings.getValue(layer) +=
            """{"requestId":"$id","startUs":$startUs,"endUs":$endUs,"status":$status,"callStartUs":$callStartUs,"callEndUs":$callEndUs,"callOutcome":$outcome}"""
        return this
    }

    fun attempt(layer: String, a: Attempt): RetryRecord {
        attempts.getValue(layer) += a
        return this
    }

    /** attempt-faults.json 의 값(서비스가 센 수). 주지 않은 칸은 0. */
    fun counted(service: String, kind: String, n: Int): RetryRecord {
        counted[service to kind] = n
        return this
    }

    /**
     * 재고가 재시도 감싸개로 결제를 부른 요청 하나(주문은 재시도하지 않는다). 시도 k 는 10ms, 결제에는 시도마다
     * 시작 +1ms 에 들어 끝 −1ms 에 나간다(`Failed:CONNECT_*` 은 결제에 들지 않는다). [outcomes]는 시도마다의 결과,
     * [delaysMs]는 2번째부터의 간격. 시도 1 은 s+4ms 에 시작한다.
     * - 결과가 `Completed`인 시도만 결제 행(APPROVED)을 남긴다.
     * - 결정: 마지막이 아닌 시도는 retry, 마지막은 Completed 면 success, 아니면 [lastDecision].
     * - 마지막이 Completed 면 201(세 층 처리), Failed 면 재고·주문 FAILED 와 502, Unknown 이면 UNKNOWN 과 504.
     * - 재고의 시각 기록 s+3 ~ (마지막 끝+2), call 칸은 마지막 시도의 것. 주문 s+1 ~ (마지막 끝+4). 경과 = 마지막 끝+6 − s.
     */
    fun inventoryRetried(
        id: String,
        sentAtMs: Long,
        outcomes: List<String>,
        delaysMs: List<Long> = List(outcomes.size - 1) { 100L },
        attemptFault: String = "",
        lastDecision: String = "max-attempts",
    ): RetryRecord {
        require(delaysMs.size == outcomes.size - 1)
        val s = sentAtMs
        val callId = nextCallId("inventory")
        var t = s + 4
        var lastStart = 0L
        var lastEnd = 0L
        for ((k, outcome) in outcomes.withIndex()) {
            val delay = if (k == 0) 0L else delaysMs[k - 1]
            t += delay
            val start = t
            val end = t + 10
            val last = k == outcomes.lastIndex
            val decision = when {
                !last -> "retry"
                outcome == "Completed" -> "success"
                else -> lastDecision
            }
            attempt("inventory", Attempt(id, callId, k + 1, us(start), us(end), outcome, delay, decision))
            if (!outcome.startsWith("Failed:CONNECT")) {
                val status = when {
                    outcome == "Completed" -> 201
                    outcome.startsWith("Failed:HTTP_") -> outcome.removePrefix("Failed:HTTP_").toInt()
                    else -> 500
                }
                timing("payment", id, us(start + 1), us(end - 1), status)
            }
            if (outcome == "Completed") row("payment", id, "APPROVED")
            lastStart = start
            lastEnd = end
            t = end
        }
        val last = outcomes.last()
        val end = when {
            last == "Completed" -> ChainEnd(201, "RESERVED", "Completed", 201, "COMPLETED")
            last.startsWith("Failed:") -> ChainEnd(502, "FAILED", "Failed:HTTP_502", 502, "FAILED")
            else -> ChainEnd(504, "UNKNOWN", "Unknown:HTTP_504", 504, "UNKNOWN")
        }
        timing("inventory", id, us(s + 3), us(t + 2), end.invStatus, us(lastStart), us(lastEnd), last)
        timing("order", id, us(s + 1), us(t + 4), end.orderStatus, us(s + 2), us(t + 3), end.orderOutcome)
        row("inventory", id, end.invRow).row("order", id, end.orderRow)
        answer(id, s, end.orderStatus, attemptFault, elapsedMs = t + 6 - s)
        return this
    }

    /** 첫 시도에 성공한 요청(재고의 시도 기록 하나 — success). 결제는 s+5 ~ s+13. */
    fun okChain(id: String, sentAtMs: Long, attemptFault: String = ""): RetryRecord =
        inventoryRetried(id, sentAtMs, listOf("Completed"), attemptFault = attemptFault)

    fun write() {
        base.write()
        val timingDir = dir.resolve("timing").createDirectories()
        for (layer in LAYERS) {
            if ("timing/timings-$layer.json" !in omit) {
                timingDir.resolve("timings-$layer.json")
                    .writeText(timingsText[layer] ?: timings.getValue(layer).joinToString(",", "[", "]"))
            }
        }
        for ((layer, list) in attempts) {
            if ("timing/attempts-$layer.json" !in omit) {
                timingDir.resolve("attempts-$layer.json")
                    .writeText(attemptsText[layer] ?: list.joinToString(",", "[", "]") { it.json() })
            }
        }
        for ((layer, enabled) in retryEnabled) {
            if ("settings-$layer.json" !in omit) {
                dir.resolve("settings-$layer.json")
                    .writeText(settingsText[layer] ?: """{"service":"$layer","retry":{"enabled":$enabled}}""")
            }
        }
        if ("attempt-faults.json" !in omit) {
            val text = attemptFaultsText ?: LAYERS.joinToString(",", "{", "}") { s ->
                "\"$s\":" + ATTEMPT_KINDS.joinToString(",", "{", "}") { k -> "\"$k\":${counted[s to k] ?: 0}" }
            }
            dir.resolve("attempt-faults.json").writeText(text)
        }
    }

    var lastOutput: String = ""

    /** `analyzer retry --run <폴더> [extra]` — 종료 코드 */
    fun runRetry(vararg extra: String): Int {
        write()
        val (code, out) = analyzerCli("retry", "--run", dir.toString(), *extra)
        lastOutput = out
        return code
    }

    val reportRetry: Path get() = dir.resolve("report-retry.json")
    val metricsTsv: Path get() = dir.resolve("metrics-retry.tsv")

    @Suppress("UNCHECKED_CAST")
    fun report(): Map<String, Any?> = RunRecord.MAPPER.readValue(reportRetry.readText(), Map::class.java) as Map<String, Any?>

    /** 재시도 분석을 돌려 보고를 돌려준다 — 종료 코드 0(불일치 없음)이어야 한다. */
    fun analyze(vararg extra: String): Map<String, Any?> {
        val code = runRetry(*extra)
        check(code == 0) { "종료 코드 0을 기대했는데 $code: $lastOutput" }
        return report()
    }

    /** 보고가 나오기만 하면(0 또는 2) 보고를 돌려준다. */
    fun analyzeAny(vararg extra: String): Map<String, Any?> {
        val code = runRetry(*extra)
        check(code == 0 || code == 2) { "보고를 기대했는데 종료 코드 $code: $lastOutput" }
        return report()
    }

    /** 입력 오류(8-3): 종료 코드 1, report-retry.json·metrics-retry.tsv 를 쓰지 않는다. */
    fun assertInputError(vararg extra: String) {
        val code = runRetry(*extra)
        assertEquals(1, code, "입력 오류는 종료 코드 1: $lastOutput")
        assertFalse(reportRetry.exists(), "입력 오류면 report-retry.json 을 쓰지 않는다")
        assertFalse(metricsTsv.exists(), "입력 오류면 metrics-retry.tsv 를 쓰지 않는다")
    }
}

/**
 * 테스트 여럿이 함께 쓰는 기준 기록(재고만 재시도, 최대 3, 간격 100·200 — 시각은 T 기준 ms):
 * - a(T) 정상 — 시도 1(success). 결제 T+5.
 * - b(T+100) `payment:fail-first=1` — 시도 1 500(retry), 간격 100 뒤 시도 2 성공. 결제 T+105, T+215. 경과 130.
 * - c(T+200) `payment:fail-first=9` — 500 세 번(retry, retry, max-attempts), 간격 100, 200. 결제 T+205, T+315, T+525. 502, 경과 340.
 * - d(T+300) `payment:slow-first=1@1500` — 시도 1 요청 타임아웃(1000ms — 결제는 T+305~T+1805 에 자고 처리, 행 남음),
 *   간격 100(실제 100.5ms) 뒤 시도 2 성공(결제 T+1406). 결제 행 둘(두 번 처리됨). 201, 경과 1120.
 * - e(T+400) 장애 없음 — 결제가 404(not-retryable). 502, 경과 20.
 * attempt-faults.json: payment fail-first 4(b 1 + c 3), slow-first 1.
 */
fun goodRetryRecord(root: Path): RetryRecord {
    val r = RetryRecord(root)
        .okChain("a", T)
        .inventoryRetried("b", T + 100, listOf("Failed:HTTP_500", "Completed"), attemptFault = "payment:fail-first=1")
        .inventoryRetried(
            "c", T + 200, listOf("Failed:HTTP_500", "Failed:HTTP_500", "Failed:HTTP_500"), listOf(100L, 200L),
            attemptFault = "payment:fail-first=9",
        )
    // d: 손으로 — 첫 시도가 타임아웃인데 결제는 늦게 처리했다
    val s = T + 300
    val callId = r.nextCallId("inventory")
    val start2 = us(s + 1104) + 500
    val end2 = us(s + 1114) + 500
    r.attempt("inventory", Attempt("d", callId, 1, us(s + 4), us(s + 1004), "Unknown:REQUEST_TIMEOUT", 0, "retry"))
        .attempt("inventory", Attempt("d", callId, 2, start2, end2, "Completed", 100, "success"))
        .timing("payment", "d", us(s + 5), us(s + 1505), 201)
        .timing("payment", "d", us(s + 1106), us(s + 1113), 201)
        .row("payment", "d", "APPROVED").row("payment", "d", "APPROVED")
        .timing("inventory", "d", us(s + 3), us(s + 1116), 201, start2, end2, "Completed")
        .timing("order", "d", us(s + 1), us(s + 1118), 201, us(s + 2), us(s + 1117), "Completed")
        .row("inventory", "d", "RESERVED").row("order", "d", "COMPLETED")
        .answer("d", s, 201, "payment:slow-first=1@1500", elapsedMs = 1120)
    r.inventoryRetried("e", T + 400, listOf("Failed:HTTP_404"), attemptFault = "", lastDecision = "not-retryable")
    return r.counted("payment", "fail-first", 4).counted("payment", "slow-first", 1)
}
