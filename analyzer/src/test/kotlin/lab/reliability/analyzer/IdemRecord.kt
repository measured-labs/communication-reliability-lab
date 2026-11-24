package lab.reliability.analyzer

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// docs/measurement.md 9절(멱등 키 지표 — `analyzer idem`)의 입력(9-2)대로 작은 실행 기록을 손으로 만든다.
// 이 파일은 분석기의 구현을 보지 않고 문서(9절과 9절이 가리키는 1·2·4·7·8절, docs/idempotency.md,
// docs/experiments/task-04-design.md)만으로 쓴다. 1·2절의 정답 줄·DB 행은 RunRecord 로 쓰고,
// 그 위에 task 04 칸(key·copy·attemptFault·dropFault·replayed)·다시 보낸 줄·키 표·센 수·끊은 수·시각 기록·설정을 더한다.
// 9-2의 입력이 아닌 파일(표본, 조회)은 쓰지 않는다. 시도 기록(attempts-<층>.json)은 그 층의 `retry.enabled`가 참일 때만 쓴다.
// 2026-11-24 20:30 뒤의 입력(9-2): 업무 행 id(`db/row-ids-<층>.csv` — 행을 넣은 차례로 1, 2, …)와 키 행의 `row_id`
// (`db/idempotency-row-ids-<층>.csv` — 주지 않으면 그 층에서 키 행의 request_id 로 처음 넣은 업무 행, 없으면 [MISSING_ROW_ID]).
// 설정: 세 층의 `idempotency.enabled`, 주문·재고의 `retry.enabled`(기본 끔).

val IDEM_KINDS = listOf("new", "replayed", "in-progress", "mismatch", "resumed", "missing-key")

/** 업무 행이 없는 키 행이 가리키는 id(어느 층의 업무 행 id 에도 없다). */
const val MISSING_ROW_ID = 9999L

/** 키 행 하나 — rowId 가 null 이면 쓸 때 정한다(위 머리 설명). */
data class KeyEntry(val key: String, val requestId: String, val amount: Long, val state: String, val status: Int?, val rowId: Long?)

class IdemRecord(root: Path, name: String = "idem-20261124T000000-r1") {
    val base = RunRecord(root, name)
    val dir: Path get() = base.dir

    private val timings = LAYERS.associateWith { mutableListOf<String>() }
    val keyRows = LAYERS.associateWith { mutableListOf<KeyEntry>() }

    /** 층마다 업무 행의 requestId — 넣은 차례(id = 차례 + 1). */
    val rowIds = LAYERS.associateWith { mutableListOf<String>() }
    private val counts = mutableMapOf<Pair<String, String>, Int>()
    private val drops = mutableMapOf<String, Int>()
    private val attemptCounted = mutableMapOf<Pair<String, String>, Int>()

    /** 다시 보낸 줄. null 이면 resends.jsonl 을 쓰지 않는다(맞추기 단계가 없는 실행). */
    var resendLines: MutableList<String>? = null

    /** 층마다 `idempotency.enabled`(settings-<층>.json). */
    val enabled = mutableMapOf("order" to true, "inventory" to true, "payment" to true)

    /** 주문·재고의 `retry.enabled`(settings-<층>.json). 참이면 attempts-<층>.json 을 쓴다. */
    val retryEnabled = mutableMapOf("order" to false, "inventory" to false)

    /** 시도 기록(주문·재고) — retryEnabled 인 층만 파일로 쓴다. */
    val attempts = mapOf("order" to mutableListOf<Attempt>(), "inventory" to mutableListOf<Attempt>())
    val attemptsText = mutableMapOf<String, String>()

    /** 값이 있으면 그 파일을 이 내용 그대로 쓴다. */
    val timingsText = mutableMapOf<String, String>()
    val keyCsvText = mutableMapOf<String, String>()
    val rowIdsText = mutableMapOf<String, String>()
    val keyRowIdsText = mutableMapOf<String, String>()
    val settingsText = mutableMapOf<String, String>()
    var countsText: String? = null
    var dropsText: String? = null
    var attemptFaultsText: String? = null
    var resendsText: String? = null

    /** 쓰지 않을 파일: "db/idempotency-order.csv", "idempotency-counts.json", "drop-faults.json", "settings-payment.json" … */
    val omit = mutableSetOf<String>()

    fun answerLine(
        type: String,
        id: String,
        sentAtMs: Long,
        status: Int,
        copy: Int = 1,
        key: String = id,
        dropFault: String = "",
        attemptFault: String = "",
        replayed: Boolean = false,
        elapsedMs: Number = 10,
        amount: Long = amountFor(id),
    ): String =
        """{"type":"$type","requestId":"$id","key":"$key","fault":"","attemptFault":"$attemptFault","dropFault":"$dropFault","amount":$amount,"copy":$copy,"sentAtMs":$sentAtMs,"status":$status,"errorCode":0,"elapsedMs":$elapsedMs,"replayed":$replayed}"""

    fun answer(
        id: String,
        sentAtMs: Long,
        status: Int = 201,
        copy: Int = 1,
        key: String = id,
        dropFault: String = "",
        attemptFault: String = "",
        replayed: Boolean = false,
        elapsedMs: Number = 10,
        amount: Long = amountFor(id),
    ): IdemRecord {
        base.rawAnswer(answerLine("answer", id, sentAtMs, status, copy, key, dropFault, attemptFault, replayed, elapsedMs, amount))
        return this
    }

    fun rawAnswer(line: String): IdemRecord {
        base.rawAnswer(line)
        return this
    }

    fun resend(
        id: String,
        sentAtMs: Long,
        status: Int = 201,
        dropFault: String = "",
        attemptFault: String = "",
        replayed: Boolean = false,
        elapsedMs: Number = 10,
        amount: Long = amountFor(id),
    ): IdemRecord {
        val list = resendLines ?: mutableListOf<String>().also { resendLines = it }
        list += answerLine("resend", id, sentAtMs, status, 1, id, dropFault, attemptFault, replayed, elapsedMs, amount)
        return this
    }

    fun row(layer: String, id: String, status: String, amount: Long = amountFor(id)): IdemRecord {
        base.row(layer, id, status, amount)
        rowIds.getValue(layer) += id
        return this
    }

    /** 그 층에서 그 requestId 로 [nth]번째(0부터) 넣은 업무 행의 id. 없으면 null. */
    fun rowIdOf(layer: String, requestId: String, nth: Int = 0): Long? {
        val ids = rowIds.getValue(layer).withIndex().filter { it.value == requestId }.map { it.index + 1L }
        return ids.getOrNull(nth)
    }

    /** 키 행 하나(`key,request_id,amount,state,response_status`). [status]가 null 이면 빈 칸. [rowId]가 null 이면 쓸 때 정한다. */
    fun key(
        layer: String,
        key: String,
        state: String,
        status: Int?,
        requestId: String = key,
        amount: Long = amountFor(requestId),
        rowId: Long? = null,
    ): IdemRecord {
        keyRows.getValue(layer) += KeyEntry(key, requestId, amount, state, status, rowId)
        return this
    }

    fun clearKeys(layer: String): IdemRecord {
        keyRows.getValue(layer).clear()
        return this
    }

    fun attempt(layer: String, a: Attempt): IdemRecord {
        attempts.getValue(layer) += a
        return this
    }

    /** 세 층의 키 행(같은 상태·응답). */
    fun keys(id: String, state: String = "COMPLETED", status: Int? = 201, amount: Long = amountFor(id)): IdemRecord {
        for (l in LAYERS) key(l, id, state, status, id, amount)
        return this
    }

    fun timing(
        layer: String,
        id: String,
        startUs: Long,
        endUs: Long,
        status: Int = 201,
        callStartUs: Long? = null,
        callEndUs: Long? = null,
        callOutcome: String? = null,
    ): IdemRecord {
        val outcome = callOutcome?.let { "\"$it\"" } ?: "null"
        timings.getValue(layer) +=
            """{"requestId":"$id","startUs":$startUs,"endUs":$endUs,"status":$status,"callStartUs":$callStartUs,"callEndUs":$callEndUs,"callOutcome":$outcome}"""
        return this
    }

    /**
     * 세 층을 모두 지난 시각 기록(시각은 s 기준 ms): 주문 s+1~s+19(호출 s+2~s+18), 재고 s+3~s+17(호출 s+4~s+16), 결제 s+5~s+15.
     * 자기 몫: 주문 2, 재고 2, 결제 10. 층마다의 status·callOutcome 을 줄 수 있다.
     */
    fun chainTimings(
        id: String,
        s: Long,
        orderStatus: Int = 201,
        orderOutcome: String = "Completed",
        invStatus: Int = 201,
        invOutcome: String = "Completed",
        payStatus: Int = 201,
    ): IdemRecord =
        timing("order", id, us(s + 1), us(s + 19), orderStatus, us(s + 2), us(s + 18), orderOutcome)
            .timing("inventory", id, us(s + 3), us(s + 17), invStatus, us(s + 4), us(s + 16), invOutcome)
            .timing("payment", id, us(s + 5), us(s + 15), payStatus)

    /** 세 층의 처리 행(COMPLETED·RESERVED·APPROVED). */
    fun processedRows(id: String, amount: Long = amountFor(id)): IdemRecord =
        row("order", id, "COMPLETED", amount).row("inventory", id, "RESERVED", amount).row("payment", id, "APPROVED", amount)

    fun counts(service: String, kind: String, n: Int): IdemRecord {
        counts[service to kind] = n
        return this
    }

    fun drops(service: String, n: Int): IdemRecord {
        drops[service] = n
        return this
    }

    fun attemptCounted(service: String, kind: String, n: Int): IdemRecord {
        attemptCounted[service to kind] = n
        return this
    }

    fun write() {
        base.write()
        val db = dir.resolve("db").createDirectories()
        for (l in LAYERS) {
            if ("db/idempotency-$l.csv" in omit) continue
            db.resolve("idempotency-$l.csv").writeText(
                keyCsvText[l] ?: (
                    listOf("key,request_id,amount,state,response_status") +
                        keyRows.getValue(l).map { "${it.key},${it.requestId},${it.amount},${it.state},${it.status ?: ""}" }
                    ).joinToString("\n", postfix = "\n"),
            )
        }
        for (l in LAYERS) {
            if ("db/row-ids-$l.csv" !in omit) {
                db.resolve("row-ids-$l.csv").writeText(
                    rowIdsText[l] ?: (listOf("id,request_id") + rowIds.getValue(l).mapIndexed { i, id -> "${i + 1},$id" })
                        .joinToString("\n", postfix = "\n"),
                )
            }
            if ("db/idempotency-row-ids-$l.csv" !in omit) {
                db.resolve("idempotency-row-ids-$l.csv").writeText(
                    keyRowIdsText[l] ?: (
                        listOf("key,row_id") + keyRows.getValue(l).map {
                            "${it.key},${it.rowId ?: rowIdOf(l, it.requestId) ?: MISSING_ROW_ID}"
                        }
                        ).joinToString("\n", postfix = "\n"),
                )
            }
        }
        val timingDir = dir.resolve("timing").createDirectories()
        for (l in LAYERS) {
            if ("timing/timings-$l.json" in omit) continue
            timingDir.resolve("timings-$l.json").writeText(timingsText[l] ?: timings.getValue(l).joinToString(",", "[", "]"))
        }
        if ("idempotency-counts.json" !in omit) {
            dir.resolve("idempotency-counts.json").writeText(
                countsText ?: LAYERS.joinToString(",", "{", "}") { s ->
                    "\"$s\":" + IDEM_KINDS.joinToString(",", "{", "}") { k -> "\"$k\":${counts[s to k] ?: 0}" }
                },
            )
        }
        if ("drop-faults.json" !in omit) {
            dir.resolve("drop-faults.json").writeText(
                dropsText ?: LAYERS.joinToString(",", "{", "}") { s -> "\"$s\":{\"drop-after-commit\":${drops[s] ?: 0}}" },
            )
        }
        if ("attempt-faults.json" !in omit) {
            dir.resolve("attempt-faults.json").writeText(
                attemptFaultsText ?: LAYERS.joinToString(",", "{", "}") { s ->
                    "\"$s\":" + ATTEMPT_KINDS.joinToString(",", "{", "}") { k -> "\"$k\":${attemptCounted[s to k] ?: 0}" }
                },
            )
        }
        for (l in LAYERS) {
            if ("settings-$l.json" in omit) continue
            val retry = retryEnabled[l]?.let { ""","retry":{"enabled":$it}""" } ?: ""
            dir.resolve("settings-$l.json").writeText(
                settingsText[l] ?: """{"service":"$l","idempotency":{"enabled":${enabled.getValue(l)}}$retry}""",
            )
        }
        for ((l, list) in attempts) {
            if ("timing/attempts-$l.json" in omit) continue
            if (retryEnabled.getValue(l) || l in attemptsText) {
                timingDir.resolve("attempts-$l.json").writeText(attemptsText[l] ?: list.joinToString(",", "[", "]") { it.json() })
            }
        }
        val resendFile = dir.resolve("resends.jsonl")
        when {
            resendsText != null -> resendFile.writeText(resendsText!!)
            resendLines != null -> resendFile.writeText(resendLines!!.joinToString("") { "$it\n" })
        }
    }

    var lastOutput: String = ""

    /** `analyzer idem --run <폴더> [extra]` — 종료 코드 */
    fun runIdem(vararg extra: String): Int {
        write()
        val (code, out) = analyzerCli("idem", "--run", dir.toString(), *extra)
        lastOutput = out
        return code
    }

    val reportIdem: Path get() = dir.resolve("report-idem.json")
    val metricsTsv: Path get() = dir.resolve("metrics-idem.tsv")

    @Suppress("UNCHECKED_CAST")
    fun report(): Map<String, Any?> = RunRecord.MAPPER.readValue(reportIdem.readText(), Map::class.java) as Map<String, Any?>

    /** metrics-idem.tsv 를 이름 → 값(빈 값은 "")으로. */
    fun metrics(): Map<String, String> = metricsTsv.readLines().filter { it.isNotEmpty() }.associate {
        val i = it.indexOf('\t')
        check(i > 0) { "탭이 없는 줄: $it" }
        it.substring(0, i) to it.substring(i + 1)
    }

    /** 멱등 키 분석을 돌려 보고를 돌려준다 — 종료 코드 0이어야 한다. */
    fun analyze(vararg extra: String): Map<String, Any?> {
        val code = runIdem(*extra)
        check(code == 0) { "종료 코드 0을 기대했는데 $code: $lastOutput" }
        return report()
    }

    /** 보고가 나오기만 하면(0 또는 2) 보고를 돌려준다. */
    fun analyzeAny(vararg extra: String): Map<String, Any?> {
        val code = runIdem(*extra)
        check(code == 0 || code == 2) { "보고를 기대했는데 종료 코드 $code: $lastOutput" }
        return report()
    }

    /** 불일치(종료 2)를 기대하고 differences 를 돌려준다. */
    fun mismatch(vararg extra: String): List<String> {
        val code = runIdem(*extra)
        assertEquals(2, code, "불일치는 종료 코드 2: $lastOutput")
        return report().differences()
    }

    /** 입력 오류(9-3): 종료 코드 1, report-idem.json·metrics-idem.tsv 를 쓰지 않는다. 출력을 돌려준다. */
    fun assertInputError(vararg extra: String): String {
        val code = runIdem(*extra)
        assertEquals(1, code, "입력 오류는 종료 코드 1: $lastOutput")
        assertFalse(reportIdem.exists(), "입력 오류면 report-idem.json 을 쓰지 않는다")
        assertFalse(metricsTsv.exists(), "입력 오류면 metrics-idem.tsv 를 쓰지 않는다")
        return lastOutput
    }
}

/** 맞추기 단계의 시각(부하가 끝나고 가라앉은 뒤). */
const val R = T + 10_000

/**
 * 테스트 여럿이 함께 쓰는 기준 기록(키 켬, 세 층 모두). requestId = 키. 시각은 T 기준 ms.
 * - a(T) 정상 — 201. 세 층 처리, 키 셋 COMPLETED 201.
 * - b(T+100) 잇달아 두 번 — copy1 201, copy2 201 replayed(주문이 저장한 응답, 주문에만 듦). 행·키 하나씩.
 * - c(T+200) `payment:drop-after-commit=1` — 결제가 처리하고 끊음(결제 status 0) → 재고 504(Unknown:IO) → 주문 504.
 *   맞추기(R): 주문 이어 하기 → 재고 이어 하기 → 결제 저장한 응답 → 201(replayed 아님). 끝 상태: 행 하나씩 처리됨, 키 COMPLETED.
 * - d(T+300) 같은 키로 잇달아 — copy2 는 금액 +1 → 422(주문에만 듦). 행·키는 copy1 의 금액.
 * - e(T+400) 동시에 두 번 — copy2 가 주문에 먼저 들어 201, copy1 은 나중에 들어 409.
 * - f(T+500) `order:drop-after-commit=1` — 세 층 처리, 주문이 끊음(status 0). 맞추기(R+100): 주문 저장한 응답 201 replayed.
 * 센 수: 주문 new 6·replayed 2(b, f)·in-progress 1(e)·mismatch 1(d)·resumed 1(c), 재고 new 6·resumed 1(c), 결제 new 6·replayed 1(c).
 * 끊은 수: 주문 1(f), 결제 1(c).
 * 경과(ms): a 10, b 20·5, c 1000, d 30·4, e 3·40(copy1·copy2), f 60000 / 다시 보낸 것 c 42, f 7.
 */
fun goodIdemRecord(root: Path): IdemRecord {
    val r = IdemRecord(root)
    // a
    r.answer("a", T, 201, elapsedMs = 10).chainTimings("a", T).processedRows("a").keys("a")
    // b
    val b = T + 100
    r.answer("b", b, 201, copy = 1, elapsedMs = 20)
        .answer("b", b + 30, 201, copy = 2, replayed = true, elapsedMs = 5)
        .chainTimings("b", b)
        .timing("order", "b", us(b + 31), us(b + 33), 201)
        .processedRows("b").keys("b")
    // c
    val c = T + 200
    r.answer("c", c, 504, dropFault = "payment:drop-after-commit=1", elapsedMs = 1000)
        .chainTimings("c", c, orderStatus = 504, orderOutcome = "Unknown:HTTP_504", invStatus = 504, invOutcome = "Unknown:IO_IOException", payStatus = 0)
        .chainTimings("c", R)
        .resend("c", R, 201, dropFault = "payment:drop-after-commit=1", elapsedMs = 42)
        .processedRows("c").keys("c")
    // d
    val d = T + 300
    r.answer("d", d, 201, copy = 1, elapsedMs = 30)
        .answer("d", d + 30, 422, copy = 2, elapsedMs = 4, amount = amountFor("d") + 1)
        .chainTimings("d", d)
        .timing("order", "d", us(d + 31), us(d + 32), 422)
        .processedRows("d").keys("d")
    // e
    val e = T + 400
    r.answer("e", e, 409, copy = 1, elapsedMs = 3)
        .answer("e", e, 201, copy = 2, elapsedMs = 40)
        .chainTimings("e", e) // copy2: 주문 e+1 에 듦
        .timing("order", "e", us(e + 2), us(e + 4), 409) // copy1: 나중에 듦
        .processedRows("e").keys("e")
    // f
    val f = T + 500
    r.answer("f", f, 0, dropFault = "order:drop-after-commit=1", elapsedMs = 60000)
        .chainTimings("f", f, orderStatus = 0)
        .timing("order", "f", us(R + 101), us(R + 103), 201)
        .resend("f", R + 100, 201, dropFault = "order:drop-after-commit=1", replayed = true, elapsedMs = 7)
        .processedRows("f").keys("f")
    return r
        .counts("order", "new", 6).counts("order", "replayed", 2).counts("order", "in-progress", 1)
        .counts("order", "mismatch", 1).counts("order", "resumed", 1)
        .counts("inventory", "new", 6).counts("inventory", "resumed", 1)
        .counts("payment", "new", 6).counts("payment", "replayed", 1)
        .drops("order", 1).drops("payment", 1)
}

/**
 * 키 꺼짐(세 층 모두)의 기준 기록 — 키 행 없음, 센 수 0. 같은 요청을 두 번 보내면 두 번 처리된다(task 01 그대로).
 * - a(T) 정상. - b(T+100) 잇달아 두 번 — 둘 다 201, 세 층 모두 처리 행 둘.
 */
fun offIdemRecord(root: Path): IdemRecord {
    val r = IdemRecord(root)
    r.enabled.keys.forEach { r.enabled[it] = false }
    r.answer("a", T, 201).chainTimings("a", T).processedRows("a")
    val b = T + 100
    r.answer("b", b, 201, copy = 1).answer("b", b + 30, 201, copy = 2)
        .chainTimings("b", b).chainTimings("b", b + 30)
        .processedRows("b").processedRows("b")
    return r
}
