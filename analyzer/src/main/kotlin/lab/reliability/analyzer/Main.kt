package lab.reliability.analyzer

import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/** 종료 코드: 0 통과 · 1 입력이 없거나 틀림(보고를 쓰지 않음) · 2 기대(--expect)나 계획한 수(--planned)와 다름(보고는 씀). */
const val EXIT_OK = 0
const val EXIT_INPUT = 1
const val EXIT_MISMATCH = 2

private const val USAGE = "usage: analyzer --run <실행 폴더> [--expect <기대 파일>] [--planned <보낸 요청 수>]"

fun main(args: Array<String>) {
    exitProcess(runAnalyzer(args))
}

/** 명령줄 계약 그대로의 입구(테스트도 이것을 부른다). 보고는 <실행 폴더>/report.json, report.md에 쓴다. */
fun runAnalyzer(args: Array<String>): Int {
    val options = try {
        parseArgs(args)
    } catch (e: InputException) {
        System.err.println(e.message)
        System.err.println(USAGE)
        return EXIT_INPUT
    }
    return try {
        val run = options.getValue("--run").let(Path::of)
        val rec = reconcile(readRun(run))
        val differences = mutableListOf<String>()
        options["--planned"]?.let { planned ->
            if (rec.answers.toString() != planned) differences += "planned: 계획 $planned, 정답 ${rec.answers}줄"
        }
        val expectFile = options["--expect"]?.let(Path::of)
        if (expectFile != null) differences += compareWithExpectation(rec, readExpectation(expectFile))
        writeReports(run, rec, options, differences)
        println(summaryLine(run, rec, differences))
        differences.forEach { System.err.println("불일치: $it") }
        if (differences.isEmpty()) EXIT_OK else EXIT_MISMATCH
    } catch (e: InputException) {
        System.err.println("입력 오류: ${e.message}")
        EXIT_INPUT
    }
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val known = setOf("--run", "--expect", "--planned")
    if (args.size % 2 != 0) throw InputException("인자는 '--이름 값' 짝이어야 한다")
    val options = args.toList().chunked(2).associate { (k, v) ->
        if (k !in known) throw InputException("모르는 인자 $k")
        k to v
    }
    if ("--run" !in options) throw InputException("--run 이 없다")
    options["--planned"]?.let { if ((it.toIntOrNull() ?: -1) < 1) throw InputException("--planned '$it' 는 1 이상의 정수가 아니다") }
    return options
}

private fun readExpectation(path: Path): JsonNode {
    if (!Files.isRegularFile(path)) throw InputException("$path: 기대 파일이 없다")
    val node = try {
        json.readTree(Files.readString(path))
    } catch (e: Exception) {
        throw InputException("$path: JSON이 아니다")
    }
    if (!node.isObject || node.isEmpty) throw InputException("$path: 키가 하나 이상인 JSON 객체가 아니다")
    val known = setOf("answers", "lostTotal", "doubledTotal", "layerMismatch", "orphanRows", "pendingRows", "faultCountsMatch", "labels")
    val unknown = node.propertyNames().filter { it !in known }
    if (unknown.isNotEmpty()) throw InputException("$path: 모르는 키 $unknown")
    return node
}

/** 기대 파일에 있는 키만 견준다. labels는 꼴(라벨 → 서명 → 건수) 전체가 같아야 한다. */
fun compareWithExpectation(rec: Reconciliation, expect: JsonNode): List<String> {
    val actual: Map<String, Any> = mapOf(
        "answers" to rec.answers,
        "lostTotal" to rec.lostTotal,
        "doubledTotal" to rec.doubledTotal,
        "layerMismatch" to rec.layerMismatch,
        "orphanRows" to rec.layers.values.sumOf { it.orphanRows },
        "pendingRows" to rec.layers.values.sumOf { it.pendingRows },
        "faultCountsMatch" to rec.faultCountsMatch,
    )
    val differences = mutableListOf<String>()
    actual.forEach { (key, value) ->
        val want = expect.get(key) ?: return@forEach
        val same = if (value is Boolean) want.isBoolean && want.booleanValue() == value else want.isIntegralNumber && want.longValue() == (value as Int).toLong()
        if (!same) differences += "$key: 기대 $want, 실제 $value"
    }
    expect.get("labels")?.let { labels ->
        if (!labels.isObject) throw InputException("기대 파일의 labels가 객체가 아니다")
        val wanted = labels.properties().associate { (label, sigs) ->
            if (!sigs.isObject) throw InputException("기대 파일 labels['$label']가 객체가 아니다")
            label to sigs.properties().associate { (sig, n) ->
                if (!n.isIntegralNumber) throw InputException("기대 파일 labels['$label']['$sig']가 정수가 아니다")
                sig to n.intValue()
            }
        }
        val got = rec.labels.mapValues { it.value.signatures }
        (wanted.keys + got.keys).sorted().forEach { label ->
            val w = wanted[label].orEmpty()
            val g = got[label].orEmpty()
            (w.keys + g.keys).sorted().forEach { sig ->
                if (w[sig] != g[sig]) differences += "labels['$label']['$sig']: 기대 ${w[sig] ?: 0}, 실제 ${g[sig] ?: 0}"
            }
        }
    }
    return differences
}

private fun summaryLine(run: Path, rec: Reconciliation, differences: List<String>) =
    "${run.fileName}: 정답 ${rec.answers}줄, requestId ${rec.requestIds}개, " +
        rec.verdicts.entries.joinToString(", ") { "${it.key} ${it.value}" } +
        ", 잃음 ${rec.lostTotal}, 두 번 처리됨 ${rec.doubledTotal}, 장애 수 일치 ${rec.faultCountsMatch}" +
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
    appendLine("- 장애 수(넣은 수 = 서비스가 센 수): ${if (rec.faultCountsMatch) "일치" else "**불일치**"}")
    options["--expect"]?.let {
        appendLine("- 기대 파일 `$it`: ${if (differences.isEmpty()) "일치" else "**불일치 ${differences.size}건**"}")
    }
    differences.forEach { appendLine("  - $it") }
    appendLine()
    appendLine("## 층마다")
    appendLine()
    appendLine("| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 |")
    appendLine("|---|---|---|---|---|---|---|---|---|---|")
    rec.layers.forEach { (name, t) ->
        appendLine("| $name | ${t.rows} | ${t.processedRows} | ${t.pendingRows} | ${t.orphanRows} | ${t.lost} | ${t.doubled} | ${t.failedButProcessed} | ${t.unknownProcessed} | ${t.unknownNotProcessed} |")
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
