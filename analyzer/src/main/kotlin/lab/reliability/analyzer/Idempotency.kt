package lab.reliability.analyzer

import lab.reliability.chain.AttemptFault
import lab.reliability.chain.AttemptFaultDirectives
import lab.reliability.chain.DropFault
import lab.reliability.chain.DropFaultDirectives
import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path

// task 04의 멱등 키 지표: 두 번 처리됨, 층마다의 키 행과 서비스가 센 수, 같은 트랜잭션의 확인, 중복 요청의 응답,
// 결과 모름의 맞추기, 넣은 끊김·시도 실패의 대조, 층마다의 처리 시간. 형식과 식의 정본: docs/measurement.md 9절.
// 명령: analyzer idem --run <실행 폴더> [--expect <기대 파일>]

private const val IDEM_USAGE = "usage: analyzer idem --run <실행 폴더> [--expect <기대 파일>]"

/** `db/idempotency-<서비스>.csv`의 머리줄. */
val KEY_CSV_HEADER = listOf("key", "request_id", "amount", "state", "response_status")

/** 키 행의 상태 낱말(서비스의 `KeyState`). */
val KEY_STATES = listOf("IN_PROGRESS", "COMPLETED", "FAILED", "UNKNOWN")

/** 서비스가 센 키 수의 종류(`idempotency-counts.json` — 서비스의 `IdempotencyCounters.KINDS`). */
val KEY_COUNT_KINDS = listOf("new", "replayed", "in-progress", "mismatch", "resumed", "missing-key")

/** 정답(1절) + task 04의 칸. 다시 보낸 줄(resends.jsonl)도 같은 꼴이다(`resend` = true). */
data class IdemSend(
    val requestId: String,
    val key: String,
    val status: Int,
    val elapsedMs: Double,
    val sentAtMs: Long,
    val amount: Long,
    val copy: Int,
    val attemptFaults: List<AttemptFault>,
    val dropFaults: List<DropFault>,
    val replayed: Boolean,
    val resend: Boolean,
) {
    val completed get() = Verdict.of(status) == Verdict.COMPLETED
}

data class KeyCsvRow(val key: String, val requestId: String, val amount: Long, val state: String, val responseStatus: Int?)

fun runIdempotency(args: Array<String>): Int {
    val options = try {
        parseIdemArgs(args)
    } catch (e: InputException) {
        System.err.println(e.message)
        System.err.println(IDEM_USAGE)
        return EXIT_INPUT
    }
    val run = Path.of(options.getValue("--run"))
    val out = listOf(run.resolve("report-idem.json"), run.resolve("metrics-idem.tsv"))
    return try {
        out.forEach(Files::deleteIfExists)
        val expect = options["--expect"]?.let { readTimingExpect(Path.of(it)) }
        val (report, alwaysDifferences) = idempotencyReport(run)
        val metrics = flatten(report)
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
        json.writerWithDefaultPrettyPrinter().writeValue(run.resolve("report-idem.json").toFile(), full)
        Files.writeString(run.resolve("metrics-idem.tsv"), metrics.entries.joinToString("") { "${it.key}\t${it.value ?: ""}\n" })
        println("${run.fileName}: 멱등 키 분석 — " + idemSummaryKeys.mapNotNull { k -> metrics[k]?.let { "$k=$it" } }.joinToString(", ") +
            if (differences.isEmpty()) "" else ", 불일치 ${differences.size}건")
        differences.forEach { System.err.println("불일치: $it") }
        if (differences.isEmpty()) EXIT_OK else EXIT_MISMATCH
    } catch (e: InputException) {
        System.err.println("입력 오류: ${e.message}")
        out.forEach { runCatching { Files.deleteIfExists(it) } }
        EXIT_INPUT
    }
}

private val idemSummaryKeys = listOf(
    "clients.notReachedServer", "clients.completed", "clients.unknown", "doubledTotal",
    "layers.order.keyRows", "layers.payment.counts.replayed", "reconcile.resent", "reconcile.finalOnce",
)

private fun parseIdemArgs(args: Array<String>): Map<String, String> {
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

private fun JsonNode.textField(name: String, where: String, allowEmpty: Boolean): String {
    val v = get(name)
    if (v == null || v.isNull || !v.isString) throw InputException("$where: '$name'이 문자열이 아니다(없거나 null)")
    if (!allowEmpty && v.stringValue().isBlank()) throw InputException("$where: '$name'이 비었다")
    return v.stringValue()
}

private fun JsonNode.intField(name: String, where: String): Long {
    val v = get(name)
    if (v == null || !v.isIntegralNumber || !v.canConvertToLong()) throw InputException("$where: '$name'이 정수가 아니다")
    return v.longValue()
}

private fun JsonNode.boolField(name: String, where: String): Boolean {
    val v = get(name)
    if (v == null || !v.isBoolean) throw InputException("$where: '$name'이 불 값이 아니다")
    return v.booleanValue()
}

/** task 04의 칸(key, copy, attemptFault, dropFault, replayed)을 읽는다 — 정답과 다시 보낸 줄이 같은 칸을 쓴다. */
private fun task04Fields(node: JsonNode, where: String, base: IdemSend): IdemSend {
    val key = node.textField("key", where, allowEmpty = false)
    val copy = node.intField("copy", where)
    if (copy < 1) throw InputException("$where: copy가 1보다 작다")
    val rawAttempt = node.textField("attemptFault", where, allowEmpty = true)
    val attempts = try { AttemptFaultDirectives.parse(rawAttempt) } catch (e: IllegalArgumentException) {
        throw InputException("$where: attemptFault '$rawAttempt' 가 문법에 맞지 않는다 (${e.message})")
    }
    val rawDrop = node.textField("dropFault", where, allowEmpty = true)
    val drops = try { DropFaultDirectives.parse(rawDrop) } catch (e: IllegalArgumentException) {
        throw InputException("$where: dropFault '$rawDrop' 가 문법에 맞지 않는다 (${e.message})")
    }
    return base.copy(key = key, copy = copy.toInt(), attemptFaults = attempts, dropFaults = drops, replayed = node.boolField("replayed", where))
}

fun readIdemAnswers(path: Path): List<IdemSend> {
    val timed = readTimedAnswers(path)
    val plain = readAnswers(path)
    val lines = Files.readString(path).removeSuffix("\n").split('\n')
    return timed.mapIndexed { i, a ->
        val base = IdemSend(a.requestId, "", a.status, a.elapsedMs, a.sentAtMs, plain[i].amount, 1, emptyList(), emptyList(), false, false)
        task04Fields(json.readTree(lines[i]), "$path:${i + 1}", base)
    }
}

/** 맞추기 단계의 줄(`{"type":"resend",...}`). 파일이 없으면 null(그 실행에는 맞추기 단계가 없다). 빈 파일 = 0줄. */
fun readResends(path: Path, answerIds: Set<String>): List<IdemSend>? {
    if (!Files.exists(path)) return null
    if (!Files.isRegularFile(path)) throw InputException("$path: 파일이 아니다")
    val text = Files.readString(path)
    if ('\r' in text) throw InputException("$path: CR(\\r)이 있다")
    if (text.isEmpty()) return emptyList()
    return text.removeSuffix("\n").split('\n').mapIndexed { i, line ->
        val where = "$path:${i + 1}"
        val n = try { json.readTree(line) } catch (e: Exception) { throw InputException("$where: JSON이 아니다") }
        if (n == null || !n.isObject) throw InputException("$where: JSON 객체가 아니다")
        if (n.textField("type", where, false) != "resend") throw InputException("$where: type이 \"resend\"가 아니다")
        val id = n.textField("requestId", where, false)
        if (id !in answerIds) throw InputException("$where: 정답에 없는 requestId '$id'")
        val status = n.intField("status", where).toInt()
        if (status != 0 && status !in 100..599) throw InputException("$where: status $status 는 0이나 100~599가 아니다")
        val e = n.get("elapsedMs")
        if (e == null || !e.isNumber || e.doubleValue() < 0) throw InputException("$where: elapsedMs가 0 이상의 수가 아니다")
        val sentAtMs = n.intField("sentAtMs", where)
        if (sentAtMs < 0) throw InputException("$where: sentAtMs가 음수다")
        val base = IdemSend(id, "", status, e.doubleValue(), sentAtMs, n.intField("amount", where), 1, emptyList(), emptyList(), false, true)
        task04Fields(n, where, base)
    }
}

fun readKeyCsv(path: Path): List<KeyCsvRow> {
    if (!Files.isRegularFile(path)) throw InputException("$path: 파일이 없다")
    val text = Files.readString(path)
    if ('\r' in text) throw InputException("$path: CR(\\r)이 있다")
    if (text.isEmpty()) throw InputException("$path: 머리줄도 없다(내보내기가 실패했다)")
    val lines = text.removeSuffix("\n").split('\n')
    if (lines.first().split(',') != KEY_CSV_HEADER) throw InputException("$path: 머리줄이 ${KEY_CSV_HEADER.joinToString(",")} 가 아니다: ${lines.first()}")
    return lines.drop(1).mapIndexed { i, line ->
        val where = "$path:${i + 2}"
        if ('"' in line) throw InputException("$where: 따옴표가 든 값은 받지 않는다")
        val c = line.split(',')
        if (c.size != KEY_CSV_HEADER.size) throw InputException("$where: 칸이 ${KEY_CSV_HEADER.size}개가 아니다")
        if (c[0].isBlank() || c[1].isBlank()) throw InputException("$where: key나 request_id가 비었다")
        val amount = c[2].takeIf { Regex("-?[0-9]+").matches(it) }?.toLongOrNull() ?: throw InputException("$where: amount '${c[2]}' 가 정수가 아니다")
        if (c[3] !in KEY_STATES) throw InputException("$where: state '${c[3]}' 는 $KEY_STATES 가 아니다")
        val status = if (c[4].isEmpty()) null else c[4].takeIf { Regex("[0-9]{3}").matches(it) }?.toInt()
            ?: throw InputException("$where: response_status '${c[4]}' 가 세 자리 정수나 빈 값이 아니다")
        KeyCsvRow(c[0], c[1], amount, c[3], status)
    }
}

/** `{"order":{<종류>:n,...},"inventory":{...},"payment":{...}}` — 서비스 셋·종류 모두 있고 0 이상의 JSON 정수, 여분의 키는 입력 오류. */
private fun readServiceCounts(path: Path, kinds: List<String>): Map<String, Map<String, Long>> {
    val node = readJson(path)
    val services = Layer.entries.map { it.label }
    if (!node.isObject) throw InputException("$path: JSON 객체가 아니다")
    val extra = node.propertyNames().filter { it !in services }
    if (extra.isNotEmpty()) throw InputException("$path: 모르는 키 $extra")
    return services.associateWith { svc ->
        val c = node.get(svc)?.takeIf { it.isObject } ?: throw InputException("$path: '$svc' 객체가 없다")
        val extraKinds = c.propertyNames().filter { it !in kinds }
        if (extraKinds.isNotEmpty()) throw InputException("$path: $svc 에 모르는 키 $extraKinds")
        kinds.associateWith { kind ->
            val v = c.get(kind)
            if (v == null || !v.isIntegralNumber || !v.canConvertToLong() || v.longValue() < 0) throw InputException("$path: $svc.$kind 가 0 이상의 정수가 아니다")
            v.longValue()
        }
    }
}

/** settings-<서비스>.json의 idempotency.enabled(떠 있는 객체에서 읽은 값). */
private fun idempotencyEnabled(run: Path, service: String): Boolean {
    val path = run.resolve("settings-$service.json")
    val v = readJson(path).get("idempotency")?.get("enabled")
    if (v == null || !v.isBoolean) throw InputException("$path: idempotency.enabled가 불 값이 아니다")
    return v.booleanValue()
}

// ───────────── 계산 ─────────────

/** 보고와, 늘 견주는 것의 차이 줄(9-5). */
fun idempotencyReport(run: Path): Pair<Map<String, Any?>, List<String>> {
    val answers = readIdemAnswers(run.resolve("answers.jsonl"))
    val answerIds = answers.map { it.requestId }.toSet()
    val resends = readResends(run.resolve("resends.jsonl"), answerIds)
    val sends = answers + resends.orEmpty()
    val rows = Layer.entries.associateWith { readLayer(run.resolve("db").resolve(it.file), it) }
    val keyRows = Layer.entries.associateWith { readKeyCsv(run.resolve("db").resolve("idempotency-${it.label}.csv")) }
    val timings = Layer.entries.associateWith { layer ->
        val path = run.resolve("timing").resolve("timings-${layer.label}.json")
        val list = readTimings(path, layer != Layer.PAYMENT)
        val stray = list.filter { it.requestId !in answerIds }
        if (stray.isNotEmpty()) throw InputException("$path: 정답에 없는 requestId의 시각 기록 ${stray.size}건(${stray.take(3).map { it.requestId }})")
        list
    }
    val keyCounts = readServiceCounts(run.resolve("idempotency-counts.json"), KEY_COUNT_KINDS)
    val dropCounts = readServiceCounts(run.resolve("drop-faults.json"), listOf(DropFault.KIND))
    val attemptCounts = readAttemptFaultCounts(run.resolve("attempt-faults.json"))
    val enabled = Layer.entries.associateWith { idempotencyEnabled(run, it.label) }
    val always = mutableListOf<String>()

    val processedById = Layer.entries.associateWith { layer ->
        rows.getValue(layer).filter { it.status == layer.processedStatus }.groupingBy { it.requestId }.eachCount()
    }
    val arrivalsById = Layer.entries.associateWith { layer -> timings.getValue(layer).groupingBy { it.requestId }.eachCount() }
    val completedIds = answers.filter { it.completed }.map { it.requestId }.toSet()

    // ① 서버에 닿지 못한 요청: requestId마다 보낸 수(정답 + 다시 보낸 것) − 주문의 시각 기록 수(0 밑은 0)의 합
    val sendsById = sends.groupingBy { it.requestId }.eachCount()
    val notReached = sendsById.entries.sumOf { (id, n) -> maxOf(0, n - (arrivalsById.getValue(Layer.ORDER)[id] ?: 0)) }
    if (notReached != 0) always += "clients.notReachedServer: 늘 0, 실제 $notReached"

    val clients = linkedMapOf<String, Any?>(
        "answers" to answers.size,
        "requestIds" to answerIds.size,
        "completed" to answers.count { it.completed },
        "failed" to answers.count { Verdict.of(it.status) == Verdict.FAILED },
        "unknown" to answers.count { Verdict.of(it.status) == Verdict.UNKNOWN },
        "noResponse" to answers.count { it.status == 0 },
        "notReachedServer" to notReached,
        "replayed" to answers.count { it.replayed },
        "status" to answers.groupingBy { "${it.status}" }.eachCount().toSortedMap(),
        "elapsedMs" to quantiles(answers.map { it.elapsedMs }),
    )

    // ② 넣은 실패의 대조: 끊김과 시도마다의 실패 — 서비스가 센 수 = 기록에서 다시 센 수(요청마다 min(n, 그 서비스에 든 수)의 합)
    val firstSendById = sends.groupBy { it.requestId }.mapValues { it.value.first() }
    val dropFaults = Layer.entries.associate { layer ->
        val svc = layer.label
        val expected = firstSendById.values.sumOf { s ->
            val d = s.dropFaults.firstOrNull { it.service == svc } ?: return@sumOf 0L
            minOf(d.n, arrivalsById.getValue(layer)[s.requestId] ?: 0).toLong()
        }
        val counted = dropCounts.getValue(svc).getValue(DropFault.KIND)
        if (counted != expected) always += "dropFaults.$svc: 서비스가 센 수 $counted, 기록에서 다시 센 수 $expected"
        svc to linkedMapOf("counted" to counted, "expected" to expected)
    }
    val attemptFaults = Layer.entries.associate { layer ->
        val svc = layer.label
        val expected = AttemptFault.KINDS.associateWith { 0L }.toMutableMap()
        firstSendById.values.forEach { s ->
            val d = s.attemptFaults.firstOrNull { it.service == svc } ?: return@forEach
            val arrivals = arrivalsById.getValue(layer)[s.requestId] ?: 0
            when (d) {
                is AttemptFault.FailFirst -> expected.merge(d.kind, minOf(d.n, arrivals).toLong(), Long::plus)
                is AttemptFault.SlowFirst -> expected.merge(d.kind, minOf(d.n, arrivals).toLong(), Long::plus)
                is AttemptFault.Outage -> throw InputException("구간 장애(outage)는 멱등 키 분석에서 다루지 않는다(requestId ${s.requestId})")
            }
        }
        svc to AttemptFault.KINDS.associateWith { kind ->
            val c = attemptCounts.getValue(svc).getValue(kind)
            val e = expected.getValue(kind)
            if (c != e) always += "attemptFaults.$svc.$kind: 서비스가 센 수 $c, 기록에서 다시 센 수 $e"
            linkedMapOf("counted" to c, "expected" to e)
        }
    }

    // ③ DB 행이 있는 requestId는 그 층의 시각 기록이 하나 이상 있어야 한다(8-5 ③과 같은 검사)
    Layer.entries.forEach { layer ->
        val missing = rows.getValue(layer).map { it.requestId }.distinct().filter { it !in arrivalsById.getValue(layer) }
        if (missing.isNotEmpty()) always += "layers.${layer.label}: DB 행은 있는데 시각 기록이 없는 requestId ${missing.size}건(${missing.take(3)})"
    }

    // 층마다
    val layers = Layer.entries.associate { layer ->
        val svc = layer.label
        val proc = processedById.getValue(layer)
        val keys = keyRows.getValue(layer)
        val rowIds = rows.getValue(layer).map { it.requestId }.toSet()
        val keyIds = keys.map { it.requestId }.toSet()
        val rowsWithoutKey = rows.getValue(layer).count { it.requestId !in keyIds }
        val keysWithoutRow = keys.count { it.requestId !in rowIds }
        val counts = keyCounts.getValue(svc)
        // ④ 같은 트랜잭션의 확인(키가 켜진 층) / 꺼진 층은 키 행·센 수가 모두 0
        if (enabled.getValue(layer)) {
            if (rowsWithoutKey != 0) always += "layers.$svc.rowsWithoutKey: 늘 0, 실제 $rowsWithoutKey"
            if (keysWithoutRow != 0) always += "layers.$svc.keysWithoutRow: 늘 0, 실제 $keysWithoutRow"
            if (counts.getValue("new") != keys.size.toLong()) always += "layers.$svc.counts.new: 키 행 ${keys.size}와 다르다(${counts.getValue("new")})"
        } else {
            if (keys.isNotEmpty()) always += "layers.$svc: 멱등 키가 꺼져 있는데(settings-$svc.json) 키 행이 ${keys.size}개"
            if (counts.values.any { it != 0L }) always += "layers.$svc: 멱등 키가 꺼져 있는데(settings-$svc.json) 센 수가 0이 아니다($counts)"
        }
        svc to linkedMapOf<String, Any?>(
            "arrivals" to timings.getValue(layer).size,
            "rows" to rows.getValue(layer).size,
            "processedRows" to proc.values.sum(),
            "doubled" to proc.count { it.value >= 2 },
            "extraProcessedRows" to proc.values.sumOf { maxOf(0, it - 1) },
            "processedNotCompleted" to proc.entries.filter { it.key !in completedIds }.sumOf { it.value },
            "keyRows" to keys.size,
            "keyStates" to KEY_STATES.associateWith { s -> keys.count { it.state == s } },
            "rowsWithoutKey" to rowsWithoutKey,
            "keysWithoutRow" to keysWithoutRow,
            "counts" to counts,
            "selfMs" to quantiles(timings.getValue(layer).map { t -> (t.endUs - t.startUs - ((t.callEndUs ?: 0) - (t.callStartUs ?: 0))) / 1000.0 }),
            "serviceMs" to quantiles(timings.getValue(layer).map { t -> (t.endUs - t.startUs) / 1000.0 }),
        )
    }
    val doubledTotal = answerIds.count { id -> Layer.entries.any { (processedById.getValue(it)[id] ?: 0) >= 2 } }

    val report = linkedMapOf<String, Any?>(
        "answers" to answers.size,
        "enabled" to Layer.entries.associate { it.label to (if (enabled.getValue(it)) 1 else 0) },
        "clients" to clients,
        "doubledTotal" to doubledTotal,
        "layers" to layers,
        "dropFaults" to dropFaults,
        "attemptFaults" to attemptFaults,
    )

    // 중복 요청(같은 requestId의 정답이 둘 이상인 것이 있을 때만)
    val dupGroups = answers.groupBy { it.requestId }.filterValues { it.size >= 2 }
    if (dupGroups.isNotEmpty()) {
        val orderTimingsById = timings.getValue(Layer.ORDER).groupBy { it.requestId }
        report["duplicates"] = linkedMapOf(
            "requestIds" to dupGroups.size,
            "pairs" to dupGroups.values.groupingBy { g -> g.map { it.status }.sorted().joinToString("+") }.eachCount().toSortedMap(),
            "secondCopyStatus" to dupGroups.values.mapNotNull { g -> g.firstOrNull { it.copy == 2 } }.groupingBy { "${it.status}" }.eachCount().toSortedMap(),
            "secondCopyReplayed" to dupGroups.values.count { g -> g.any { it.copy == 2 && it.replayed } },
            // 주문에 나중에 든 것(시각 기록의 startUs가 가장 늦은 것)의 응답 상태 — 동시에 보낸 쌍에서 "둘째"를 가린다
            "laterArrivalStatus" to dupGroups.keys.mapNotNull { id -> orderTimingsById[id]?.maxByOrNull { it.startUs }?.status }
                .groupingBy { "$it" }.eachCount().toSortedMap(),
            "doubled" to dupGroups.keys.count { id -> Layer.entries.any { (processedById.getValue(it)[id] ?: 0) >= 2 } },
        )
    }

    // 맞추기(맞추기 단계가 있는 실행)
    if (resends != null) {
        val ids = resends.map { it.requestId }.toSet()
        fun processed(id: String) = Layer.entries.map { processedById.getValue(it)[id] ?: 0 }
        report["reconcile"] = linkedMapOf(
            "resent" to resends.size,
            "resentRequestIds" to ids.size,
            "completed" to resends.count { it.completed },
            "replayed" to resends.count { it.replayed },
            "status" to resends.groupingBy { "${it.status}" }.eachCount().toSortedMap(),
            "finalOnce" to ids.count { id -> processed(id).all { it == 1 } },
            "finalDoubled" to ids.count { id -> processed(id).any { it >= 2 } },
            "finalMissing" to ids.count { id -> processed(id).any { it == 0 } },
            "elapsedMs" to quantiles(resends.map { it.elapsedMs }),
        )
    }
    return report to always
}
