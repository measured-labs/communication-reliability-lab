package lab.reliability.analyzer

import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/** 종료 코드: 0 통과 · 1 입력이 없거나 틀림(보고를 쓰지 않고, 있던 보고는 지움) · 2 불일치(보고는 씀). */
const val EXIT_OK = 0
const val EXIT_INPUT = 1
const val EXIT_MISMATCH = 2

private const val USAGE = "usage: analyzer --run <실행 폴더> [--expect <기대 파일>] [--planned <보낸 요청 수>]"

/** 기대 파일이 없어도 늘 0이어야 하는 지표(docs/measurement.md 5-3). 차이 목록의 차례도 이 차례. */
val ALWAYS_ZERO = listOf("orphanRows", "pendingRows", "amountMismatch")

/** 기대 파일의 정수 키 — 차이 목록의 차례도 이 차례. */
val EXPECT_INT_KEYS = listOf("answers", "lostTotal", "doubledTotal", "layerMismatch", "orphanRows", "pendingRows", "amountMismatch")

fun main(args: Array<String>) {
    exitProcess(runAnalyzer(args))
}

/**
 * 명령줄 계약 그대로의 입구(테스트도 이것을 부른다 — docs/measurement.md 5절). 첫 인자가 `timing`이면 task 02의 시각 지표
 * (Timing.kt, 7절), `retry`면 task 03의 재시도 지표(Retry.kt, 8절), 아니면 대조 분석 — 보고는 <실행 폴더>/report.json, report.md에 쓴다.
 */
fun runAnalyzer(args: Array<String>): Int {
    if (args.firstOrNull() == "timing") return runTiming(args.drop(1).toTypedArray())
    if (args.firstOrNull() == "retry") return runRetry(args.drop(1).toTypedArray())
    val options = try {
        parseArgs(args)
    } catch (e: InputException) {
        System.err.println(e.message)
        System.err.println(USAGE)
        runValueOf(args)?.let(::removeReports)
        return EXIT_INPUT
    }
    val run = Path.of(options.getValue("--run"))
    return try {
        val expectFile = options["--expect"]?.let(Path::of)
        val expect = expectFile?.let(::readExpectation)
        val rec = reconcile(readRun(run))
        val differences = mutableListOf<String>()
        options["--planned"]?.toInt()?.let { planned ->
            if (rec.answers != planned) differences += "planned: 계획 $planned, 정답 ${rec.answers}줄"
        }
        differences += alwaysZeroDifferences(rec)
        if (expect != null) differences += compareWithExpectation(rec, expect)
        writeReports(run, rec, options, differences)
        println(summaryLine(run, rec, differences))
        differences.forEach { System.err.println("불일치: $it") }
        if (differences.isEmpty()) EXIT_OK else EXIT_MISMATCH
    } catch (e: InputException) {
        System.err.println("입력 오류: ${e.message}")
        removeReports(run)
        EXIT_INPUT
    }
}

/** 입력 오류일 때 지난 분석의 보고가 남아 통과로 읽히지 않게 지운다. */
private fun removeReports(run: Path) {
    runCatching {
        Files.deleteIfExists(run.resolve("report.json"))
        Files.deleteIfExists(run.resolve("report.md"))
    }
}

private fun runValueOf(args: Array<String>): Path? {
    val i = args.indexOf("--run")
    return args.getOrNull(i + 1)?.takeIf { i >= 0 && !it.startsWith("--") }?.let { runCatching { Path.of(it) }.getOrNull() }
}

private val DIGITS = Regex("[0-9]+")

private fun parseArgs(args: Array<String>): Map<String, String> {
    val known = setOf("--run", "--expect", "--planned")
    if (args.size % 2 != 0) throw InputException("인자는 '--이름 값' 짝이어야 한다")
    val options = linkedMapOf<String, String>()
    args.toList().chunked(2).forEach { (k, v) ->
        if (k !in known) throw InputException("모르는 인자 $k")
        if (k in options) throw InputException("인자 $k 가 두 번 나왔다")
        options[k] = v
    }
    val run = options["--run"] ?: throw InputException("--run 이 없다")
    if (run.isBlank() || Path.of(run).fileName == null) throw InputException("--run '$run' 에 실행 폴더 이름이 없다")
    options["--planned"]?.let {
        if (!DIGITS.matches(it) || (it.toIntOrNull() ?: 0) < 1) throw InputException("--planned '$it' 는 1 이상의 정수(10진 숫자만)가 아니다")
    }
    return options
}

/** 기대 파일을 읽고 꼴을 검사한다 — 값의 형이 틀리면 차이가 아니라 입력 오류다. */
private fun readExpectation(path: Path): JsonNode {
    if (!Files.isRegularFile(path)) throw InputException("$path: 기대 파일이 없다")
    val node = try {
        json.readTree(Files.readString(path))
    } catch (e: Exception) {
        throw InputException("$path: JSON이 아니다")
    }
    if (node == null || !node.isObject || node.isEmpty) throw InputException("$path: 키가 하나 이상인 JSON 객체가 아니다")
    val known = EXPECT_INT_KEYS + listOf("faultCountsMatch", "labels")
    val unknown = node.propertyNames().filter { it !in known }
    if (unknown.isNotEmpty()) throw InputException("$path: 모르는 키 $unknown")
    EXPECT_INT_KEYS.forEach { key ->
        node.get(key)?.let { if (!isCount(it)) throw InputException("$path: $key 가 0 이상의 정수가 아니다") }
    }
    node.get("faultCountsMatch")?.let { if (!it.isBoolean) throw InputException("$path: faultCountsMatch 가 불 값이 아니다") }
    node.get("labels")?.let { labels ->
        if (!labels.isObject) throw InputException("$path: labels가 객체가 아니다")
        labels.properties().forEach { (label, sigs) ->
            if (!sigs.isObject) throw InputException("$path: labels['$label']가 객체가 아니다")
            sigs.properties().forEach { (sig, n) ->
                if (!isCount(n)) throw InputException("$path: labels['$label']['$sig']가 0 이상의 정수가 아니다")
            }
        }
    }
    return node
}

private fun isCount(n: JsonNode) = n.isIntegralNumber && n.canConvertToInt() && n.intValue() >= 0

/** 기대 파일이 없어도 늘 0이어야 하는 지표. 0이 아니면 한 줄씩. */
fun alwaysZeroDifferences(rec: Reconciliation): List<String> =
    ALWAYS_ZERO.mapNotNull { key -> actualInts(rec).getValue(key).takeIf { it != 0 }?.let { "$key: 늘 0, 실제 $it" } }

private fun actualInts(rec: Reconciliation): Map<String, Int> = mapOf(
    "answers" to rec.answers,
    "lostTotal" to rec.lostTotal,
    "doubledTotal" to rec.doubledTotal,
    "layerMismatch" to rec.layerMismatch,
    "orphanRows" to rec.orphanRows,
    "pendingRows" to rec.pendingRows,
    "amountMismatch" to rec.amountMismatch,
)

/**
 * 기대 파일에 있는 키만 견준다(꼴은 [readExpectation]이 검사했다). 차례: 정수 키([EXPECT_INT_KEYS] 차례) → faultCountsMatch → labels(라벨, 서명의 사전순).
 * labels는 꼴(라벨 → 서명 → 건수) 전체를 견주되 없는 쪽을 0으로 본다 — 기대의 0과 없음은 같다.
 */
fun compareWithExpectation(rec: Reconciliation, expect: JsonNode): List<String> {
    val differences = mutableListOf<String>()
    val actual = actualInts(rec)
    EXPECT_INT_KEYS.forEach { key ->
        val want = expect.get(key) ?: return@forEach
        val got = actual.getValue(key)
        if (want.intValue() != got) differences += "$key: 기대 ${want.intValue()}, 실제 $got"
    }
    expect.get("faultCountsMatch")?.let { want ->
        if (want.booleanValue() != rec.faultCountsMatch) differences += "faultCountsMatch: 기대 ${want.booleanValue()}, 실제 ${rec.faultCountsMatch}"
    }
    expect.get("labels")?.let { labels ->
        val wanted = labels.properties().associate { (label, sigs) -> label to sigs.properties().associate { (sig, n) -> sig to n.intValue() } }
        val got = rec.labels.mapValues { it.value.signatures }
        (wanted.keys + got.keys).sorted().forEach { label ->
            val w = wanted[label].orEmpty()
            val g = got[label].orEmpty()
            (w.keys + g.keys).sorted().forEach { sig ->
                val wn = w[sig] ?: 0
                val gn = g[sig] ?: 0
                if (wn != gn) differences += "labels['$label']['$sig']: 기대 $wn, 실제 $gn"
            }
        }
    }
    return differences
}

private fun summaryLine(run: Path, rec: Reconciliation, differences: List<String>) =
    "${run.fileName}: 정답 ${rec.answers}줄, requestId ${rec.requestIds}개, " +
        rec.verdicts.entries.joinToString(", ") { "${it.key} ${it.value}" } +
        ", 잃음 ${rec.lostTotal}, 두 번 처리됨 ${rec.doubledTotal}, 금액 불일치 ${rec.amountMismatch}" +
        ", 정답에 없는 행 ${rec.orphanRows}, PENDING 행 ${rec.pendingRows}, 장애 수 일치 ${rec.faultCountsMatch}" +
        if (differences.isEmpty()) "" else ", 불일치 ${differences.size}건"

private fun writeReports(run: Path, rec: Reconciliation, options: Map<String, String>, differences: List<String>) {
    val report = linkedMapOf(
        "run" to run.fileName.toString(),
        "expect" to options["--expect"],
        "planned" to options["--planned"]?.toInt(),
        "differences" to differences,
        "reconciliation" to rec,
    )
    json.writerWithDefaultPrettyPrinter().writeValue(run.resolve("report.json").toFile(), report)
    Files.writeString(run.resolve("report.md"), markdown(run, rec, options, differences))
}

private fun Double?.ms() = this?.let { "%.1f".format(it) } ?: "-"

private fun markdown(run: Path, rec: Reconciliation, options: Map<String, String>, differences: List<String>): String = buildString {
    appendLine("# 분석 보고 — ${run.fileName}")
    appendLine()
    appendLine("- 정답 ${rec.answers}줄, requestId ${rec.requestIds}개" + (options["--planned"]?.let { " (계획 $it)" } ?: ""))
    appendLine("- 응답으로 가름: " + rec.verdicts.entries.joinToString(", ") { "${it.key} ${it.value}" })
    appendLine("- **잃음 ${rec.lostTotal} · 두 번 처리됨 ${rec.doubledTotal}** · 층 사이 처리 수가 다른 requestId ${rec.layerMismatch}")
    appendLine("- 금액이 보낸 금액과 다른 처리 행 ${rec.amountMismatch} · 정답에 없는 행 ${rec.orphanRows} · PENDING 행 ${rec.pendingRows} (셋은 늘 0이어야 한다)")
    appendLine("- 장애 수(넣은 수 = 서비스가 센 수): ${if (rec.faultCountsMatch) "일치" else "**불일치**"}")
    options["--expect"]?.let { appendLine("- 기대 파일 `$it`") }
    appendLine("- 판정: ${if (differences.isEmpty()) "통과" else "**불일치 ${differences.size}건**"}")
    differences.forEach { appendLine("  - $it") }
    appendLine()
    appendLine("## 층마다")
    appendLine()
    appendLine("| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |")
    appendLine("|---|---|---|---|---|---|---|---|---|---|---|")
    rec.layers.forEach { (name, t) ->
        appendLine("| $name | ${t.rows} | ${t.processedRows} | ${t.pendingRows} | ${t.orphanRows} | ${t.lost} | ${t.doubled} | ${t.failedButProcessed} | ${t.unknownProcessed} | ${t.unknownNotProcessed} | ${t.amountMismatch ?: "-"} |")
    }
    appendLine()
    appendLine("## 장애 수")
    appendLine()
    appendLine("| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |")
    appendLine("|---|---|---|---|")
    rec.faultCounts.filter { it.injected > 0 || it.counted > 0 }.forEach {
        appendLine("| ${it.service} | ${it.kind} | ${it.injected} | ${it.counted}${if (it.match) "" else " **≠**"} |")
    }
    appendLine()
    appendLine("## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)")
    appendLine()
    appendLine("| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |")
    appendLine("|---|---|---|---|---|---|---|---|")
    rec.labels.forEach { (label, s) ->
        s.signatures.entries.forEachIndexed { i, (sig, n) ->
            val q = s.elapsedMs
            if (i == 0) {
                appendLine("| `${label.ifEmpty { "(없음)" }}` | ${s.answers} | `$sig` | $n | ${q.p50.ms()} | ${q.p95.ms()} | ${q.p99.ms()} | ${q.max.ms()} |")
            } else {
                appendLine("| | | `$sig` | $n | | | | |")
            }
        }
    }
    appendLine()
    appendLine("## 처리 시간 (k6가 잰 요청마다의 경과, ms)")
    appendLine()
    appendLine("| 묶음 | 건수 | p50 | p95 | p99 | max |")
    appendLine("|---|---|---|---|---|---|")
    rec.elapsedMs.forEach { (k, q) -> appendLine("| $k | ${q.count} | ${q.p50.ms()} | ${q.p95.ms()} | ${q.p99.ms()} | ${q.max.ms()} |") }
}
