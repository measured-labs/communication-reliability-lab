package lab.reliability.analyzer

import lab.reliability.chain.FaultDirectives
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path

/** 입력이 비었거나 형식이 틀렸다. 0건으로 통과하지 않고 종료 코드 1로 멈춘다. */
class InputException(message: String) : RuntimeException(message)

val json: JsonMapper = JsonMapper.builder().build()

/** 정답 한 줄 = 부하 도구가 보낸 요청 하나. 형식: docs/measurement.md "1. 정답". */
data class Answer(
    val requestId: String,
    val fault: String,
    val status: Int,
    val errorCode: Int,
    val elapsedMs: Double,
    /** 보낸 본문의 금액 */
    val amount: Long,
)

/** 층(서비스)마다의 DB 행. 판정에 쓰는 것은 request_id, status, 금액(금액 칸이 있는 층만). */
data class LayerRow(val requestId: String, val status: String, val amount: Long?)

enum class Layer(
    val label: String,
    val file: String,
    val header: List<String>,
    val processedStatus: String,
    /** 그 층의 서비스가 쓰는 status 낱말 — 이 밖의 낱말은 입력 오류 */
    val statuses: Set<String>,
) {
    ORDER("order", "orders.csv", listOf("request_id", "amount", "status"), "COMPLETED", setOf("PENDING", "COMPLETED", "FAILED", "UNKNOWN")),
    INVENTORY("inventory", "reservations.csv", listOf("request_id", "status"), "RESERVED", setOf("PENDING", "RESERVED", "FAILED", "UNKNOWN")),
    PAYMENT("payment", "payments.csv", listOf("request_id", "amount", "status"), "APPROVED", setOf("APPROVED")),
    ;

    val hasAmount get() = "amount" in header
}

val FAULT_KINDS = listOf("delay", "error", "fail-after-commit")

class RunInputs(
    val answers: List<Answer>,
    val rows: Map<Layer, List<LayerRow>>,
    /** 서비스 → 장애 종류 → 서비스가 센 수 */
    val faultCounts: Map<String, Map<String, Long>>,
)

fun readRun(dir: Path): RunInputs = RunInputs(
    answers = readAnswers(dir.resolve("answers.jsonl")),
    rows = Layer.entries.associateWith { readLayer(dir.resolve("db").resolve(it.file), it) },
    faultCounts = readFaultCounts(dir.resolve("fault-counts.json")),
)

private fun requireFile(path: Path) {
    if (!Files.isRegularFile(path)) throw InputException("$path: 파일이 없다")
}

/**
 * 줄로 나눈다. 줄끝은 LF만 받는다(CR이 있으면 입력 오류 — 만드는 쪽이 모두 LF를 쓴다).
 * 끝의 줄바꿈 하나는 마지막 줄의 끝으로 본다. 빈 파일 = 0줄.
 */
private fun readLines(path: Path): List<String> {
    requireFile(path)
    val text = Files.readString(path)
    if ('\r' in text) throw InputException("$path: CR(\\r)이 있다 — 줄끝은 LF만 받는다")
    if (text.isEmpty()) return emptyList()
    return text.removeSuffix("\n").split('\n')
}

fun readAnswers(path: Path): List<Answer> {
    val lines = readLines(path)
    if (lines.isEmpty()) throw InputException("$path: 정답이 0줄이다")
    return lines.mapIndexed { index, line ->
        val where = "$path:${index + 1}"
        if (line.isBlank()) throw InputException("$where: 빈 줄")
        val node = try {
            json.readTree(line)
        } catch (e: Exception) {
            throw InputException("$where: JSON이 아니다 (${e.message?.lineSequence()?.first()})")
        }
        if (node == null || !node.isObject) throw InputException("$where: JSON 객체가 아니다")
        if (text(node, "type", where) != "answer") throw InputException("$where: type이 \"answer\"가 아니다")
        val requestId = text(node, "requestId", where)
        if (requestId.isBlank()) throw InputException("$where: requestId가 비었다")
        val status = int(node, "status", where)
        if (status != 0 && status !in 100..599) throw InputException("$where: status $status 는 0이나 100~599가 아니다")
        val elapsed = number(node, "elapsedMs", where)
        if (elapsed < 0) throw InputException("$where: elapsedMs가 음수다")
        val fault = text(node, "fault", where)
        parseFault(fault, where)
        Answer(requestId, fault, status, int(node, "errorCode", where), elapsed, long(node, "amount", where))
    }
}

private fun field(node: JsonNode, name: String, where: String): JsonNode =
    node.get(name)?.takeUnless { it.isNull } ?: throw InputException("$where: '$name'이 없다")

private fun text(node: JsonNode, name: String, where: String): String {
    val v = field(node, name, where)
    if (!v.isString) throw InputException("$where: '$name'이 문자열이 아니다")
    return v.stringValue()
}

/** 정수 칸: JSON 정수만 받는다(`201.0`, `2.01e2`, `"201"`은 입력 오류). */
private fun int(node: JsonNode, name: String, where: String): Int {
    val v = field(node, name, where)
    if (!v.isIntegralNumber || !v.canConvertToInt()) throw InputException("$where: '$name'이 정수가 아니다")
    return v.intValue()
}

private fun long(node: JsonNode, name: String, where: String): Long {
    val v = field(node, name, where)
    if (!v.isIntegralNumber || !v.canConvertToLong()) throw InputException("$where: '$name'이 정수가 아니다")
    return v.longValue()
}

private fun number(node: JsonNode, name: String, where: String): Double {
    val v = field(node, name, where)
    if (!v.isNumber || !v.doubleValue().isFinite()) throw InputException("$where: '$name'이 수가 아니다")
    return v.doubleValue()
}

/**
 * 장애 헤더의 해석은 서비스와 같은 코드(fault-header 모듈의 [FaultDirectives.parse])로 한다 —
 * 서비스가 400으로 거절하는 값을 분석기가 유효한 정답으로 읽지 않게. 빈 값 = 장애 없음.
 * 돌려주는 것: (서비스, 종류) 짝.
 */
fun parseFault(fault: String, where: String): List<Pair<String, String>> = try {
    FaultDirectives.parse(fault).map { it.service to it.kind.label }
} catch (e: IllegalArgumentException) {
    throw InputException("$where: fault '$fault' 가 문법에 맞지 않는다 (${e.message})")
}

private val INTEGER = Regex("-?[0-9]+")

fun readLayer(path: Path, layer: Layer): List<LayerRow> {
    val lines = readLines(path)
    if (lines.isEmpty()) throw InputException("$path: 머리줄도 없다(내보내기가 실패했다)")
    val header = lines.first().split(',')
    if (header != layer.header) throw InputException("$path: 머리줄이 ${layer.header.joinToString(",")} 가 아니다: ${lines.first()}")
    val statusAt = header.indexOf("status")
    val amountAt = header.indexOf("amount")
    return lines.drop(1).mapIndexed { index, line ->
        val where = "$path:${index + 2}"
        if ('"' in line) throw InputException("$where: 따옴표가 든 값은 받지 않는다")
        val cols = line.split(',')
        if (cols.size != header.size) throw InputException("$where: 칸이 ${header.size}개가 아니다")
        if (cols[0].isBlank() || cols[statusAt].isBlank()) throw InputException("$where: request_id나 status가 비었다")
        val status = cols[statusAt]
        if (status !in layer.statuses) throw InputException("$where: status '$status' 는 ${layer.label}이 쓰는 ${layer.statuses} 가 아니다")
        val amount = if (amountAt < 0) {
            null
        } else {
            cols[amountAt].takeIf { INTEGER.matches(it) }?.toLongOrNull()
                ?: throw InputException("$where: amount '${cols[amountAt]}' 가 정수가 아니다")
        }
        LayerRow(cols[0], status, amount)
    }
}

// ───────────── (task 06) 두 서버 시계의 어긋남 — docs/measurement.md 12절 ─────────────

/** `config.env`의 칸 이름. 앞(`_start`)·뒤(`_end`) — 옛 실행(task 06-a)은 앞의 값을 접미사 없는 이름으로 남겼다. */
const val CLOCK_OFFSET_START_KEY = "clock_offset_services_minus_load_ms_start"
const val CLOCK_OFFSET_OLD_KEY = "clock_offset_services_minus_load_ms"
const val CLOCK_OFFSET_END_KEY = "clock_offset_services_minus_load_ms_end"
const val CLOCK_DRIFT_MAX_KEY = "clock_offset_drift_max_ms"

/** 앞·뒤의 차가 이것(ms)보다 크면 그 실행은 입력 오류 — `config.env`에 [CLOCK_DRIFT_MAX_KEY]가 없을 때의 값. 까닭: 12절. */
val CLOCK_DRIFT_MAX_MS_DEFAULT = BigDecimal("5")

private val SIGNED_DECIMAL = Regex("-?[0-9]+(\\.[0-9]+)?")
private val UNSIGNED_DECIMAL = Regex("[0-9]+(\\.[0-9]+)?")

/**
 * 실행 기록에 남은 시계 어긋남(서비스 서버의 시계 − 부하 서버의 시계, ms). [offsetMs] = 앞·뒤의 평균(뒤가 없으면 앞),
 * [shiftMs] = 그것을 ms 정수로 반올림한 것(.5는 0에서 먼 쪽) — 분석기가 서비스의 시각을 부하 서버의 시계로 옮기는 양(`t − shiftMs`).
 */
class ClockOffset(val startMs: BigDecimal, val endMs: BigDecimal?) {
    val offsetMs: BigDecimal = if (endMs == null) startMs else (startMs + endMs).divide(BigDecimal(2))
    // 단순화: 옮기는 양을 ms 정수로 반올림한다 — 남는 오차 ≤ 0.5ms(재는 법의 오차 상한 0.45~1.67ms보다 작다). μs 기록과 ms 기록(표본, 구간 장애)을
    // 같은 정수 양으로 옮겨 서비스끼리 견주는 값이 한 글자도 바뀌지 않게 하려는 것. 0.5ms보다 작은 몫을 따지는 지표가 생기면 μs로 옮긴다.
    val shiftMs: Long = offsetMs.setScale(0, RoundingMode.HALF_UP).longValueExact()

    /** 보고의 `clock` 칸(어긋남이 기록된 실행에만 — 12절). */
    fun report(): Map<String, Any?> = linkedMapOf(
        "offsetStartMs" to startMs.toDouble(),
        "offsetEndMs" to endMs?.toDouble(),
        "offsetMs" to offsetMs.toDouble(),
        "shiftMs" to shiftMs,
    )
}

/**
 * `<실행 폴더>/config.env`에서 시계 어긋남을 읽는다. 파일이 없거나 앞의 값이 없으면 null(어긋남 0 — 노트북의 실행, 옛 실행).
 * 입력 오류: 칸이 두 번, 앞의 값이 두 이름으로 다 있음, 뒤만 있음, 값이 10진 수(`-?[0-9]+(.[0-9]+)?`)가 아님, 문턱이 0 이상의 10진 수가 아님,
 * 앞·뒤의 차가 문턱([CLOCK_DRIFT_MAX_KEY], 없으면 [CLOCK_DRIFT_MAX_MS_DEFAULT])보다 큼.
 */
fun readClockOffset(run: Path): ClockOffset? {
    val path = run.resolve("config.env")
    if (!Files.isRegularFile(path)) return null
    val keys = setOf(CLOCK_OFFSET_START_KEY, CLOCK_OFFSET_OLD_KEY, CLOCK_OFFSET_END_KEY, CLOCK_DRIFT_MAX_KEY)
    val values = linkedMapOf<String, String>()
    Files.readString(path).lines().forEach { line ->
        val key = line.substringBefore('=', "")
        if (key !in keys) return@forEach
        if (key in values) throw InputException("$path: '$key' 가 두 번 나온다")
        values[key] = line.substringAfter('=')
    }
    fun decimal(key: String, re: Regex, what: String): BigDecimal? = values[key]?.let {
        if (!re.matches(it)) throw InputException("$path: $key '$it' 가 $what 가 아니다")
        BigDecimal(it)
    }
    if (CLOCK_OFFSET_START_KEY in values && CLOCK_OFFSET_OLD_KEY in values) {
        throw InputException("$path: 실행 앞의 시계 어긋남이 두 이름($CLOCK_OFFSET_START_KEY, $CLOCK_OFFSET_OLD_KEY)으로 다 있다")
    }
    val start = decimal(CLOCK_OFFSET_START_KEY, SIGNED_DECIMAL, "10진 수(ms)") ?: decimal(CLOCK_OFFSET_OLD_KEY, SIGNED_DECIMAL, "10진 수(ms)")
    val end = decimal(CLOCK_OFFSET_END_KEY, SIGNED_DECIMAL, "10진 수(ms)")
    val driftMax = decimal(CLOCK_DRIFT_MAX_KEY, UNSIGNED_DECIMAL, "0 이상의 10진 수(ms)") ?: CLOCK_DRIFT_MAX_MS_DEFAULT
    if (start == null) {
        if (end != null) throw InputException("$path: 실행 뒤의 시계 어긋남($CLOCK_OFFSET_END_KEY)만 있고 앞의 값이 없다")
        return null
    }
    if (end != null) {
        val drift = (end - start).abs()
        if (drift > driftMax) {
            throw InputException(
                "$path: 실행 앞·뒤의 시계 어긋남이 ${start.toPlainString()} → ${end.toPlainString()}ms, 차 ${drift.toPlainString()}ms가 " +
                    "문턱 ${driftMax.toPlainString()}ms보다 크다 — 실행 동안 두 서버의 시계가 움직여 부하 도구의 시각과 서비스의 시각을 견줄 수 없다",
            )
        }
    }
    return ClockOffset(start, end)
}

fun readFaultCounts(path: Path): Map<String, Map<String, Long>> {
    requireFile(path)
    val node = try {
        json.readTree(Files.readString(path))
    } catch (e: Exception) {
        throw InputException("$path: JSON이 아니다")
    }
    val services = Layer.entries.map { it.label }
    if (node == null || !node.isObject) throw InputException("$path: JSON 객체가 아니다")
    val extra = node.propertyNames().filter { it !in services }
    if (extra.isNotEmpty()) throw InputException("$path: 모르는 키 $extra")
    return services.associateWith { service ->
        val counts = node.get(service)?.takeIf { it.isObject } ?: throw InputException("$path: '$service' 객체가 없다")
        val extraKinds = counts.propertyNames().filter { it !in FAULT_KINDS }
        if (extraKinds.isNotEmpty()) throw InputException("$path: $service 에 모르는 키 $extraKinds")
        FAULT_KINDS.associateWith { kind ->
            val v = counts.get(kind)
            if (v == null || !v.isIntegralNumber || !v.canConvertToLong() || v.longValue() < 0) {
                throw InputException("$path: $service.$kind 가 0 이상의 정수가 아니다")
            }
            v.longValue()
        }
    }
}
