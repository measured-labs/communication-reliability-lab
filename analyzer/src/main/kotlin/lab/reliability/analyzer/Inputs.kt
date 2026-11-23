package lab.reliability.analyzer

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
)

/** 층(서비스)마다의 DB 행. 판정에 쓰는 것은 request_id와 status. */
data class LayerRow(val requestId: String, val status: String)

enum class Layer(val label: String, val file: String, val header: List<String>, val processedStatus: String) {
    ORDER("order", "orders.csv", listOf("request_id", "amount", "status"), "COMPLETED"),
    INVENTORY("inventory", "reservations.csv", listOf("request_id", "status"), "RESERVED"),
    PAYMENT("payment", "payments.csv", listOf("request_id", "amount", "status"), "APPROVED"),
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

fun readAnswers(path: Path): List<Answer> {
    requireFile(path)
    val lines = Files.readAllLines(path)
    if (lines.isEmpty() || lines.all { it.isBlank() }) throw InputException("$path: 정답이 0줄이다")
    return lines.mapIndexed { index, line ->
        val where = "$path:${index + 1}"
        if (line.isBlank()) throw InputException("$where: 빈 줄")
        val node = try {
            json.readTree(line)
        } catch (e: Exception) {
            throw InputException("$where: JSON이 아니다 (${e.message?.lineSequence()?.first()})")
        }
        if (!node.isObject) throw InputException("$where: JSON 객체가 아니다")
        if (text(node, "type", where) != "answer") throw InputException("$where: type이 \"answer\"가 아니다")
        val requestId = text(node, "requestId", where)
        if (requestId.isBlank()) throw InputException("$where: requestId가 비었다")
        val status = int(node, "status", where)
        if (status != 0 && status !in 100..599) throw InputException("$where: status $status 는 0이나 100~599가 아니다")
        val elapsed = number(node, "elapsedMs", where)
        if (elapsed < 0) throw InputException("$where: elapsedMs가 음수다")
        val fault = text(node, "fault", where)
        parseFault(fault, where)
        Answer(requestId, fault, status, int(node, "errorCode", where), elapsed)
    }
}

private fun field(node: JsonNode, name: String, where: String): JsonNode =
    node.get(name)?.takeUnless { it.isNull } ?: throw InputException("$where: '$name'이 없다")

private fun text(node: JsonNode, name: String, where: String): String {
    val v = field(node, name, where)
    if (!v.isString) throw InputException("$where: '$name'이 문자열이 아니다")
    return v.stringValue()
}

private fun int(node: JsonNode, name: String, where: String): Int {
    val v = field(node, name, where)
    if (!v.isIntegralNumber || !v.canConvertToInt()) throw InputException("$where: '$name'이 정수가 아니다")
    return v.intValue()
}

private fun number(node: JsonNode, name: String, where: String): Double {
    val v = field(node, name, where)
    if (!v.isNumber) throw InputException("$where: '$name'이 수가 아니다")
    return v.doubleValue()
}

/** 서비스의 장애 헤더 문법과 같다(chain-link FaultDirectives). 빈 문자열 = 장애 없음. */
fun parseFault(fault: String, where: String): List<Pair<String, String>> {
    if (fault.isEmpty()) return emptyList()
    return fault.split(',').map { token ->
        val parts = token.split(':')
        val service = parts.getOrNull(0)
        val kv = parts.getOrNull(1)?.split('=')
        val kind = kv?.getOrNull(0)
        val ok = parts.size == 2 && service in Layer.entries.map { it.label } && kind in FAULT_KINDS &&
            (if (kind == "delay") kv!!.size == 2 && (kv[1].toLongOrNull() ?: 0) > 0 else kv!!.size == 1)
        if (!ok) throw InputException("$where: fault '$fault' 의 '$token'이 문법에 맞지 않는다")
        service!! to kind!!
    }
}

fun readLayer(path: Path, layer: Layer): List<LayerRow> {
    requireFile(path)
    val lines = Files.readAllLines(path)
    if (lines.isEmpty()) throw InputException("$path: 머리줄도 없다(내보내기가 실패했다)")
    val header = lines.first().split(',')
    if (header != layer.header) throw InputException("$path: 머리줄이 ${layer.header.joinToString(",")} 가 아니다: ${lines.first()}")
    val statusAt = header.indexOf("status")
    return lines.drop(1).mapIndexed { index, line ->
        val where = "$path:${index + 2}"
        if ('"' in line) throw InputException("$where: 따옴표가 든 값은 받지 않는다")
        val cols = line.split(',')
        if (cols.size != header.size) throw InputException("$where: 칸이 ${header.size}개가 아니다")
        if (cols[0].isBlank() || cols[statusAt].isBlank()) throw InputException("$where: request_id나 status가 비었다")
        LayerRow(cols[0], cols[statusAt])
    }
}

fun readFaultCounts(path: Path): Map<String, Map<String, Long>> {
    requireFile(path)
    val node = try {
        json.readTree(Files.readString(path))
    } catch (e: Exception) {
        throw InputException("$path: JSON이 아니다")
    }
    return Layer.entries.associate { layer ->
        val service = node.get(layer.label) ?: throw InputException("$path: '${layer.label}'이 없다")
        layer.label to FAULT_KINDS.associateWith { kind ->
            val v = service.get(kind)
            if (v == null || !v.isIntegralNumber || v.longValue() < 0) throw InputException("$path: ${layer.label}.$kind 가 0 이상의 정수가 아니다")
            v.longValue()
        }
    }
}
