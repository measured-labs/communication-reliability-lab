package lab.reliability.analyzer

import lab.reliability.chain.AttemptFault
import lab.reliability.chain.AttemptFaultDirectives
import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path

// task 03의 재시도 지표: 성공률·응답 시간, 층마다 받은 호출(배수)과 다시 한 일, 시도·결정·간격, 넣은 실패의 대조,
// 50ms 칸의 몰림, 구간 장애의 호출 수와 회복. 형식과 식의 정본: docs/measurement.md 8절.
// 명령: analyzer retry --run <실행 폴더> [--expect <기대 파일>]

private const val RETRY_USAGE = "usage: analyzer retry --run <실행 폴더> [--expect <기대 파일>]"

/** 몰림을 세는 칸(ms). */
const val BIN_MS = 50L

/** 시간 흐름(보고의 series)의 칸(ms). */
const val SERIES_BIN_MS = 100L

/** 재시도가 이 수 이상 든 50ms 칸을 "꽉 찬 칸"으로 센다 — 실험 나의 무리(20건)의 3/4(docs/measurement.md 8-4). */
const val CROWDED_BIN = 15

val DECISIONS = listOf("success", "not-retryable", "retry", "max-attempts", "budget-denied")

private val OUTCOME = Regex("Completed|(Failed|Unknown):\\S+")

data class RetryAnswer(
    val requestId: String,
    val attemptFault: String,
    val directives: List<AttemptFault>,
    val status: Int,
    val elapsedMs: Double,
    val sentAtMs: Long,
) {
    val completed get() = Verdict.of(status) == Verdict.COMPLETED
}

data class Attempt(
    val requestId: String,
    val callId: Long,
    val attempt: Int,
    val startUs: Long,
    val endUs: Long,
    val outcome: String,
    val delayBeforeMs: Long,
    val decision: String,
)

fun runRetry(args: Array<String>): Int {
    val options = try {
        parseRetryArgs(args)
    } catch (e: InputException) {
        System.err.println(e.message)
        System.err.println(RETRY_USAGE)
        removeOnArgError(args, listOf("report-retry.json", "metrics-retry.tsv"))
        return EXIT_INPUT
    }
    val run = Path.of(options.getValue("--run"))
    val out = listOf(run.resolve("report-retry.json"), run.resolve("metrics-retry.tsv"))
    return try {
        out.forEach(Files::deleteIfExists)
        val expect = options["--expect"]?.let { readTimingExpect(Path.of(it)) }
        val (report, alwaysDifferences) = retryReport(run)
        val metrics = flatten(report - "series") // series는 보고에만(8-4)
        val differences = alwaysDifferences.toMutableList()
        expect?.forEach { (key, range) ->
            val v = metrics[key]
            if (v !is Number) {
                differences += "$key: 기대 ${num(range.first)}~${num(range.second)}, 실제 ${v ?: "없음"}"
            } else if (v.toDouble() < range.first || v.toDouble() > range.second) {
                differences += "$key: 기대 ${num(range.first)}~${num(range.second)}, 실제 ${num(v.toDouble())}"
            }
        }
        val full = linkedMapOf<String, Any?>("run" to run.fileName.toString(), "expect" to options["--expect"], "differences" to differences) + report
        json.writerWithDefaultPrettyPrinter().writeValue(run.resolve("report-retry.json").toFile(), full)
        Files.writeString(run.resolve("metrics-retry.tsv"), metrics.entries.joinToString("") { "${it.key}\t${it.value ?: ""}\n" })
        println("${run.fileName}: 재시도 분석 — " + retrySummaryKeys.mapNotNull { k -> metrics[k]?.let { "$k=$it" } }.joinToString(", ") +
            if (differences.isEmpty()) "" else ", 불일치 ${differences.size}건")
        differences.forEach { System.err.println("불일치: $it") }
        if (differences.isEmpty()) EXIT_OK else EXIT_MISMATCH
    } catch (e: InputException) {
        System.err.println("입력 오류: ${e.message}")
        out.forEach { runCatching { Files.deleteIfExists(it) } }
        EXIT_INPUT
    }
}

private val retrySummaryKeys = listOf(
    "clients.notReachedServer", "clients.completed", "clients.successRatio", "doubledTotal",
    "layers.payment.arrivals", "edges.inventory->payment.retries", "edges.inventory->payment.budgetDenied",
    "bins.payment.peakPer50ms",
)

private fun parseRetryArgs(args: Array<String>): Map<String, String> {
    val known = setOf("--run", "--expect")
    if (args.size % 2 != 0) throw InputException("인자는 '--이름 값' 짝이어야 한다")
    val options = linkedMapOf<String, String>()
    args.toList().chunked(2).forEach { (k, v) ->
        if (k !in known) throw InputException("모르는 인자 $k")
        if (k in options) throw InputException("인자 $k 가 두 번 나왔다")
        options[k] = v
    }
    val run = options["--run"] ?: throw InputException("--run 이 없다")
    if (run.isBlank() || Path.of(run).fileName == null) throw InputException("--run '$run' 에 실행 폴더 이름이 없다")
    return options
}

// ───────────── 읽기 ─────────────

/**
 * [shiftMs]: (task 06) 구간 장애(`outage`)의 from·to는 서비스가 **자기 시계로** 판정하는 시각이다 — 이 양을 빼 부하 서버의 시계로
 * 옮긴다([retryReport]는 [ClockOffset.outageShiftMs]를 준다 — docs/measurement.md 12-4). 라벨(`attemptFault` 문자열)은 그대로 둔다.
 */
fun readRetryAnswers(path: Path, shiftMs: Long = 0): List<RetryAnswer> {
    val timed = readTimedAnswers(path)
    val lines = Files.readString(path).removeSuffix("\n").split('\n')
    return timed.mapIndexed { i, a ->
        val where = "$path:${i + 1}"
        val v = json.readTree(lines[i]).get("attemptFault")
        if (v == null || v.isNull || !v.isString) throw InputException("$where: 'attemptFault'가 문자열이 아니다(없거나 null)")
        val raw = v.stringValue()
        val directives = try {
            AttemptFaultDirectives.parse(raw)
        } catch (e: IllegalArgumentException) {
            throw InputException("$where: attemptFault '$raw' 가 문법에 맞지 않는다 (${e.message})")
        }
        val shifted = if (shiftMs == 0L) directives else directives.map { d ->
            if (d is AttemptFault.Outage) d.copy(fromMs = d.fromMs - shiftMs, toMs = d.toMs - shiftMs) else d
        }
        RetryAnswer(a.requestId, raw, shifted, a.status, a.elapsedMs, a.sentAtMs)
    }
}

/** [shiftMs]: [readTimings]와 같다 — 검사 뒤 `startUs`·`endUs`에서 `shiftMs × 1000`을 뺀다. */
fun readAttempts(path: Path, known: Set<String>, shiftMs: Long = 0): List<Attempt> {
    val node = readJson(path)
    if (!node.isArray) throw InputException("$path: JSON 배열이 아니다")
    fun JsonNode.lng(name: String, where: String): Long {
        val v = get(name)
        if (v == null || !v.isIntegralNumber || !v.canConvertToLong()) throw InputException("$where: '$name'이 정수가 아니다")
        return v.longValue()
    }
    fun JsonNode.txt(name: String, where: String): String {
        val v = get(name)
        if (v == null || !v.isString || v.stringValue().isBlank()) throw InputException("$where: '$name'이 비지 않은 문자열이 아니다")
        return v.stringValue()
    }
    val list = node.mapIndexed { i, r ->
        val where = "$path[$i]"
        if (!r.isObject) throw InputException("$where: 객체가 아니다")
        val a = Attempt(
            r.txt("requestId", where), r.lng("callId", where), r.lng("attempt", where).toInt(), r.lng("startUs", where), r.lng("endUs", where),
            r.txt("outcome", where), r.lng("delayBeforeMs", where), r.txt("decision", where),
        )
        if (a.requestId !in known) throw InputException("$where: 정답에 없는 requestId '${a.requestId}'")
        if (a.attempt < 1) throw InputException("$where: attempt가 1보다 작다")
        if (a.startUs < 0 || a.endUs < a.startUs) throw InputException("$where: 시각이 0 ≤ startUs ≤ endUs가 아니다")
        if (!OUTCOME.matches(a.outcome)) throw InputException("$where: outcome '${a.outcome}' 의 꼴이 틀렸다")
        if (a.decision !in DECISIONS) throw InputException("$where: decision '${a.decision}' 은 $DECISIONS 가 아니다")
        if (a.delayBeforeMs < 0 || (a.attempt == 1 && a.delayBeforeMs != 0L)) throw InputException("$where: delayBeforeMs가 음수이거나 첫 시도인데 0이 아니다")
        if ((a.outcome == "Completed") != (a.decision == "success")) throw InputException("$where: outcome Completed와 decision success가 함께 가지 않는다")
        a
    }
    list.groupBy { it.callId }.forEach { (callId, attempts) ->
        val sorted = attempts.sortedBy { it.attempt }
        val where = "$path callId $callId"
        if (sorted.map { it.requestId }.distinct().size != 1) throw InputException("$where: 한 호출에 requestId가 둘 이상")
        if (sorted.map { it.attempt } != (1..sorted.size).toList()) throw InputException("$where: attempt가 1부터 빈틈없이 이어지지 않는다 (${sorted.map { it.attempt }})")
        sorted.dropLast(1).forEach { if (it.decision != "retry") throw InputException("$where: 마지막이 아닌 시도의 decision이 retry가 아니다") }
        if (sorted.last().decision == "retry") throw InputException("$where: 마지막 시도의 decision이 retry다(뒤의 시도 기록이 없다)")
        sorted.zipWithNext().forEach { (p, n) -> if (n.startUs < p.endUs) throw InputException("$where: 시도 ${n.attempt}가 앞 시도가 끝나기 전에 시작했다") }
    }
    val s = shiftMs * 1000
    return if (s == 0L) list else list.map { it.copy(startUs = it.startUs - s, endUs = it.endUs - s) }
}

fun readAttemptFaultCounts(path: Path): Map<String, Map<String, Long>> {
    val node = readJson(path)
    val services = Layer.entries.map { it.label }
    if (!node.isObject) throw InputException("$path: JSON 객체가 아니다")
    val extra = node.propertyNames().filter { it !in services }
    if (extra.isNotEmpty()) throw InputException("$path: 모르는 키 $extra")
    return services.associateWith { svc ->
        val c = node.get(svc)?.takeIf { it.isObject } ?: throw InputException("$path: '$svc' 객체가 없다")
        val extraKinds = c.propertyNames().filter { it !in AttemptFault.KINDS }
        if (extraKinds.isNotEmpty()) throw InputException("$path: $svc 에 모르는 키 $extraKinds")
        AttemptFault.KINDS.associateWith { kind ->
            val v = c.get(kind)
            if (v == null || !v.isIntegralNumber || !v.canConvertToLong() || v.longValue() < 0) throw InputException("$path: $svc.$kind 가 0 이상의 정수가 아니다")
            v.longValue()
        }
    }
}

// ───────────── 계산 ─────────────

/** origin부터 binMs 칸으로 나눠, 하나 이상 든 칸 가운데 칸 시작이 [keep]을 만족하는 칸마다의 수. */
private fun binCounts(times: List<Long>, binMs: Long, origin: Long, keep: (Long) -> Boolean = { true }): List<Int> {
    val counts = HashMap<Long, Int>()
    times.forEach { t -> counts.merge(Math.floorDiv(t - origin, binMs), 1, Int::plus) }
    return counts.filterKeys { keep(origin + it * binMs) }.values.toList()
}

private fun peak(times: List<Long>, binMs: Long, origin: Long, keep: (Long) -> Boolean = { true }): Int =
    binCounts(times, binMs, origin, keep).maxOrNull() ?: 0

/** settings-<서비스>.json의 retry.enabled — 떠 있는 감싸개에서 읽은 값(docs/retry.md 1절). */
internal fun retryEnabled(run: Path, service: String): Boolean {
    val path = run.resolve("settings-$service.json")
    val v = readJson(path).get("retry")?.get("enabled")
    if (v == null || !v.isBoolean) throw InputException("$path: retry.enabled가 불 값이 아니다")
    return v.booleanValue()
}

private fun series(times: List<Long>, origin: Long, last: Long): List<Int> {
    val n = (Math.floorDiv(last - origin, SERIES_BIN_MS) + 1).coerceAtLeast(0).toInt()
    val out = IntArray(n)
    times.forEach { t -> val i = Math.floorDiv(t - origin, SERIES_BIN_MS).toInt(); if (i in 0 until n) out[i]++ }
    return out.toList()
}

/** 보고와, 늘 견주는 것의 차이 줄(8-4). */
fun retryReport(run: Path): Pair<Map<String, Any?>, List<String>> {
    val clock = readClockOffset(run) // (task 06) 서비스의 시각을 부하 서버의 시계로 — 12절
    val shift = clock?.shiftMs ?: 0L
    // 구간 장애의 from·to는 k6가 헤더에 더한 양만큼 되옮긴다 — k6가 뜻한 구간(부하 서버의 시계, 12-4). 옮긴 서비스의 시각과 견줄 때는
    // svcMs로 서비스와 같은 양만 옮긴 값을 쓴다(= 헤더 그대로의 from·to와 기록 그대로의 시각을 견준 것 — 서비스가 판정한 그대로).
    val answers = readRetryAnswers(run.resolve("answers.jsonl"), clock?.outageShiftMs ?: 0L)
    val svcMs: (Long) -> Long = { it + (clock?.outageShiftMs ?: 0L) - shift }
    val dup = answers.groupBy { it.requestId }.filterValues { it.size > 1 }.keys
    if (dup.isNotEmpty()) throw InputException("같은 requestId를 두 번 보낸 정답이 있다(${dup.take(3)}) — 재시도 지표는 requestId마다 한 번 보낸 실행에서만 정의된다")
    val byId = answers.associateBy { it.requestId }
    val rows = Layer.entries.associateWith { readLayer(run.resolve("db").resolve(it.file), it) }
    val timings = Layer.entries.associateWith { layer ->
        val path = run.resolve("timing").resolve("timings-${layer.label}.json")
        val list = readTimings(path, layer != Layer.PAYMENT, shift)
        val stray = list.filter { it.requestId !in byId }
        if (stray.isNotEmpty()) throw InputException("$path: 정답에 없는 requestId의 시각 기록 ${stray.size}건(${stray.take(3).map { it.requestId }})")
        list
    }
    val attempts = mapOf(
        Layer.ORDER to readAttempts(run.resolve("timing").resolve("attempts-order.json"), byId.keys, shift),
        Layer.INVENTORY to readAttempts(run.resolve("timing").resolve("attempts-inventory.json"), byId.keys, shift),
    )
    val counted = readAttemptFaultCounts(run.resolve("attempt-faults.json"))
    val retryOn = mapOf(Layer.ORDER to retryEnabled(run, "order"), Layer.INVENTORY to retryEnabled(run, "inventory"))
    val always = mutableListOf<String>()

    val t0 = answers.minOf { it.sentAtMs }
    val processedById = Layer.entries.associateWith { layer ->
        rows.getValue(layer).filter { it.status == layer.processedStatus }.groupingBy { it.requestId }.eachCount()
    }
    val arrivalsById = Layer.entries.associateWith { layer -> timings.getValue(layer).groupingBy { it.requestId }.eachCount() }

    // DB 행이 있는 requestId는 그 층의 시각 기록이 하나 이상 있어야 한다(8-5 ③) — 기록이 빠져 도착 수가 조용히 줄지 않게
    val layerDifferences = Layer.entries.mapNotNull { layer ->
        val missing = rows.getValue(layer).map { it.requestId }.distinct().filter { it !in arrivalsById.getValue(layer) }
        if (missing.isEmpty()) null else "layers.${layer.label}: DB 행은 있는데 시각 기록이 없는 requestId ${missing.size}건(${missing.take(3)})"
    }

    // 부른 쪽
    val notReached = answers.count { it.status == 0 && it.requestId !in arrivalsById.getValue(Layer.ORDER) }
    val clients = linkedMapOf<String, Any?>(
        "answers" to answers.size,
        "completed" to answers.count { it.completed },
        "failed" to answers.count { Verdict.of(it.status) == Verdict.FAILED },
        "unknown" to answers.count { Verdict.of(it.status) == Verdict.UNKNOWN },
        "successRatio" to answers.count { it.completed }.toDouble() / answers.size,
        "elapsedMs" to quantiles(answers.map { it.elapsedMs }),
        "completedElapsedMs" to quantiles(answers.filter { it.completed }.map { it.elapsedMs }),
        "noResponse" to answers.count { it.status == 0 },
        "notReachedServer" to notReached,
    )
    if (notReached != 0) always += "clients.notReachedServer: 늘 0, 실제 $notReached"

    // 층마다
    val layers = Layer.entries.associate { layer ->
        val arr = arrivalsById.getValue(layer)
        val proc = processedById.getValue(layer)
        layer.label to linkedMapOf<String, Any?>(
            "arrivals" to timings.getValue(layer).size,
            "arrivalsPerRequest" to timings.getValue(layer).size.toDouble() / answers.size,
            "requestsReached" to arr.size,
            "repeatArrivals" to arr.values.sumOf { it - 1 },
            "rows" to rows.getValue(layer).size,
            "processedRows" to proc.values.sum(),
            "doubled" to proc.count { it.value >= 2 },
            "extraProcessedRows" to proc.values.sumOf { maxOf(0, it - 1) },
            "processedNotCompleted" to proc.entries.filter { byId[it.key]?.completed == false }.sumOf { it.value },
        )
    }
    val doubledTotal = answers.count { a -> Layer.entries.any { (processedById.getValue(it)[a.requestId] ?: 0) >= 2 } }

    // 부르는 쪽의 시도
    val edges = linkedMapOf<String, Any?>()
    val edgeDifferences = mutableListOf<String>()
    for ((caller, callee) in listOf(Layer.ORDER to Layer.INVENTORY, Layer.INVENTORY to Layer.PAYMENT)) {
        val list = attempts.getValue(caller)
        val byCall = list.groupBy { it.callId }.mapValues { (_, v) -> v.sortedBy { it.attempt } }
        val retries = list.filter { it.attempt >= 2 }
        val maxAttempt = list.maxOfOrNull { it.attempt } ?: 0
        // 실제 간격(μs 정수) — 앞 시도의 끝 → 이 시도의 시작
        val gaps = byCall.values.flatMap { call -> call.zipWithNext { p, n -> n to (n.startUs - p.endUs) } }
        val name = "edges.${caller.label}->${callee.label}"
        val edge = linkedMapOf<String, Any?>(
            "calls" to byCall.size,
            "attempts" to list.size,
            "retries" to retries.size,
            "budgetDenied" to list.count { it.decision == "budget-denied" },
            "decisions" to DECISIONS.associateWith { d -> list.count { it.decision == d } },
            "callsByAttempts" to (1..maxAttempt).associate { k -> "$k" to byCall.values.count { it.size == k } },
        )
        // 부르는 쪽의 재시도가 켜져 있을 때만 시도 기록이 있다 — 꺼져 있으면 이 지표를 내지 않는다(8-4, 8-5 ④)
        if (retryOn.getValue(caller)) {
            val notArrived = list.size - timings.getValue(callee).size
            edge["attemptsNotArrived"] = notArrived
            if (list.isEmpty()) {
                edgeDifferences += "$name: 재시도가 켜져 있는데(settings-${caller.label}.json) 시도 기록이 없다"
            } else if (notArrived != 0) {
                edgeDifferences += "$name.attemptsNotArrived: 늘 0, 실제 $notArrived"
            }
        } else if (list.isNotEmpty()) {
            edgeDifferences += "$name: 재시도가 꺼져 있는데(settings-${caller.label}.json) 시도 기록이 ${list.size}건"
        }
        edges["${caller.label}->${callee.label}"] = edge + linkedMapOf(
            "delayMs" to (2..maxAttempt).associate { k -> "attempt$k" to quantiles(list.filter { it.attempt == k }.map { it.delayBeforeMs.toDouble() }) },
            "gapMs" to (2..maxAttempt).associate { k -> "attempt$k" to quantiles(gaps.filter { it.first.attempt == k }.map { it.second / 1000.0 }) },
            // μs 정수로 빼서 ms로 — 실수 뺄셈의 꼬리를 남기지 않게
            "gapMinusDelayMs" to quantiles(gaps.map { (n, g) -> (g - n.delayBeforeMs * 1000) / 1000.0 }),
            "retriesPeakPer50ms" to peak(retries.map { it.startUs / 1000 }, BIN_MS, t0),
        )
    }

    // 넣은 실패: 서비스가 센 수와 기록에서 다시 센 수(8-4)
    val attemptFaults = Layer.entries.associate { layer ->
        val svc = layer.label
        val expected = AttemptFault.KINDS.associateWith { 0L }.toMutableMap()
        answers.forEach { a ->
            val d = a.directives.firstOrNull { it.service == svc } ?: return@forEach
            val arrivals = arrivalsById.getValue(layer)[a.requestId] ?: 0
            when (d) {
                is AttemptFault.FailFirst -> expected.merge(d.kind, minOf(d.n, arrivals).toLong(), Long::plus)
                is AttemptFault.SlowFirst -> expected.merge(d.kind, minOf(d.n, arrivals).toLong(), Long::plus)
                is AttemptFault.Outage -> Unit
            }
        }
        val outageOf = answers.associate { a -> a.requestId to a.directives.filterIsInstance<AttemptFault.Outage>().firstOrNull { it.service == svc } }
        expected["outage"] = timings.getValue(layer).count { t ->
            val o = outageOf[t.requestId] ?: return@count false
            t.startUs >= svcMs(o.fromMs) * 1000 && t.startUs < svcMs(o.toMs) * 1000
        }.toLong()
        svc to AttemptFault.KINDS.associateWith { kind ->
            val c = counted.getValue(svc).getValue(kind)
            val e = expected.getValue(kind)
            if (c != e) always += "attemptFaults.$svc.$kind: 서비스가 센 수 $c, 기록에서 다시 센 수 $e"
            linkedMapOf("counted" to c, "expected" to e)
        }
    }

    // 섞음의 라벨마다
    val mix = answers.groupBy { it.attemptFault.ifEmpty { "none" } }.toSortedMap().mapValues { (_, list) ->
        linkedMapOf(
            "count" to list.size,
            "completed" to list.count { it.completed },
            "unknown" to list.count { Verdict.of(it.status) == Verdict.UNKNOWN },
            "paymentArrivals" to list.sumOf { arrivalsById.getValue(Layer.PAYMENT)[it.requestId] ?: 0 },
            "inventoryArrivals" to list.sumOf { arrivalsById.getValue(Layer.INVENTORY)[it.requestId] ?: 0 },
            "elapsedMs" to quantiles(list.map { it.elapsedMs }),
        )
    }

    // 몰림
    val arrivalMs = Layer.entries.associateWith { layer -> timings.getValue(layer).map { it.startUs / 1000 } }
    val bins = Layer.entries.associate { layer ->
        layer.label to linkedMapOf(
            "peakPer50ms" to peak(arrivalMs.getValue(layer), BIN_MS, t0),
            "peakPer1s" to peak(arrivalMs.getValue(layer), 1000, t0),
        )
    }

    val report = linkedMapOf<String, Any?>(
        "answers" to answers.size,
        "clients" to clients,
        "doubledTotal" to doubledTotal,
        "layers" to layers,
        "edges" to edges,
        "attemptFaults" to attemptFaults,
        "mix" to mix,
        "bins" to (linkedMapOf<String, Any?>("binMs" to BIN_MS) + bins),
    )

    // 구간 장애(있으면 하나여야 한다)
    val outages = answers.flatMap { it.directives.filterIsInstance<AttemptFault.Outage>() }.distinct()
    if (outages.size > 1) throw InputException("구간 장애가 하나가 아니다(${outages.take(3)}) — 이 분석은 실행마다 구간 하나를 본다")
    outages.singleOrNull()?.let { o ->
        val layer = Layer.entries.first { it.label == o.service }
        val caller = Layer.entries.getOrNull(layer.ordinal - 1)
        val times = arrivalMs.getValue(layer)
        val retryStarts = caller?.let { c -> attempts[c]?.filter { it.attempt >= 2 }?.map { it.startUs / 1000 } }.orEmpty()
        val during = answers.filter { it.sentAtMs >= o.fromMs && it.sentAtMs < o.toMs }
        val inWindow: (Long) -> Boolean = { it >= o.fromMs && it + BIN_MS <= o.toMs }
        val retryBins = binCounts(retryStarts, BIN_MS, t0, inWindow)
        report["outage"] = linkedMapOf(
            "startMs" to o.fromMs - t0,
            "endMs" to o.toMs - t0,
            "requestsSentDuring" to during.size,
            "completedSentDuring" to during.count { it.completed },
            "calleeArrivalsDuring" to timings.getValue(layer).count { it.startUs >= svcMs(o.fromMs) * 1000 && it.startUs < svcMs(o.toMs) * 1000 },
            "calleePeakPer50msBefore" to peak(times, BIN_MS, t0) { it + BIN_MS <= o.fromMs },
            "calleePeakPer50msDuring" to peak(times, BIN_MS, t0) { it >= o.fromMs && it + BIN_MS <= o.toMs },
            "calleePeakPer50msAfter" to peak(times, BIN_MS, t0) { it >= o.toMs },
            "retriesPeakPer50msDuring" to (retryBins.maxOrNull() ?: 0),
            // 재시도만의 칸 분포(구간 안, 재시도가 하나 이상 든 칸만): 칸의 수(count)와 칸마다의 수의 분위수
            "retryBinsDuring" to quantiles(retryBins.map { it.toDouble() }),
            "retriesInCrowdedBinsDuring" to retryBins.sum().takeIf { it > 0 }?.let { total -> retryBins.filter { it >= CROWDED_BIN }.sum().toDouble() / total },
            "retryTailMs" to retryStarts.maxOrNull()?.let { it - svcMs(o.toMs) },
            "clientRecoveryMs" to answers.filter { !it.completed }.maxOfOrNull { it.sentAtMs }?.let { it - o.toMs },
        )
    }

    clock?.let { report["clock"] = it.report() }

    // 시간 흐름(보고에만 — metrics에 들지 않는다): 100ms 칸마다 결제에 든 수와 재고의 재시도 수
    val last = (arrivalMs.values.flatten() + answers.map { it.sentAtMs + it.elapsedMs.toLong() }).max()
    report["series"] = linkedMapOf(
        "binMs" to SERIES_BIN_MS,
        "originSentAtMs" to t0,
        "paymentArrivals" to series(arrivalMs.getValue(Layer.PAYMENT), t0, last),
        "inventoryRetries" to series(attempts.getValue(Layer.INVENTORY).filter { it.attempt >= 2 }.map { it.startUs / 1000 }, t0, last),
        "orderRetries" to series(attempts.getValue(Layer.ORDER).filter { it.attempt >= 2 }.map { it.startUs / 1000 }, t0, last),
    )
    return report to (always + layerDifferences + edgeDifferences)
}
