package lab.reliability.analyzer

import tools.jackson.databind.ObjectMapper
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// docs/measurement.md 의 입력 형식(1~3절)대로 작은 실행 기록을 손으로 만든다.
// 이 파일은 분석기의 구현을 보지 않고 문서만으로 쓴다.

val LAYERS = listOf("order", "inventory", "payment")
val KINDS = listOf("delay", "error", "fail-after-commit")

private val HEADERS = mapOf(
    "order" to "request_id,amount,status",
    "inventory" to "request_id,status",
    "payment" to "request_id,amount,status",
)
private val CSV_FILES = mapOf("order" to "orders.csv", "inventory" to "reservations.csv", "payment" to "payments.csv")

class RunRecord(root: Path, val name: String = "test-20261124T000000-r1") {
    val dir: Path = root.resolve(name).also { it.createDirectories() }
    private val answerLines = mutableListOf<String>()
    private val rows = LAYERS.associateWith { mutableListOf<String>() }
    private val counted = mutableMapOf<Pair<String, String>, Int>()

    /** 값이 있으면 그 파일을 이 내용 그대로 쓴다(null 이면 위의 줄들로 만든다). */
    var answersText: String? = null
    val csvText = mutableMapOf<String, String>()
    var faultCountsText: String? = null

    /** 쓰지 않을 파일(없는 파일 시험용): "answers.jsonl", "fault-counts.json", "db/orders.csv" 등 */
    val omit = mutableSetOf<String>()

    fun answer(id: String, status: Int = 201, fault: String = "", elapsedMs: Number = 10): RunRecord {
        answerLines += """{"type":"answer","requestId":"$id","fault":"$fault","status":$status,"errorCode":0,"elapsedMs":$elapsedMs}"""
        return this
    }

    fun rawAnswer(line: String): RunRecord {
        answerLines += line
        return this
    }

    fun row(layer: String, id: String, status: String): RunRecord {
        rows.getValue(layer) += if (layer == "inventory") "$id,$status" else "$id,1000,$status"
        return this
    }

    /** 세 층의 행을 한 번에. null 이면 그 층에 행이 없다. */
    fun rows(id: String, order: String?, inventory: String?, payment: String?): RunRecord {
        order?.let { row("order", id, it) }
        inventory?.let { row("inventory", id, it) }
        payment?.let { row("payment", id, it) }
        return this
    }

    /** 완료 응답(201)과 세 층의 처리 행 하나씩 — 모두 맞는 요청 하나. */
    fun completedChain(id: String, elapsedMs: Number = 10): RunRecord =
        answer(id, 201, "", elapsedMs).rows(id, "COMPLETED", "RESERVED", "APPROVED")

    fun counted(service: String, kind: String, n: Int): RunRecord {
        counted[service to kind] = n
        return this
    }

    fun write() {
        if ("answers.jsonl" !in omit) {
            dir.resolve("answers.jsonl").writeText(answersText ?: answerLines.joinToString("\n", postfix = "\n"))
        }
        val db = dir.resolve("db").createDirectories()
        for (layer in LAYERS) {
            val file = CSV_FILES.getValue(layer)
            if ("db/$file" in omit) continue
            val text = csvText[layer]
                ?: (listOf(HEADERS.getValue(layer)) + rows.getValue(layer)).joinToString("\n", postfix = "\n")
            db.resolve(file).writeText(text)
        }
        if ("fault-counts.json" !in omit) {
            val text = faultCountsText ?: LAYERS.joinToString(",", "{", "}") { s ->
                "\"$s\":" + KINDS.joinToString(",", "{", "}") { k -> "\"$k\":${counted[s to k] ?: 0}" }
            }
            dir.resolve("fault-counts.json").writeText(text)
        }
    }

    fun run(vararg extra: String): Int {
        write()
        return runAnalyzer(arrayOf("--run", dir.toString(), *extra))
    }

    val reportJson: Path get() = dir.resolve("report.json")
    val reportMd: Path get() = dir.resolve("report.md")

    @Suppress("UNCHECKED_CAST")
    fun report(): Map<String, Any?> = MAPPER.readValue(reportJson.readText(), Map::class.java) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun rec(): Map<String, Any?> = report()["reconciliation"] as Map<String, Any?>

    /** 분석을 돌려 reconciliation 을 돌려준다. 종료 코드가 0 이나 2 가 아니면(보고가 없으면) 실패. */
    fun analyze(vararg extra: String): Map<String, Any?> {
        val code = run(*extra)
        check(code == 0 || code == 2) { "보고를 기대했는데 종료 코드 $code" }
        return rec()
    }

    /** 입력 오류: 종료 코드 1, 보고 파일을 쓰지 않는다. */
    fun assertInputError(vararg extra: String) {
        assertEquals(1, run(*extra), "입력 오류는 종료 코드 1")
        assertFalse(reportJson.exists(), "입력 오류면 report.json 을 쓰지 않는다")
        assertFalse(reportMd.exists(), "입력 오류면 report.md 를 쓰지 않는다")
    }

    companion object {
        val MAPPER = ObjectMapper()
    }
}

fun writeExpect(root: Path, json: String, fileName: String = "expect.json"): Path =
    root.resolve(fileName).also { it.writeText(json) }

@Suppress("UNCHECKED_CAST")
fun Any?.at(vararg path: String): Any? {
    var cur: Any? = this
    for (p in path) {
        val obj = cur as? Map<String, Any?> ?: error("경로 ${path.joinToString(".")} 의 '$p' 앞이 객체가 아니다: $cur")
        require(p in obj) { "보고에 키 '$p' 가 없다 (경로 ${path.joinToString(".")})" }
        cur = obj[p]
    }
    return cur
}

/** 정수 값(JSON 수)을 Long 으로. */
fun Any?.long(vararg path: String): Long = (at(*path) as Number).toLong()

fun Any?.double(vararg path: String): Double? = (at(*path) as Number?)?.toDouble()

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.differences(): List<String> = this["differences"] as List<String>

/** 층마다 지표 값 — order, inventory, payment 차례. */
fun Map<String, Any?>.perLayer(metric: String): List<Long> = LAYERS.map { long("layers", it, metric) }

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.faultCell(service: String, kind: String): Map<String, Any?> =
    (this["faultCounts"] as List<Map<String, Any?>>).single { it["service"] == service && it["kind"] == kind }
