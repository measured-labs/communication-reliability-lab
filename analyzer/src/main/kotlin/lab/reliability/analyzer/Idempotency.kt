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
// 명령: analyzer idem --run <실행 폴더> [--expect <기대 파일>] [--no-row-ids]

private const val IDEM_USAGE = "usage: analyzer idem --run <실행 폴더> [--expect <기대 파일>] [--no-row-ids]"

/**
 * `--no-row-ids`: 업무 행 id와 키 행의 row_id를 내보내기 전의 실행(task 04의 첫 62개 — 2026-11-24 20:30 전)을 다시 계산할 때만 준다.
 * 주면 `db/row-ids-*.csv`·`db/idempotency-row-ids-*.csv`를 읽지 않고 `layers.<L>.keyLinks`를 내지 않는다(그 대조도 하지 않는다).
 * 주지 않으면 두 파일은 필수다 — 없으면 입력 오류(조용히 건너뛰지 않는다).
 */
const val NO_ROW_IDS = "--no-row-ids"

/** `db/row-ids-<서비스>.csv`(업무 행의 id)의 머리줄. */
val ROW_IDS_HEADER = listOf("id", "request_id")

/** `db/idempotency-row-ids-<서비스>.csv`(키 행이 가리키는 업무 행)의 머리줄. */
val KEY_ROW_IDS_HEADER = listOf("key", "row_id")

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

/** 업무 행 하나의 id와 requestId(`db/row-ids-<서비스>.csv`). */
data class RowId(val id: Long, val requestId: String)

/** 키 행 하나가 가리키는 업무 행(`db/idempotency-row-ids-<서비스>.csv`). */
data class KeyRowId(val key: String, val rowId: Long)

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
        val (report, alwaysDifferences) = idempotencyReport(run, rowIds = NO_ROW_IDS !in options)
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
    val flags = args.count { it == NO_ROW_IDS }
    if (flags > 1) throw InputException("인자 $NO_ROW_IDS 가 두 번 나왔다")
    val pairs = args.filter { it != NO_ROW_IDS }
    if (pairs.size % 2 != 0) throw InputException("인자는 '--이름 값' 짝이어야 한다($NO_ROW_IDS 밖)")
    val options = linkedMapOf<String, String>()
    if (flags == 1) options[NO_ROW_IDS] = ""
    pairs.chunked(2).forEach { (k, v) ->
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

private val POSITIVE = Regex("[1-9][0-9]*")

/** 머리줄과 줄마다 두 칸인 CSV(따옴표 없음). 줄마다 칸 둘을 돌려준다. */
private fun readTwoColumnCsv(path: Path, header: List<String>): List<Pair<String, String>> {
    if (!Files.isRegularFile(path)) throw InputException("$path: 파일이 없다")
    val text = Files.readString(path)
    if ('\r' in text) throw InputException("$path: CR(\\r)이 있다")
    if (text.isEmpty()) throw InputException("$path: 머리줄도 없다(내보내기가 실패했다)")
    val lines = text.removeSuffix("\n").split('\n')
    if (lines.first().split(',') != header) throw InputException("$path: 머리줄이 ${header.joinToString(",")} 가 아니다: ${lines.first()}")
    return lines.drop(1).mapIndexed { i, line ->
        val where = "$path:${i + 2}"
        if ('"' in line) throw InputException("$where: 따옴표가 든 값은 받지 않는다")
        val c = line.split(',')
        if (c.size != 2) throw InputException("$where: 칸이 2개가 아니다")
        c[0] to c[1]
    }
}

/** 업무 행의 id(`id,request_id` — id는 1 이상의 정수이고 겹치지 않는다, request_id는 비지 않는다). */
fun readRowIds(path: Path): List<RowId> {
    val rows = readTwoColumnCsv(path, ROW_IDS_HEADER).mapIndexed { i, (id, rid) ->
        val where = "$path:${i + 2}"
        if (!POSITIVE.matches(id) || id.toLongOrNull() == null) throw InputException("$where: id '$id' 가 1 이상의 정수가 아니다")
        if (rid.isBlank()) throw InputException("$where: request_id가 비었다")
        RowId(id.toLong(), rid)
    }
    val dup = rows.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    if (dup.isNotEmpty()) throw InputException("$path: 같은 id가 두 번 나왔다(${dup.take(3)})")
    return rows
}

/** 키 행이 가리키는 업무 행(`key,row_id` — key는 비지 않고 겹치지 않는다, row_id는 1 이상의 정수). */
fun readKeyRowIds(path: Path): List<KeyRowId> {
    val rows = readTwoColumnCsv(path, KEY_ROW_IDS_HEADER).mapIndexed { i, (key, id) ->
        val where = "$path:${i + 2}"
        if (key.isBlank()) throw InputException("$where: key가 비었다")
        if (!POSITIVE.matches(id) || id.toLongOrNull() == null) throw InputException("$where: row_id '$id' 가 1 이상의 정수가 아니다")
        KeyRowId(key, id.toLong())
    }
    val dup = rows.groupingBy { it.key }.eachCount().filterValues { it > 1 }.keys
    if (dup.isNotEmpty()) throw InputException("$path: 같은 key가 두 번 나왔다(${dup.take(3)})")
    return rows
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

/** 끊김을 위 층이 본 결과 모름(연결이 끊김)으로 셀 때의 결과 이름 앞부분(docs/measurement.md 9-4 `dropFaults.<서비스>.observedAbove`). */
const val IO_OUTCOME_PREFIX = "Unknown:IO_"

/**
 * 보고와, 늘 견주는 것의 차이 줄(9-5). [rowIds]가 false면(`--no-row-ids`) 업무 행 id와 키 행의 row_id를 읽지 않고 `keyLinks`를 내지 않는다.
 */
fun idempotencyReport(run: Path, rowIds: Boolean = true): Pair<Map<String, Any?>, List<String>> {
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
    // 업무 행 id와 키 행의 row_id(R04-2) — 내보낸 두 표가 같은 순간의 것인지(업무 행의 requestId 차례, 키의 집합)를 먼저 본다
    val rowIdsByLayer = if (!rowIds) null else Layer.entries.associateWith { layer ->
        val path = run.resolve("db").resolve("row-ids-${layer.label}.csv")
        val list = readRowIds(path)
        if (list.map { it.requestId } != rows.getValue(layer).map { it.requestId }) {
            throw InputException("$path: 업무 행의 requestId가 db/${layer.file}와 같은 차례가 아니다(내보내기가 서로 맞지 않는다)")
        }
        list
    }
    val keyRowIdsByLayer = if (!rowIds) null else Layer.entries.associateWith { layer ->
        val path = run.resolve("db").resolve("idempotency-row-ids-${layer.label}.csv")
        val list = readKeyRowIds(path)
        if (list.map { it.key }.toSet() != keyRows.getValue(layer).map { it.key }.toSet()) {
            throw InputException("$path: key의 집합이 db/idempotency-${layer.label}.csv와 다르다(내보내기가 서로 맞지 않는다)")
        }
        list
    }
    // 위 층의 시도 기록(재시도가 켜진 층만 — 끊김을 위 층이 본 수로 견주려고). 켜져 있으면 필수.
    val upperAttempts = mapOf(Layer.ORDER to "order", Layer.INVENTORY to "inventory").mapValues { (_, svc) ->
        if (retryEnabled(run, svc)) readAttempts(run.resolve("timing").resolve("attempts-$svc.json"), answerIds) else null
    }
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

    // ② 넣은 실패의 대조: 끊김과 시도마다의 실패 — 서비스가 센 수 = 기록에서 다시 센 수(요청마다 min(n, 그 서비스에 든 수)의 합).
    //   "첫 보낸 것" = 정답 파일에서 그 requestId가 처음 나오는 줄(다시 보낸 줄보다 앞선다 — 정답 줄은 늘 있다).
    //   끊김은 서비스 밖의 관측과도 견준다(R04-7): 위 층이 본 결과 모름(연결이 끊김) — 주문 층이면 클라이언트의 status 0.
    val firstSendById = sends.groupBy { it.requestId }.mapValues { it.value.first() }
    fun observedAbove(layer: Layer): Long = when (layer) {
        Layer.ORDER -> sends.count { it.status == 0 }.toLong()
        else -> {
            val upper = Layer.entries[layer.ordinal - 1]
            val attempts = upperAttempts.getValue(upper)
            attempts?.count { it.outcome.startsWith(IO_OUTCOME_PREFIX) }?.toLong()
                ?: timings.getValue(upper).count { it.callOutcome?.startsWith(IO_OUTCOME_PREFIX) == true }.toLong()
        }
    }
    val dropFaults = Layer.entries.associate { layer ->
        val svc = layer.label
        val expected = firstSendById.values.sumOf { s ->
            val d = s.dropFaults.firstOrNull { it.service == svc } ?: return@sumOf 0L
            minOf(d.n, arrivalsById.getValue(layer)[s.requestId] ?: 0).toLong()
        }
        val counted = dropCounts.getValue(svc).getValue(DropFault.KIND)
        val observed = observedAbove(layer)
        if (counted != expected) always += "dropFaults.$svc: 서비스가 센 수 $counted, 기록에서 다시 센 수 $expected"
        if (counted != observed) always += "dropFaults.$svc: 서비스가 센 수 $counted, 위 층이 본 끊김 $observed"
        svc to linkedMapOf("counted" to counted, "expected" to expected, "observedAbove" to observed)
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

    // 클라이언트가 성공으로 받지 못한 requestId의 판정: 결과 모름이 하나라도 있으면 unknown, 아니면 failed(정답 줄만 — 부하 단계)
    val notCompletedVerdict = answers.groupBy { it.requestId }.filterKeys { it !in completedIds }
        .mapValues { (_, list) -> if (list.any { Verdict.of(it.status) == Verdict.UNKNOWN }) Verdict.UNKNOWN else Verdict.FAILED }

    // 층마다
    val layers = Layer.entries.associate { layer ->
        val svc = layer.label
        val proc = processedById.getValue(layer)
        val keys = keyRows.getValue(layer)
        val rowRequestIds = rows.getValue(layer).map { it.requestId }.toSet()
        val keyIds = keys.map { it.requestId }.toSet()
        val rowsWithoutKey = rows.getValue(layer).count { it.requestId !in keyIds }
        val keysWithoutRow = keys.count { it.requestId !in rowRequestIds }
        val counts = keyCounts.getValue(svc)
        // R04-5: 서비스가 센 409·422를 그 층의 시각 기록의 status와 견준다. 끊긴 응답(status 0)은 무엇이었는지 모른다 —
        // 그 가운데 409·422였을 수 있는 것(같은 requestId의 다른 기록이 그 기록이 끝나기 전에 들었다 — 이 실험은 키 = requestId)만큼만 차를 받는다.
        val layerTimings = timings.getValue(layer)
        val timingsById = layerTimings.groupBy { it.requestId }
        val responses = linkedMapOf(
            "409" to layerTimings.count { it.status == 409 }.toLong(),
            "422" to layerTimings.count { it.status == 422 }.toLong(),
            "0" to layerTimings.count { it.status == 0 }.toLong(),
            "0Repeat" to layerTimings.count { t ->
                t.status == 0 && timingsById.getValue(t.requestId).any { o -> o !== t && o.startUs <= t.endUs }
            }.toLong(),
        )
        // R04-2: 키 행 하나가 업무 행 하나를 가리키나(row_id)
        val keyLinks = if (rowIdsByLayer == null || keyRowIdsByLayer == null) null else {
            val businessById = rowIdsByLayer.getValue(layer).associateBy { it.id }
            val keyRequestId = keys.associate { it.key to it.requestId }
            val linked = keyRowIdsByLayer.getValue(layer).groupingBy { it.rowId }.eachCount()
            linkedMapOf(
                "rowsNotLinked" to businessById.keys.count { (linked[it] ?: 0) == 0 },
                "rowsMultiLinked" to businessById.keys.count { (linked[it] ?: 0) >= 2 },
                "keysToMissingRow" to keyRowIdsByLayer.getValue(layer).count { it.rowId !in businessById },
                "requestIdDiffers" to keyRowIdsByLayer.getValue(layer).count { k ->
                    val row = businessById[k.rowId]
                    row != null && row.requestId != keyRequestId.getValue(k.key)
                },
            )
        }
        // ④ 같은 트랜잭션의 확인(키가 켜진 층) / 꺼진 층은 키 행·센 수가 모두 0
        if (enabled.getValue(layer)) {
            if (rowsWithoutKey != 0) always += "layers.$svc.rowsWithoutKey: 늘 0, 실제 $rowsWithoutKey"
            if (keysWithoutRow != 0) always += "layers.$svc.keysWithoutRow: 늘 0, 실제 $keysWithoutRow"
            if (counts.getValue("new") != keys.size.toLong()) always += "layers.$svc.counts.new: 키 행 ${keys.size}와 다르다(${counts.getValue("new")})"
            if (keyLinks != null) {
                if (rows.getValue(layer).size != keys.size) always += "layers.$svc.keyRows: 업무 행 ${rows.getValue(layer).size}과 다르다(${keys.size})"
                keyLinks.forEach { (k, v) -> if (v != 0) always += "layers.$svc.keyLinks.$k: 늘 0, 실제 $v" }
            }
        } else {
            if (keys.isNotEmpty()) always += "layers.$svc: 멱등 키가 꺼져 있는데(settings-$svc.json) 키 행이 ${keys.size}개"
            if (counts.values.any { it != 0L }) always += "layers.$svc: 멱등 키가 꺼져 있는데(settings-$svc.json) 센 수가 0이 아니다($counts)"
        }
        // ⑤ 다시 온 키의 409·422: 시각 기록의 수 ≤ 서비스가 센 수, 넘친 몫의 합 ≤ 409·422였을 수 있는 끊긴 응답 수
        val extra409 = counts.getValue("in-progress") - responses.getValue("409")
        val extra422 = counts.getValue("mismatch") - responses.getValue("422")
        if (extra409 < 0 || extra422 < 0 || extra409 + extra422 > responses.getValue("0Repeat")) {
            always += "layers.$svc.counts: in-progress ${counts.getValue("in-progress")}·mismatch ${counts.getValue("mismatch")}가 " +
                "시각 기록의 409 ${responses.getValue("409")}·422 ${responses.getValue("422")}와 맞지 않는다(409·422였을 수 있는 끊긴 응답 ${responses.getValue("0Repeat")})"
        }
        svc to linkedMapOf<String, Any?>(
            "arrivals" to timings.getValue(layer).size,
            "rows" to rows.getValue(layer).size,
            "processedRows" to proc.values.sum(),
            "doubled" to proc.count { it.value >= 2 },
            "extraProcessedRows" to proc.values.sumOf { maxOf(0, it - 1) },
            "processedNotCompleted" to proc.entries.filter { it.key !in completedIds }.sumOf { it.value },
            "processedClientFailed" to proc.entries.filter { notCompletedVerdict[it.key] == Verdict.FAILED }.sumOf { it.value },
            "processedClientUnknown" to proc.entries.filter { notCompletedVerdict[it.key] == Verdict.UNKNOWN }.sumOf { it.value },
            "keyRows" to keys.size,
            "keyStates" to KEY_STATES.associateWith { s -> keys.count { it.state == s } },
            "rowsWithoutKey" to rowsWithoutKey,
            "keysWithoutRow" to keysWithoutRow,
            "counts" to counts,
            "responses" to responses,
        ).apply { if (keyLinks != null) put("keyLinks", keyLinks) } + linkedMapOf<String, Any?>(
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
        val orderTimingsById = timings.getValue(Layer.ORDER).groupBy { it.requestId }.mapValues { (_, l) -> l.sortedBy { it.startUs } }
        val pairKey = dupGroups.mapValues { (_, g) -> g.map { it.status }.sorted().joinToString("+") }
        // 주문에 나중에 든 것(시각 기록의 startUs가 가장 늦은 것)의 응답 상태. 가장 늦은 둘이 같은 μs면 가릴 수 없어 arrivalTies로 따로 센다(R04-1).
        val laterStatus = mutableListOf<Int>()
        var ties = 0
        dupGroups.keys.forEach { id ->
            val recs = orderTimingsById[id] ?: return@forEach
            if (recs.size >= 2 && recs[recs.size - 1].startUs == recs[recs.size - 2].startUs) ties++ else laterStatus += recs.last().status
        }
        // 주문의 시각 기록이 정확히 둘인 쌍: 두 요청이 든 간격, 그리고 응답이 201+409인 쌍에서 409를 받은 것이 먼저 든 것인가 나중에 든 것인가
        val twoArrivals = dupGroups.keys.mapNotNull { id -> orderTimingsById[id]?.takeIf { it.size == 2 }?.let { id to it } }
        var earlier409 = 0
        var later409 = 0
        var same409 = 0
        twoArrivals.filter { (id, _) -> pairKey.getValue(id) == "201+409" }.forEach { (_, r) ->
            when {
                r[0].startUs == r[1].startUs -> same409++
                r[0].status == 409 -> earlier409++
                r[1].status == 409 -> later409++
            }
        }
        report["duplicates"] = linkedMapOf(
            "requestIds" to dupGroups.size,
            "pairs" to pairKey.values.groupingBy { it }.eachCount().toSortedMap(),
            "secondCopyStatus" to dupGroups.values.mapNotNull { g -> g.firstOrNull { it.copy == 2 } }.groupingBy { "${it.status}" }.eachCount().toSortedMap(),
            "secondCopyReplayed" to dupGroups.values.count { g -> g.any { it.copy == 2 && it.replayed } },
            "laterArrivalStatus" to laterStatus.groupingBy { "$it" }.eachCount().toSortedMap(),
            "arrivalTies" to ties,
            "arrivalGapMs" to quantiles(twoArrivals.map { (_, r) -> (r[1].startUs - r[0].startUs) / 1000.0 }),
            "conflict409" to linkedMapOf("earlierGot409" to earlier409, "laterGot409" to later409, "sameMicros" to same409),
            "doubled" to dupGroups.keys.count { id -> Layer.entries.any { (processedById.getValue(it)[id] ?: 0) >= 2 } },
        )
    }

    // 맞추기(맞추기 단계가 있는 실행)
    if (resends != null) {
        val ids = resends.map { it.requestId }.toSet()
        fun processed(id: String) = Layer.entries.map { processedById.getValue(it)[id] ?: 0 }
        // R04-3: 다시 보낼 후보를 정답에서 다시 센다(docs/measurement.md 9-6의 규칙 — 2xx가 하나도 없고 status 0 또는 504가 있는 requestId)
        val candidates = answers.groupBy { it.requestId }.filterValues { list ->
            list.none { it.status in 200..299 } && list.any { it.status == 0 || it.status == 504 }
        }.keys
        val notResent = candidates.count { it !in ids }
        val notCandidate = ids.count { it !in candidates }
        if (notResent != 0 || notCandidate != 0) {
            always += "reconcile: 다시 보낼 후보 ${candidates.size}건 가운데 다시 보내지 못한 것 ${notResent}건, 후보가 아닌데 다시 보낸 것 ${notCandidate}건"
        }
        report["reconcile"] = linkedMapOf(
            "candidates" to candidates.size,
            "notResent" to notResent,
            "resentNotCandidate" to notCandidate,
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
