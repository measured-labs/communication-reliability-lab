package lab.reliability.analyzer

import lab.reliability.chain.FaultDirectives
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
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
