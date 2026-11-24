package lab.reliability.analyzer

import lab.reliability.chain.OverloadWindow
import java.nio.file.Files
import java.nio.file.Path

// task 04-b의 한도 지표: 결제의 거절을 세 갈래(서비스가 센 수 · 시각 기록의 503 · 든 시각에 규칙을 다시 적용한 수)로 대조하고,
// 구간 뒤 결제의 거절이 멈출 때까지와 클라이언트의 성공이 평소로 돌아올 때까지의 시간을 낸다. 형식과 식의 정본: docs/measurement.md 10절.
// 명령: analyzer overload --run <실행 폴더> [--expect <기대 파일>]

private const val OVERLOAD_USAGE = "usage: analyzer overload --run <실행 폴더> [--expect <기대 파일>]"

/** 시간 흐름(보고의 series)의 칸(ms). */
const val OVERLOAD_SERIES_BIN_MS = 1000L

/** 회복의 판정: 마지막 나쁜 것 뒤로 이만큼(창의 몇 배) 넘게 조용해야 회복이다(docs/measurement.md 10-3). */
const val QUIET_WINDOWS = 2L

val PHASES = listOf("base", "spike")

data class OverloadAnswer(val requestId: String, val status: Int, val elapsedMs: Double, val sentAtMs: Long, val phase: String) {
    val completed get() = Verdict.of(status) == Verdict.COMPLETED
}

/** settings-<서비스>.json의 overload(떠 있는 한도 객체에서 읽은 값). 꺼져 있으면 limit·windowMs는 null. */
data class OverloadSetting(val enabled: Boolean, val limit: Int?, val windowMs: Long?)

fun runOverload(args: Array<String>): Int {
    val options = try {
        parseOverloadArgs(args)
    } catch (e: InputException) {
        System.err.println(e.message)
        System.err.println(OVERLOAD_USAGE)
        return EXIT_INPUT
    }
    val run = Path.of(options.getValue("--run"))
    val out = listOf(run.resolve("report-overload.json"), run.resolve("metrics-overload.tsv"))
    return try {
        out.forEach(Files::deleteIfExists)
        val expect = options["--expect"]?.let { readTimingExpect(Path.of(it)) }
        val (report, alwaysDifferences) = overloadReport(run)
        val metrics = flatten(report - "series") // series는 보고에만(10-3)
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
        json.writerWithDefaultPrettyPrinter().writeValue(run.resolve("report-overload.json").toFile(), full)
        Files.writeString(run.resolve("metrics-overload.tsv"), metrics.entries.joinToString("") { "${it.key}\t${it.value ?: ""}\n" })
        println("${run.fileName}: 한도 분석 — " + overloadSummaryKeys.joinToString(", ") { k -> "$k=${metrics[k] ?: "없음"}" } +
            if (differences.isEmpty()) "" else ", 불일치 ${differences.size}건")
        differences.forEach { System.err.println("불일치: $it") }
        if (differences.isEmpty()) EXIT_OK else EXIT_MISMATCH
    } catch (e: InputException) {
        System.err.println("입력 오류: ${e.message}")
        out.forEach { runCatching { Files.deleteIfExists(it) } }
        EXIT_INPUT
    }
}

private val overloadSummaryKeys = listOf(
    "payment.rejectedCounted", "payment.rejectedObserved", "payment.rejectedReplayed",
    "payment.recovered", "payment.rejectionsStopMs", "clients.recovered", "clients.recoveryMs", "clients.failed",
)

private fun parseOverloadArgs(args: Array<String>): Map<String, String> {
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

/** 7-1절의 정답(`sentAtMs` 필수) + `phase`(`base`·`spike`). */
fun readOverloadAnswers(path: Path): List<OverloadAnswer> {
    val timed = readTimedAnswers(path)
    val lines = Files.readString(path).removeSuffix("\n").split('\n')
    return timed.mapIndexed { i, a ->
        val v = json.readTree(lines[i]).get("phase")
        if (v == null || !v.isString || v.stringValue() !in PHASES) throw InputException("$path:${i + 1}: 'phase'가 $PHASES 가운데 하나가 아니다")
        OverloadAnswer(a.requestId, a.status, a.elapsedMs, a.sentAtMs, v.stringValue())
    }
}

fun readOverloadSetting(path: Path): OverloadSetting {
    val node = readJson(path).get("overload")
    if (node == null || !node.isObject) throw InputException("$path: overload가 객체가 아니다")
    val enabled = node.get("enabled")
    if (enabled == null || !enabled.isBoolean) throw InputException("$path: overload.enabled가 불 값이 아니다")
    if (!enabled.booleanValue()) return OverloadSetting(false, null, null)
    val limit = node.get("limit")
    val windowMs = node.get("windowMs")
    if (limit == null || !limit.isIntegralNumber || !limit.canConvertToInt() || limit.intValue() < 1) throw InputException("$path: overload.limit이 1 이상의 정수가 아니다")
    if (windowMs == null || !windowMs.isIntegralNumber || !windowMs.canConvertToLong() || windowMs.longValue() < 1) throw InputException("$path: overload.windowMs가 1 이상의 정수가 아니다")
    return OverloadSetting(true, limit.intValue(), windowMs.longValue())
}

/** `{"order":{"arrivals":n,"rejected":n},"inventory":{…},"payment":{…}}` — 서비스 셋·칸 둘 모두, 0 이상의 JSON 정수, 여분의 키는 입력 오류. */
fun readOverloadCounts(path: Path): Map<String, Map<String, Long>> {
    val node = readJson(path)
    val services = Layer.entries.map { it.label }
    val kinds = listOf("arrivals", "rejected")
    if (!node.isObject) throw InputException("$path: JSON 객체가 아니다")
    val extra = node.propertyNames().filter { it !in services }
    if (extra.isNotEmpty()) throw InputException("$path: 모르는 키 $extra")
    return services.associateWith { svc ->
        val c = node.get(svc)?.takeIf { it.isObject } ?: throw InputException("$path: '$svc' 객체가 없다")
        val extraKinds = c.propertyNames().filter { it !in kinds }
        if (extraKinds.isNotEmpty()) throw InputException("$path: $svc 에 모르는 키 $extraKinds")
        kinds.associateWith { k ->
            val v = c.get(k)
            if (v == null || !v.isIntegralNumber || !v.canConvertToLong() || v.longValue() < 0) throw InputException("$path: $svc.$k 가 0 이상의 정수가 아니다")
            v.longValue()
        }
    }
}

// ───────────── 계산 ─────────────

/** 보고와, 늘 견주는 것의 차이 줄(10-4). */
fun overloadReport(run: Path): Pair<Map<String, Any?>, List<String>> {
    val answers = readOverloadAnswers(run.resolve("answers.jsonl"))
    val dup = answers.groupBy { it.requestId }.filterValues { it.size > 1 }.keys
    if (dup.isNotEmpty()) throw InputException("같은 requestId를 두 번 보낸 정답이 있다(${dup.take(3)}) — 한도 지표는 requestId마다 한 번 보낸 실행에서만 정의된다")
    val known = answers.map { it.requestId }.toSet()
    val settings = Layer.entries.associateWith { readOverloadSetting(run.resolve("settings-${it.label}.json")) }
    val payment = settings.getValue(Layer.PAYMENT)
    if (!payment.enabled) throw InputException("settings-payment.json: 결제의 한도가 꺼져 있다 — 이 분석은 결제의 한도가 켜진 실행만 다룬다")
    settings.filterKeys { it != Layer.PAYMENT }.filterValues { it.enabled }.keys.firstOrNull()?.let {
        throw InputException("settings-${it.label}.json: ${it.label}의 한도가 켜져 있다 — 이 분석은 결제의 한도만 다룬다")
    }
    val limit = payment.limit!!
    val windowMs = payment.windowMs!!
    val counted = readOverloadCounts(run.resolve("overload-counts.json"))
    fun timingsOf(layer: Layer): List<Timing> {
        val path = run.resolve("timing").resolve("timings-${layer.label}.json")
        val list = readTimings(path, layer != Layer.PAYMENT)
        val stray = list.filter { it.requestId !in known }
        if (stray.isNotEmpty()) throw InputException("$path: 정답에 없는 requestId의 시각 기록 ${stray.size}건(${stray.take(3).map { it.requestId }})")
        return list
    }
    val pay = timingsOf(Layer.PAYMENT)
    val inv = timingsOf(Layer.INVENTORY)

    val spikeAnswers = answers.filter { it.phase == "spike" }
    if (spikeAnswers.isEmpty()) throw InputException("phase가 spike인 정답이 없다 — 구간을 가릴 수 없다")
    val t0 = answers.minOf { it.sentAtMs }
    val spikeStart = spikeAnswers.minOf { it.sentAtMs }
    val spikeEnd = spikeAnswers.maxOf { it.sentAtMs }
    val lastSent = answers.maxOf { it.sentAtMs }
    val quietMs = QUIET_WINDOWS * windowMs

    // 규칙을 다시 적용한다: 결제의 든 시각(startUs)의 오름차순으로(같은 μs는 파일의 차례 — 묶음 안의 거절 수는 차례와 무관)
    val sorted = pay.sortedBy { it.startUs }
    val window = OverloadWindow(limit, windowMs * 1000)
    var maxWindowCount = 0
    val replayed = sorted.map { t -> window.arrive(t.startUs).also { maxWindowCount = maxOf(maxWindowCount, window.size()) } }
    val observed = sorted.map { it.status == 503 }
    // 같은 μs 묶음마다: 다시 적용한 거절 수 = 시각 기록의 503 수
    val groups = sorted.indices.groupBy { sorted[it].startUs }
    val mismatchedGroups = groups.filterValues { idx -> idx.count { replayed[it] } != idx.count { observed[it] } }.keys.sorted()

    val rejectedObserved = observed.count { it }
    val rejectedReplayed = replayed.count { it }
    val pc = counted.getValue("payment")
    val rejectedMs = sorted.filter { it.status == 503 }.map { it.startUs / 1000 }
    val arrivalMs = sorted.map { it.startUs / 1000 }
    val lastRejected = rejectedMs.maxOrNull()
    val paymentRecovered = lastRejected == null || lastRejected <= lastSent - quietMs

    val bad = answers.filter { !it.completed }
    val lastBad = bad.maxOfOrNull { it.sentAtMs }
    val clientsRecovered = lastBad == null || lastBad <= lastSent - quietMs

    val always = mutableListOf<String>()
    // ① 한도가 꺼진 서비스는 세지 않는다
    for (layer in listOf(Layer.ORDER, Layer.INVENTORY)) {
        val c = counted.getValue(layer.label)
        if (c.getValue("arrivals") != 0L || c.getValue("rejected") != 0L) {
            always += "overload.${layer.label}: 한도가 꺼져 있는데(settings-${layer.label}.json) 센 수가 0이 아니다(arrivals ${c.getValue("arrivals")}, rejected ${c.getValue("rejected")})"
        }
    }
    // ② 서비스가 센 든 수 = 결제의 시각 기록 수
    if (pc.getValue("arrivals") != pay.size.toLong()) always += "payment.arrivalsCounted: 서비스가 센 든 수 ${pc.getValue("arrivals")}, 시각 기록 ${pay.size}"
    // ③ 거절 수 셋
    if (pc.getValue("rejected") != rejectedObserved.toLong() || rejectedObserved != rejectedReplayed) {
        always += "payment.rejected: 서비스가 센 수 ${pc.getValue("rejected")}, 시각 기록의 503 $rejectedObserved, 규칙을 다시 적용한 수 $rejectedReplayed"
    }
    // ④ 같은 μs 묶음마다
    if (mismatchedGroups.isNotEmpty()) {
        always += "payment.rejectedByMicros: 같은 μs 묶음 ${mismatchedGroups.size}개에서 시각 기록의 503과 규칙을 다시 적용한 거절 수가 다르다(${mismatchedGroups.take(3)})"
    }
    // ⑤ 결제의 응답은 201(처리)이나 503(한도)뿐이어야 한다 — 그 밖은 흉내 낸 한도가 아닌 실패다
    val otherStatus = pay.count { it.status != 201 && it.status != 503 }
    if (otherStatus != 0) always += "payment.otherStatus: 늘 0, 실제 $otherStatus"
    // ⑥ 구간 앞은 평소(한도 아래)여야 한다
    val rejectedBefore = rejectedMs.count { it < spikeStart }
    if (rejectedBefore != 0) always += "payment.rejectedBeforeSpike: 늘 0, 실제 $rejectedBefore"
    val badBefore = bad.count { it.sentAtMs < spikeStart }
    if (badBefore != 0) always += "clients.badBeforeSpike: 늘 0, 실제 $badBefore"
    // ⑦ 구간이 한도를 넘었어야 한다
    val rejectedDuring = rejectedMs.count { it in spikeStart..spikeEnd }
    if (rejectedDuring == 0) always += "payment.rejectedDuringSpike: 0보다 커야 한다(구간이 한도를 넘지 않았다)"

    val report = linkedMapOf<String, Any?>(
        "answers" to answers.size,
        "limit" to linkedMapOf("limit" to limit, "windowMs" to windowMs, "quietMs" to quietMs),
        "spike" to linkedMapOf(
            "startMs" to spikeStart - t0,
            "endMs" to spikeEnd - t0,
            "sent" to spikeAnswers.size,
            "baseSent" to answers.size - spikeAnswers.size,
            "lastSentAfterEndMs" to lastSent - spikeEnd,
        ),
        "clients" to linkedMapOf(
            "completed" to answers.count { it.completed },
            "failed" to answers.count { Verdict.of(it.status) == Verdict.FAILED },
            "unknown" to answers.count { Verdict.of(it.status) == Verdict.UNKNOWN },
            "successRatio" to answers.count { it.completed }.toDouble() / answers.size,
            "badBeforeSpike" to badBefore,
            "badDuringSpike" to bad.count { it.sentAtMs in spikeStart..spikeEnd },
            "badAfterSpike" to bad.count { it.sentAtMs > spikeEnd },
            "sentAfterSpike" to answers.count { it.sentAtMs > spikeEnd },
            "recovered" to if (clientsRecovered) 1 else 0,
            // 나쁜 요청이 없으면 0, 회복하지 못했으면 null(값을 만들지 않는다)
            "recoveryMs" to if (!clientsRecovered) null else (lastBad?.minus(spikeEnd) ?: 0L),
            "completedElapsedMs" to quantiles(answers.filter { it.completed }.map { it.elapsedMs }),
            "elapsedMs" to quantiles(answers.map { it.elapsedMs }),
        ),
        "payment" to linkedMapOf(
            "arrivals" to pay.size,
            "arrivalsCounted" to pc.getValue("arrivals"),
            "rejectedCounted" to pc.getValue("rejected"),
            "rejectedObserved" to rejectedObserved,
            "rejectedReplayed" to rejectedReplayed,
            "tieGroups" to groups.count { it.value.size > 1 },
            "tieGroupsMismatched" to mismatchedGroups.size,
            "otherStatus" to otherStatus,
            "maxWindowCount" to maxWindowCount,
            "rejectedBeforeSpike" to rejectedBefore,
            "rejectedDuringSpike" to rejectedDuring,
            "rejectedAfterSpike" to rejectedMs.count { it > spikeEnd },
            "arrivalsAfterSpike" to arrivalMs.count { it > spikeEnd },
            "acceptedAfterSpike" to sorted.count { it.startUs / 1000 > spikeEnd && it.status != 503 },
            "recovered" to if (paymentRecovered) 1 else 0,
            "rejectionsStopMs" to if (!paymentRecovered) null else lastRejected?.minus(spikeEnd),
        ),
        // 재고가 결제를 부른 마지막 시도의 결과마다(재시도가 켜져 있으면 마지막 시도 — 7-1의 call 칸)
        "inventory" to linkedMapOf(
            "callOutcomes" to inv.mapNotNull { it.callOutcome }.groupingBy { it }.eachCount().toSortedMap(),
        ),
    )

    // 시간 흐름(보고에만): t0부터 1s 칸
    val last = (arrivalMs + answers.map { it.sentAtMs + it.elapsedMs.toLong() }).max()
    fun series(times: List<Long>): List<Int> {
        val n = (Math.floorDiv(last - t0, OVERLOAD_SERIES_BIN_MS) + 1).coerceAtLeast(0).toInt()
        val out = IntArray(n)
        times.forEach { t -> val i = Math.floorDiv(t - t0, OVERLOAD_SERIES_BIN_MS).toInt(); if (i in 0 until n) out[i]++ }
        return out.toList()
    }
    report["series"] = linkedMapOf(
        "binMs" to OVERLOAD_SERIES_BIN_MS,
        "originSentAtMs" to t0,
        "paymentArrivals" to series(arrivalMs),
        "paymentRejected" to series(rejectedMs),
        "clientSent" to series(answers.map { it.sentAtMs }),
        "clientBad" to series(bad.map { it.sentAtMs }),
    )
    return report to always
}
