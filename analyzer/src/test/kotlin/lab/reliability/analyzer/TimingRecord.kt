package lab.reliability.analyzer

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// docs/measurement.md 7절(시각 지표 — `analyzer timing`)의 입력(7-1)대로 작은 실행 기록을 손으로 만든다.
// 이 파일은 분석기의 구현을 보지 않고 문서(7절, docs/experiments/task-02-design.md)만으로 쓴다.
// 1·2절의 정답 줄·DB 행은 RunRecord 로 쓰고, 그 위에 sentAtMs·시각 기록·표본·설정·조회를 더한다.

/** 시험 기록의 기준 시각(벽시계 ms). 시각 기록은 μs 라서 [us]로 바꾼다. */
const val T = 1_000_000L

/** ms → μs */
fun us(ms: Long): Long = ms * 1000

/** 표본 하나(7-1). 가상 스레드면 active·poolSize·maxThreads·queue 가 null. */
data class TestSample(
    val atMs: Long,
    val inflight: Int = 0,
    val active: Int? = 0,
    val poolSize: Int? = 10,
    val maxThreads: Int? = 200,
    val queue: Int? = 0,
    val connections: Int? = 1,
) {
    fun json(): String =
        """{"atMs":$atMs,"inflight":$inflight,"active":$active,"poolSize":$poolSize,"maxThreads":$maxThreads,"queue":$queue,"connections":$connections}"""
}

/** [from]부터 100ms 간격, 마지막은 꼭 [to](간격 ≤ 100). */
fun grid(from: Long, to: Long, step: Long = 100): List<Long> {
    val out = mutableListOf<Long>()
    var t = from
    while (t < to) { out += t; t += step }
    out += to
    return out
}

/**
 * 문서의 명령줄(`analyzer timing ...`, 7-2)을 그대로 별도 JVM 에서 돌린다 — 종료 코드와 표준 출력·오류.
 * 5절의 테스트 입구 runAnalyzer 는 `analyzer --run …`(대조 분석)의 입구로 적혀 있고 7절에는 시각 분석의 테스트 입구가 없다.
 * main 은 exitProcess 하므로 이 JVM 에서 부르지 않는다. 메인 클래스는 analyzer/build.gradle.kts 의 application.mainClass.
 */
fun analyzerCli(vararg args: String): Pair<Int, String> {
    val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
    val p = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "lab.reliability.analyzer.MainKt", *args)
        .redirectErrorStream(true)
        .start()
    val out = p.inputStream.bufferedReader().readText()
    return p.waitFor() to out
}

class TimingRecord(root: Path, name: String = "timing-20261124T000000-r1") {
    val base = RunRecord(root, name)
    val dir: Path get() = base.dir

    /** 표본이 덮어야 하는 구간을 셈하려고 sentAtMs 와 elapsedMs 를 모은다(answer 로 넣은 줄만). */
    private val sends = mutableListOf<Pair<Long, Double>>()
    private val timings = LAYERS.associateWith { mutableListOf<String>() }
    private val lookupLines = mutableListOf<String>()

    /** 값이 있으면 그 파일을 이 내용 그대로 쓴다. */
    val timingsText = mutableMapOf<String, String>()
    val samplesText = mutableMapOf<String, String>()
    val settingsText = mutableMapOf<String, String>()
    var lookupsText: String? = null

    /** 서비스마다 표본을 손으로 준다(주지 않으면 첫 sentAtMs 부터 마지막 끝까지 100ms 간격의 0 표본). */
    val samples = mutableMapOf<String, List<TestSample>>()
    val intervalMs = mutableMapOf<String, Int>()

    /** 쓰지 않을 파일: "timing/timings-payment.json", "timing/samples-order.json", "settings-order.json", "lookups.jsonl" */
    val omit = mutableSetOf<String>()

    var orderTimeoutMs = 2000
    var inventoryTimeoutMs = 1000

    fun answer(
        id: String,
        sentAtMs: Long,
        status: Int = 201,
        fault: String = "",
        elapsedMs: Number = 20,
        amount: Long = amountFor(id),
    ): TimingRecord {
        sends += sentAtMs to elapsedMs.toDouble()
        base.rawAnswer(
            """{"type":"answer","requestId":"$id","fault":"$fault","amount":$amount,"sentAtMs":$sentAtMs,"status":$status,"errorCode":0,"elapsedMs":$elapsedMs}""",
        )
        return this
    }

    fun rawAnswer(line: String): TimingRecord {
        base.rawAnswer(line)
        return this
    }

    fun row(layer: String, id: String, status: String): TimingRecord {
        base.row(layer, id, status)
        return this
    }

    /** 시각 기록 한 줄(μs). call 칸 셋은 모두 주거나 모두 null. */
    fun timing(
        layer: String,
        id: String,
        startUs: Long,
        endUs: Long,
        status: Int = 201,
        callStartUs: Long? = null,
        callEndUs: Long? = null,
        callOutcome: String? = null,
    ): TimingRecord {
        val outcome = callOutcome?.let { "\"$it\"" } ?: "null"
        timings.getValue(layer) +=
            """{"requestId":"$id","startUs":$startUs,"endUs":$endUs,"status":$status,"callStartUs":$callStartUs,"callEndUs":$callEndUs,"callOutcome":$outcome}"""
        return this
    }

    fun rawTiming(layer: String, json: String): TimingRecord {
        timings.getValue(layer) += json
        return this
    }

    fun lookup(sentAtMs: Long, status: Int = 200, elapsedMs: Number = 5): TimingRecord {
        lookupLines += """{"type":"lookup","sentAtMs":$sentAtMs,"status":$status,"errorCode":0,"elapsedMs":$elapsedMs}"""
        return this
    }

    /**
     * 모두 맞는 사슬 요청 하나: 201, 세 층의 처리 행, 시각 기록이 서로 안에 든다(ms 오프셋):
     * order 1~19(call 2~18), inventory 3~17(call 4~16), payment 5~15. 경과 20ms.
     */
    fun okChain(id: String, sentAtMs: Long, fault: String = "", status: Int = 201, elapsedMs: Number = 20): TimingRecord {
        val s = sentAtMs
        answer(id, s, status, fault, elapsedMs)
        timing("order", id, us(s + 1), us(s + 19), 201, us(s + 2), us(s + 18), "Completed")
        timing("inventory", id, us(s + 3), us(s + 17), 201, us(s + 4), us(s + 16), "Completed")
        timing("payment", id, us(s + 5), us(s + 15), 201)
        row("order", id, "COMPLETED").row("inventory", id, "RESERVED").row("payment", id, "APPROVED")
        return this
    }

    /**
     * 재고→결제가 요청 타임아웃(재고의 callEnd = s+1004ms)으로 끊긴 요청. 재고는 504, 주문은 아래가 504라 `Unknown:HTTP_504`,
     * k6는 504(경과 1010ms). 결제는 [paymentStartUs](기본 s+5ms)에 시작해 [paymentEndUs]에 끝난다. [paymentProcessed]면 결제 행 APPROVED,
     * 아니면 결제 행이 없다(결제의 시각 기록은 status 500).
     */
    fun inventoryTimesOut(
        id: String,
        sentAtMs: Long,
        paymentProcessed: Boolean = true,
        paymentEndUs: Long = us(sentAtMs + 1500),
        paymentStartUs: Long = us(sentAtMs + 5),
    ): TimingRecord {
        val s = sentAtMs
        answer(id, s, 504, elapsedMs = 1010)
        timing("order", id, us(s + 1), us(s + 1007), 504, us(s + 2), us(s + 1006), "Unknown:HTTP_504")
        timing("inventory", id, us(s + 3), us(s + 1005), 504, us(s + 4), us(s + 1004), "Unknown:REQUEST_TIMEOUT")
        timing("payment", id, paymentStartUs, paymentEndUs, if (paymentProcessed) 201 else 500)
        row("order", id, "UNKNOWN").row("inventory", id, "UNKNOWN")
        if (paymentProcessed) row("payment", id, "APPROVED")
        return this
    }

    /**
     * 주문→재고가 요청 타임아웃(주문의 callEnd = s+1002ms)으로 끊긴 요청. 재고는 그 뒤에도 결제를 기다려 s+1510ms 에 끝나고
     * 재고 행은 [inventoryRow](null 이면 행 없음), 결제는 s+5~s+1500ms 에 처리(APPROVED). k6는 504(경과 1010ms).
     */
    fun orderTimesOut(id: String, sentAtMs: Long, inventoryRow: String? = "RESERVED"): TimingRecord {
        val s = sentAtMs
        answer(id, s, 504, elapsedMs = 1010)
        timing("order", id, us(s + 1), us(s + 1003), 504, us(s + 2), us(s + 1002), "Unknown:REQUEST_TIMEOUT")
        timing("inventory", id, us(s + 3), us(s + 1510), 201, us(s + 4), us(s + 1504), "Completed")
        timing("payment", id, us(s + 5), us(s + 1500), 201)
        row("order", id, "UNKNOWN")
        inventoryRow?.let { row("inventory", id, it) }
        row("payment", id, "APPROVED")
        return this
    }

    private fun defaultSamples(): List<TestSample> {
        check(sends.isNotEmpty()) { "표본을 만들려면 sentAtMs 가 있는 정답이 하나 이상 있어야 한다" }
        val first = sends.minOf { it.first }
        val lastEnd = sends.maxOf { it.first + Math.ceil(it.second).toLong() }
        return grid(first, lastEnd).map { TestSample(it) }
    }

    fun write() {
        base.write()
        val timingDir = dir.resolve("timing").createDirectories()
        for (layer in LAYERS) {
            if ("timing/timings-$layer.json" !in omit) {
                timingDir.resolve("timings-$layer.json")
                    .writeText(timingsText[layer] ?: timings.getValue(layer).joinToString(",", "[", "]"))
            }
            if ("timing/samples-$layer.json" !in omit) {
                val text = samplesText[layer] ?: run {
                    val list = samples[layer] ?: defaultSamples()
                    """{"intervalMs":${intervalMs[layer] ?: 100},"overflow":false,"samples":${list.joinToString(",", "[", "]") { it.json() }}}"""
                }
                timingDir.resolve("samples-$layer.json").writeText(text)
            }
        }
        for ((svc, timeout) in listOf("order" to orderTimeoutMs, "inventory" to inventoryTimeoutMs)) {
            if ("settings-$svc.json" !in omit) {
                dir.resolve("settings-$svc.json")
                    .writeText(settingsText[svc] ?: """{"service":"$svc","nextLayer":{"requestTimeoutMs":$timeout}}""")
            }
        }
        if ("lookups.jsonl" !in omit && (lookupsText != null || lookupLines.isNotEmpty())) {
            dir.resolve("lookups.jsonl").writeText(lookupsText ?: lookupLines.joinToString("\n", postfix = "\n"))
        }
    }

    /** 마지막 실행의 표준 출력과 표준 오류(실패 메시지용). */
    var lastOutput: String = ""

    /** `analyzer timing --run <폴더> [extra]` — 종료 코드 */
    fun runTiming(vararg extra: String): Int {
        write()
        val (code, out) = analyzerCli("timing", "--run", dir.toString(), *extra)
        lastOutput = out
        return code
    }

    val reportTiming: Path get() = dir.resolve("report-timing.json")
    val metricsTsv: Path get() = dir.resolve("metrics.tsv")

    @Suppress("UNCHECKED_CAST")
    fun report(): Map<String, Any?> = RunRecord.MAPPER.readValue(reportTiming.readText(), Map::class.java) as Map<String, Any?>

    /** 시각 분석을 돌려 보고를 돌려준다 — 종료 코드 0(불일치 없음)이어야 한다. */
    fun analyze(vararg extra: String): Map<String, Any?> {
        val code = runTiming(*extra)
        check(code == 0) { "종료 코드 0을 기대했는데 $code: $lastOutput" }
        return report()
    }

    /** 보고가 나오기만 하면(0 또는 2) 보고를 돌려준다. */
    fun analyzeAny(vararg extra: String): Map<String, Any?> {
        val code = runTiming(*extra)
        check(code == 0 || code == 2) { "보고를 기대했는데 종료 코드 $code: $lastOutput" }
        return report()
    }

    /** 입력 오류(7-2): 종료 코드 1, report-timing.json·metrics.tsv 를 쓰지 않는다. */
    fun assertInputError(vararg extra: String) {
        val code = runTiming(*extra)
        assertEquals(1, code, "입력 오류는 종료 코드 1: $lastOutput")
        assertFalse(reportTiming.exists(), "입력 오류면 report-timing.json 을 쓰지 않는다")
        assertFalse(metricsTsv.exists(), "입력 오류면 metrics.tsv 를 쓰지 않는다")
    }
}
