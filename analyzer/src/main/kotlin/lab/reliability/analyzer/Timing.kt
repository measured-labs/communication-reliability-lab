package lab.reliability.analyzer

import lab.reliability.chain.FaultDirectives
import lab.reliability.chain.FaultKind
import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path

// task 02의 시각 지표: 거짓 타임아웃, 불린 쪽 응답 시간, 헛일, 표본(바쁜 스레드·대기 수), 느린 구간의 조회와 회복.
// 형식과 판정 규칙: docs/measurement.md 7절. 명령: analyzer timing --run <실행 폴더> [--slow-label <장애 값>] [--expect <기대 파일>]

private const val TIMING_USAGE = "usage: analyzer timing --run <실행 폴더> [--slow-label <장애 값>] [--expect <기대 파일>]"

/** 회복 시간 ②에서 "나쁜" 요청: 성공이 아니거나 이것보다 오래 걸림(ms). */
const val BAD_ELAPSED_MS = 1000.0

/** 표본 사이 간격이 이보다 길면 불일치(표본이 끊겼다). */
const val MAX_SAMPLE_GAP_MS = 1000L

const val TIMEOUT_OUTCOME = "Unknown:REQUEST_TIMEOUT"

/** 정답 한 줄 가운데 시각 지표가 쓰는 것. */
data class TimedAnswer(val requestId: String, val fault: String, val status: Int, val elapsedMs: Double, val sentAtMs: Long) {
    /** k6가 응답을 받지 못했으면 포기한 시각(μs) */
    val clientGiveUpUs: Long? get() = if (status == 0) (sentAtMs * 1000 + (elapsedMs * 1000).toLong()) else null
}

data class Timing(
    val requestId: String,
    val startUs: Long,
    val endUs: Long,
    val status: Int,
    val callStartUs: Long?,
    val callEndUs: Long?,
    val callOutcome: String?,
)

data class Sample(val atMs: Long, val inflight: Int, val active: Int?, val maxThreads: Int?, val queue: Int?, val connections: Long?) {
    val load get() = inflight + (queue ?: 0)
}

data class Lookup(val sentAtMs: Long, val status: Int, val elapsedMs: Double)

fun runTiming(args: Array<String>): Int {
    val options = try {
        parseTimingArgs(args)
    } catch (e: InputException) {
        System.err.println(e.message)
        System.err.println(TIMING_USAGE)
        return EXIT_INPUT
    }
    val run = Path.of(options.getValue("--run"))
    val out = listOf(run.resolve("report-timing.json"), run.resolve("metrics.tsv"))
    return try {
        out.forEach(Files::deleteIfExists)
        val expect = options["--expect"]?.let { readTimingExpect(Path.of(it)) }
        val report = timingReport(run, options["--slow-label"])
        val metrics = flatten(report)
        val differences = mutableListOf<String>()
        @Suppress("UNCHECKED_CAST")
        val sampleGaps = (report["samples"] as Map<String, Map<String, Any?>>).mapValues { it.value["maxGapMs"] as Long }
        sampleGaps.forEach { (svc, gap) -> if (gap > MAX_SAMPLE_GAP_MS) differences += "samples.$svc.maxGapMs: 늘 ${MAX_SAMPLE_GAP_MS} 이하, 실제 $gap" }
        expect?.forEach { (key, range) ->
            val v = metrics[key]
            if (v !is Number) {
                differences += "$key: 기대 ${range.first}~${range.second}, 실제 ${v ?: "없음"}"
            } else if (v.toDouble() < range.first || v.toDouble() > range.second) {
                differences += "$key: 기대 ${range.first}~${range.second}, 실제 $v"
            }
        }
        val full = linkedMapOf<String, Any?>("run" to run.fileName.toString(), "expect" to options["--expect"], "differences" to differences) + report
        json.writerWithDefaultPrettyPrinter().writeValue(run.resolve("report-timing.json").toFile(), full)
        Files.writeString(run.resolve("metrics.tsv"), metrics.entries.joinToString("") { "${it.key}\t${it.value ?: ""}\n" })
        println("${run.fileName}: 시각 분석 — " + summaryKeys.mapNotNull { k -> metrics[k]?.let { "$k=$it" } }.joinToString(", ") +
            if (differences.isEmpty()) "" else ", 불일치 ${differences.size}건")
        differences.forEach { System.err.println("불일치: $it") }
        if (differences.isEmpty()) EXIT_OK else EXIT_MISMATCH
    } catch (e: InputException) {
        System.err.println("입력 오류: ${e.message}")
        out.forEach { runCatching { Files.deleteIfExists(it) } }
        EXIT_INPUT
    }
}

private val summaryKeys = listOf(
    "edges.inventory->payment.timeouts", "edges.inventory->payment.falseTimeouts",
    "edges.order->inventory.timeouts", "edges.order->inventory.falseTimeouts",
    "waste.inventory.wasted", "waste.payment.wasted", "samples.order.maxActive", "samples.order.maxQueue",
)

private fun parseTimingArgs(args: Array<String>): Map<String, String> {
    val known = setOf("--run", "--slow-label", "--expect")
    if (args.size % 2 != 0) throw InputException("인자는 '--이름 값' 짝이어야 한다")
    val options = linkedMapOf<String, String>()
    args.toList().chunked(2).forEach { (k, v) ->
        if (k !in known) throw InputException("모르는 인자 $k")
        if (k in options) throw InputException("인자 $k 가 두 번 나왔다")
        options[k] = v
    }
    val run = options["--run"] ?: throw InputException("--run 이 없다")
    if (run.isBlank() || Path.of(run).fileName == null) throw InputException("--run '$run' 에 실행 폴더 이름이 없다")
    options["--slow-label"]?.let { if (it.isBlank()) throw InputException("--slow-label 이 비었다") }
    return options
}

/** 기대 파일: {"<지표 이름>": {"min": n, "max": n}, ...} — 이름은 metrics.tsv의 이름. */
private fun readTimingExpect(path: Path): List<Pair<String, Pair<Double, Double>>> {
    if (!Files.isRegularFile(path)) throw InputException("$path: 기대 파일이 없다")
    val node = try { json.readTree(Files.readString(path)) } catch (e: Exception) { throw InputException("$path: JSON이 아니다") }
    if (node == null || !node.isObject || node.isEmpty) throw InputException("$path: 키가 하나 이상인 JSON 객체가 아니다")
    return node.properties().map { (key, range) ->
        val min = range.get("min")
        val max = range.get("max")
        if (!range.isObject || range.size() != 2 || min == null || max == null || !min.isNumber || !max.isNumber || min.doubleValue() > max.doubleValue()) {
            throw InputException("$path: '$key' 가 {\"min\": 수, \"max\": 수}(min ≤ max)가 아니다")
        }
        key to (min.doubleValue() to max.doubleValue())
    }
}

// ───────────── 읽기 ─────────────

private fun readJson(path: Path): JsonNode {
    if (!Files.isRegularFile(path)) throw InputException("$path: 파일이 없다")
    val text = Files.readString(path)
    if (text.isBlank()) throw InputException("$path: 비었다")
    return try { json.readTree(text) } catch (e: Exception) { throw InputException("$path: JSON이 아니다") }
        ?: throw InputException("$path: 비었다")
}

private fun JsonNode.req(name: String, where: String): JsonNode =
    get(name)?.takeUnless { it.isNull } ?: throw InputException("$where: '$name'이 없다")

private fun JsonNode.long(name: String, where: String): Long {
    val v = req(name, where)
    if (!v.isIntegralNumber || !v.canConvertToLong()) throw InputException("$where: '$name'이 정수가 아니다")
    return v.longValue()
}

private fun JsonNode.longOrNull(name: String, where: String): Long? {
    val v = get(name) ?: throw InputException("$where: '$name' 칸이 없다(null이라도 있어야 한다)")
    if (v.isNull) return null
    if (!v.isIntegralNumber || !v.canConvertToLong()) throw InputException("$where: '$name'이 정수나 null이 아니다")
    return v.longValue()
}

private fun JsonNode.str(name: String, where: String): String {
    val v = req(name, where)
    if (!v.isString || v.stringValue().isBlank()) throw InputException("$where: '$name'이 비지 않은 문자열이 아니다")
    return v.stringValue()
}

private fun readLines(path: Path): List<String> {
    if (!Files.isRegularFile(path)) throw InputException("$path: 파일이 없다")
    val text = Files.readString(path)
    if ('\r' in text) throw InputException("$path: CR(\\r)이 있다")
    if (text.isEmpty()) throw InputException("$path: 0줄이다")
    return text.removeSuffix("\n").split('\n')
}

/** 정답(1절 검사는 [readAnswers]가 한다) + 시각 칸 `sentAtMs`(이 지표에서는 필수). */
fun readTimedAnswers(path: Path): List<TimedAnswer> {
    val answers = readAnswers(path)
    val lines = readLines(path)
    return answers.mapIndexed { i, a ->
        val where = "$path:${i + 1}"
        val sentAtMs = json.readTree(lines[i]).long("sentAtMs", where)
        TimedAnswer(a.requestId, a.fault, a.status, a.elapsedMs, sentAtMs)
    }
}

fun readTimings(path: Path, hasNextLayer: Boolean): List<Timing> {
    val node = readJson(path)
    if (!node.isArray) throw InputException("$path: JSON 배열이 아니다")
    return node.mapIndexed { i, r ->
        val where = "$path[$i]"
        if (!r.isObject) throw InputException("$where: 객체가 아니다")
        val t = Timing(
            r.str("requestId", where), r.long("startUs", where), r.long("endUs", where), r.long("status", where).toInt(),
            r.longOrNull("callStartUs", where), r.longOrNull("callEndUs", where),
            r.get("callOutcome")?.takeUnless { it.isNull }?.let { if (it.isString && it.stringValue().isNotBlank()) it.stringValue() else throw InputException("$where: 'callOutcome'이 문자열이 아니다") },
        )
        if (t.endUs < t.startUs) throw InputException("$where: endUs < startUs")
        val callFields = listOf(t.callStartUs, t.callEndUs, t.callOutcome)
        if (callFields.any { it != null } && callFields.any { it == null }) throw InputException("$where: call 칸 셋이 함께 있거나 함께 없어야 한다")
        if (t.callStartUs != null) {
            if (!hasNextLayer) throw InputException("$where: 맨 아래 층인데 call 칸이 있다")
            if (t.callStartUs < t.startUs || t.callEndUs!! < t.callStartUs || t.callEndUs > t.endUs) throw InputException("$where: call 시각이 start ≤ callStart ≤ callEnd ≤ end가 아니다")
            if (!Regex("Completed|(Failed|Unknown):\\S+").matches(t.callOutcome!!)) throw InputException("$where: callOutcome '${t.callOutcome}' 의 꼴이 틀렸다")
        }
        t
    }
}

class SampleSeries(val intervalMs: Long, val samples: List<Sample>)

fun readSamples(path: Path): SampleSeries {
    val node = readJson(path)
    if (!node.isObject) throw InputException("$path: JSON 객체가 아니다")
    val interval = node.long("intervalMs", path.toString())
    if (interval <= 0) throw InputException("$path: intervalMs가 양수가 아니다")
    val overflow = node.req("overflow", path.toString())
    if (!overflow.isBoolean) throw InputException("$path: overflow가 불이 아니다")
    if (overflow.booleanValue()) throw InputException("$path: 표본이 상한을 넘어 잘렸다(overflow)")
    val arr = node.req("samples", path.toString())
    if (!arr.isArray || arr.isEmpty) throw InputException("$path: samples가 비었다")
    val samples = arr.mapIndexed { i, s ->
        val where = "$path.samples[$i]"
        if (!s.isObject) throw InputException("$where: 객체가 아니다")
        val inflight = s.long("inflight", where)
        if (inflight < 0) throw InputException("$where: inflight가 음수다")
        s.longOrNull("poolSize", where)
        Sample(s.long("atMs", where), inflight.toInt(), s.longOrNull("active", where)?.toInt(), s.longOrNull("maxThreads", where)?.toInt(),
            s.longOrNull("queue", where)?.toInt(), s.longOrNull("connections", where))
    }
    samples.zipWithNext().forEachIndexed { i, (a, b) -> if (b.atMs < a.atMs) throw InputException("$path.samples[${i + 1}]: atMs가 앞보다 이르다") }
    return SampleSeries(interval, samples)
}

fun readLookups(path: Path): List<Lookup> = readLines(path).mapIndexed { i, line ->
    val where = "$path:${i + 1}"
    if (line.isBlank()) throw InputException("$where: 빈 줄")
    val n = try { json.readTree(line) } catch (e: Exception) { throw InputException("$where: JSON이 아니다") }
    if (n == null || !n.isObject) throw InputException("$where: JSON 객체가 아니다")
    if (n.str("type", where) != "lookup") throw InputException("$where: type이 \"lookup\"이 아니다")
    val status = n.long("status", where).toInt()
    if (status != 0 && status !in 100..599) throw InputException("$where: status $status")
    val e = n.req("elapsedMs", where)
    if (!e.isNumber || e.doubleValue() < 0) throw InputException("$where: elapsedMs가 0 이상의 수가 아니다")
    Lookup(n.long("sentAtMs", where), status, e.doubleValue())
}

/** settings-<서비스>.json의 nextLayer.requestTimeoutMs(떠 있는 객체에서 읽은 값). */
private fun requestTimeoutMs(run: Path, service: String): Long {
    val path = run.resolve("settings-$service.json")
    val v = readJson(path).get("nextLayer")?.get("requestTimeoutMs")
    if (v == null || !v.isIntegralNumber) throw InputException("$path: nextLayer.requestTimeoutMs가 정수가 아니다")
    return v.longValue()
}

// ───────────── 계산 ─────────────

/** nearest-rank, 천분율 q(500·900·990·999): ceil(q·n/1000)번째(정수 산술). 0건이면 null. */
fun quantiles(values: List<Double>): Map<String, Any?> {
    if (values.isEmpty()) return linkedMapOf("count" to 0, "p50" to null, "p90" to null, "p99" to null, "max" to null)
    val s = values.sorted()
    val n = s.size.toLong()
    fun q(permille: Long) = s[((permille * n + 999) / 1000 - 1).toInt()]
    return linkedMapOf("count" to s.size, "p50" to q(500), "p90" to q(900), "p99" to q(990), "max" to s.last())
}

private fun ms(us: Long) = us / 1000.0

private fun Double.round1() = Math.round(this * 10) / 10.0

fun timingReport(run: Path, slowLabel: String?): Map<String, Any?> {
    val answers = readTimedAnswers(run.resolve("answers.jsonl"))
    val dup = answers.groupBy { it.requestId }.filterValues { it.size > 1 }.keys
    if (dup.isNotEmpty()) throw InputException("같은 requestId를 두 번 보낸 정답이 있다(${dup.take(3)}) — 시각 지표는 requestId마다 한 번 보낸 실행에서만 정의된다")
    val byId = answers.associateBy { it.requestId }
    val processed = Layer.entries.associateWith { layer ->
        readLayer(run.resolve("db").resolve(layer.file), layer).filter { it.status == layer.processedStatus }.map { it.requestId }.toSet()
    }
    val rowIds = Layer.entries.associateWith { layer -> readLayer(run.resolve("db").resolve(layer.file), layer).map { it.requestId } }
    val timings = Layer.entries.associateWith { layer ->
        val path = run.resolve("timing").resolve("timings-${layer.label}.json")
        val list = readTimings(path, layer != Layer.PAYMENT)
        val twice = list.groupBy { it.requestId }.filterValues { it.size > 1 }.keys
        if (twice.isNotEmpty()) throw InputException("$path: 한 requestId의 시각 기록이 둘 이상(${twice.take(3)})")
        val stray = list.map { it.requestId }.filter { it !in byId }
        if (stray.isNotEmpty()) throw InputException("$path: 정답에 없는 requestId의 시각 기록 ${stray.size}건(${stray.take(3)})")
        val byReq = list.associateBy { it.requestId }
        val missing = rowIds.getValue(layer).filter { it !in byReq }
        if (missing.isNotEmpty()) throw InputException("$path: DB 행은 있는데 시각 기록이 없는 requestId ${missing.size}건(${missing.take(3)})")
        byReq
    }

    // 주입한 지연(정답의 fault에서) — 서비스마다
    val delays = FaultDirectives.SERVICES.sorted().associateWith { svc ->
        answers.mapNotNull { a -> FaultDirectives.parse(a.fault).firstOrNull { it.service == svc && it.kind == FaultKind.DELAY }?.delayMs?.toDouble() }
    }

    val edges = linkedMapOf<String, Any?>()
    for ((caller, callee, settingsOf) in listOf(Triple(Layer.ORDER, Layer.INVENTORY, "order"), Triple(Layer.INVENTORY, Layer.PAYMENT, "inventory"))) {
        val calls = timings.getValue(caller).values.filter { it.callOutcome != null }
        val timeouts = calls.filter { it.callOutcome == TIMEOUT_OUTCOME }
        val falseTimeouts = timeouts.count { it.requestId in processed.getValue(callee) }
        val timeoutMs = requestTimeoutMs(run, settingsOf)
        edges["${caller.label}->${callee.label}"] = linkedMapOf(
            "timeoutMs" to timeoutMs,
            "calls" to calls.size,
            "timeouts" to timeouts.size,
            "falseTimeouts" to falseTimeouts,
            "falseTimeoutRatio" to if (calls.isEmpty()) null else falseTimeouts.toDouble() / calls.size,
            "outcomes" to calls.groupingBy { it.callOutcome!! }.eachCount().toSortedMap(),
            "callMs" to quantiles(calls.map { ms(it.callEndUs!! - it.callStartUs!!) }),
            // 넣은 지연이 이 구간의 타임아웃 이상인 요청 수(불린 쪽이 맨 아래 결제일 때 — 거짓 타임아웃의 아래 한계)
            "injectedCalleeDelayAtLeastTimeout" to delays.getValue(callee.label).count { it >= timeoutMs },
        )
    }

    val layers = Layer.entries.associate { layer ->
        val ts = timings.getValue(layer).values
        layer.label to linkedMapOf(
            "records" to ts.size,
            "serviceMs" to quantiles(ts.map { ms(it.endUs - it.startUs) }),
        )
    }

    // 헛일: 위의 층들이 포기한 가장 이른 시각 뒤에도 그 층이 계속한 일
    fun giveUp(layer: Layer, id: String): Long? {
        val candidates = mutableListOf<Long>()
        byId.getValue(id).clientGiveUpUs?.let(candidates::add)
        val above = Layer.entries.takeWhile { it != layer }
        for (u in above) {
            val t = timings.getValue(u)[id] ?: continue
            if (t.callOutcome == TIMEOUT_OUTCOME) candidates += t.callEndUs!!
        }
        return candidates.minOrNull()
    }
    val waste = Layer.entries.associate { layer ->
        val wasted = timings.getValue(layer).values.mapNotNull { t ->
            val g = giveUp(layer, t.requestId) ?: return@mapNotNull null
            if (t.endUs <= g) null else t to ms(t.endUs - maxOf(t.startUs, g))
        }
        layer.label to linkedMapOf(
            "wasted" to wasted.size,
            "wastedProcessed" to wasted.count { (t, _) -> t.requestId in processed.getValue(layer) },
            "startedAfterGiveUp" to wasted.count { (t, _) -> giveUp(layer, t.requestId)!! <= t.startUs },
            "wastedMsSum" to wasted.sumOf { it.second }.round1(),
            "wastedMs" to quantiles(wasted.map { it.second }),
        )
    }

    // 표본
    val firstSent = answers.minOf { it.sentAtMs }
    val lastSent = answers.maxOf { it.sentAtMs }
    val lastEnd = answers.maxOf { it.sentAtMs + it.elapsedMs.toLong() }
    val series = Layer.entries.associate { layer -> layer.label to readSamples(run.resolve("timing").resolve("samples-${layer.label}.json")) }
    val samples = series.mapValues { (svc, s) ->
        val list = s.samples
        if (list.first().atMs > firstSent + 2 * s.intervalMs || list.last().atMs < lastEnd) {
            throw InputException("samples-$svc.json: 표본(${list.first().atMs}~${list.last().atMs})이 부하 구간(${firstSent}~${lastEnd})을 덮지 않는다")
        }
        val inLoad = list.filter { it.atMs in firstSent..lastSent }
        val maxThreads = list.mapNotNull { it.maxThreads }.maxOrNull()
        linkedMapOf(
            "count" to list.size,
            "intervalMs" to s.intervalMs,
            "maxGapMs" to (list.zipWithNext { a, b -> b.atMs - a.atMs }.maxOrNull() ?: 0L),
            "maxInflight" to list.maxOf { it.inflight },
            "maxActive" to list.mapNotNull { it.active }.maxOrNull(),
            "maxThreads" to maxThreads,
            "maxQueue" to list.mapNotNull { it.queue }.maxOrNull(),
            "maxConnections" to list.mapNotNull { it.connections }.maxOrNull(),
            "saturatedMs" to if (maxThreads == null) null else list.count { it.active != null && it.active >= maxThreads } * s.intervalMs,
            "meanLoadDuringSend" to if (inLoad.isEmpty()) null else (inLoad.sumOf { it.load }.toDouble() / inLoad.size).round1(),
        )
    }

    val report = linkedMapOf<String, Any?>(
        "answers" to answers.size,
        "delays" to delays.mapValues { quantiles(it.value) },
        "edges" to edges,
        "layers" to layers,
        "waste" to waste,
        "samples" to samples,
    )
    if (slowLabel != null) report["slow"] = slowWindow(run, slowLabel, answers, series.getValue("order"))
    return report
}

private fun slowWindow(run: Path, label: String, answers: List<TimedAnswer>, order: SampleSeries): Map<String, Any?> {
    val slow = answers.filter { it.fault == label }
    if (slow.isEmpty()) throw InputException("--slow-label '$label' 로 보낸 정답이 없다")
    val start = slow.minOf { it.sentAtMs }
    val end = slow.maxOf { it.sentAtMs }
    val lookups = readLookups(run.resolve("lookups.jsonl"))
    fun phase(list: List<Lookup>) = linkedMapOf(
        "count" to list.size,
        "failed" to list.count { it.status != 200 },
        "elapsedMs" to quantiles(list.map { it.elapsedMs }),
    )
    val normal = answers.filter { it.fault != label }
    fun bad(status: Int, ok: (Int) -> Boolean, elapsed: Double) = !ok(status) || elapsed > BAD_ELAPSED_MS
    val lastBadAfter = (
        normal.filter { it.sentAtMs > end && bad(it.status, { s -> s in 200..299 }, it.elapsedMs) }.map { it.sentAtMs } +
            lookups.filter { it.sentAtMs > end && bad(it.status, { s -> s == 200 }, it.elapsedMs) }.map { it.sentAtMs }
        ).maxOrNull()
    val baseline = order.samples.filter { it.atMs < start }.maxOfOrNull { it.load }
        ?: throw InputException("samples-order.json: 느린 구간 앞의 표본이 없다")
    val lastHighAfter = order.samples.filter { it.atMs > end && it.load > baseline }.maxOfOrNull { it.atMs }
    return linkedMapOf(
        "label" to label,
        "startMs" to start,
        "endMs" to end,
        "slowRequests" to slow.size,
        "lookups" to linkedMapOf(
            "before" to phase(lookups.filter { it.sentAtMs < start }),
            "during" to phase(lookups.filter { it.sentAtMs in start..end }),
            "after" to phase(lookups.filter { it.sentAtMs > end }),
        ),
        "normalChain" to linkedMapOf(
            "before" to verdictCounts(normal.filter { it.sentAtMs < start }),
            "after" to verdictCounts(normal.filter { it.sentAtMs > end }),
        ),
        "slowChain" to verdictCounts(slow),
        "baselineOrderLoad" to baseline,
        "recoveryBySamplesMs" to (lastHighAfter?.let { it - end } ?: 0L),
        "recoveryByClientsMs" to (lastBadAfter?.let { it - end } ?: 0L),
    )
}

private fun verdictCounts(list: List<TimedAnswer>): Map<String, Int> =
    linkedMapOf("count" to list.size) + Verdict.entries.associate { v -> v.label to list.count { Verdict.of(it.status) == v } }

/** 보고를 "a.b.c" → 값으로 편다(수와 null만 — 기대 파일과 metrics.tsv가 쓴다). */
fun flatten(node: Map<String, Any?>, prefix: String = ""): Map<String, Any?> {
    val out = linkedMapOf<String, Any?>()
    node.forEach { (k, v) ->
        val key = if (prefix.isEmpty()) k else "$prefix.$k"
        when (v) {
            is Map<*, *> -> @Suppress("UNCHECKED_CAST") out.putAll(flatten(v as Map<String, Any?>, key))
            is Number, null -> out[key] = v
            else -> Unit
        }
    }
    return out
}
