package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 1~3절과 5절의 종료 코드 1 — 틀린 입력을 조용히 통과시키지 않는다(종료 코드 1, 보고를 쓰지 않음).
// 틀린 이유로 통과하지 않게: 오류 테스트마다 "기준 기록"에서 한 가지만 바꾼다. 기준 기록이 통과(종료 코드 0)하는 것은
// 기준_… 테스트가 지킨다. 그래서 오류 테스트가 종료 코드 1을 보면 그것은 바꾼 한 가지 때문이다.
class InputErrorTest {
    @TempDir
    lateinit var root: Path

    /** "a"의 금액 — 정답 줄과 세 층의 행이 같은 값을 쓴다. */
    private val amountA = 1234L

    /** 기준 기록: 완료된 요청 "a" 하나(정답 줄 + 세 층의 처리 행). 여기에 기준 줄 [line]을 하나 더해 쓴다. */
    private fun good() = RunRecord(root).completedChain("a", amount = amountA)

    /** 기준 정답 줄 "b"의 칸들(JSON 값 그대로). 실패 응답이라 행이 없어도 잃음·늘 0 지표가 생기지 않는다. */
    private val baseFields = linkedMapOf(
        "type" to "\"answer\"",
        "requestId" to "\"b\"",
        "fault" to "\"\"",
        "amount" to "1500",
        "status" to "500",
        "errorCode" to "0",
        "elapsedMs" to "10",
    )

    /** 기준 줄에서 칸 몇 개를 바꾼 줄. 값이 null 이면 그 칸을 뺀다. 기준에 없는 칸은 더한다. */
    private fun line(vararg change: Pair<String, String?>): String {
        val f = LinkedHashMap<String, String?>(baseFields)
        change.forEach { (k, v) -> f[k] = v }
        return f.filterValues { it != null }.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
    }

    /** 기준 기록의 "a" 줄을 손으로 쓴 것(파일 전체를 손으로 쓰는 테스트용). */
    private fun lineA() = line("requestId" to "\"a\"", "status" to "201", "amount" to "$amountA")

    private val faultZero = """{"delay":0,"error":0,"fail-after-commit":0}"""

    private val orderGood = "a,$amountA,COMPLETED"
    private val inventoryGood = "a,RESERVED"
    private val paymentGood = "a,$amountA,APPROVED"

    private fun orderCsv(vararg rows: String) = (listOf("request_id,amount,status") + rows).joinToString("\n", postfix = "\n")
    private fun inventoryCsv(vararg rows: String) = (listOf("request_id,status") + rows).joinToString("\n", postfix = "\n")
    private fun paymentCsv(vararg rows: String) = (listOf("request_id,amount,status") + rows).joinToString("\n", postfix = "\n")

    // ---- 기준 기록 — 아래 오류 테스트가 바꾸는 출발점 ----

    @Test
    fun 기준_기록은_통과한다() {
        assertEquals(0, good().rawAnswer(line()).run())
    }

    @Test
    fun 기준_정답_파일을_손으로_써도_통과한다() {
        val r = good().apply { answersText = lineA() + "\n" + line() + "\n" }
        assertEquals(0, r.run())
    }

    @Test
    fun 기준_CSV를_손으로_써도_통과한다() {
        val r = good().apply {
            csvText["order"] = orderCsv(orderGood)
            csvText["inventory"] = inventoryCsv(inventoryGood)
            csvText["payment"] = paymentCsv(paymentGood)
        }
        assertEquals(0, r.run())
    }

    @Test
    fun 기준_장애_수_파일을_손으로_써도_통과한다() {
        val r = good().apply { faultCountsText = """{"order":$faultZero,"inventory":$faultZero,"payment":$faultZero}""" }
        assertEquals(0, r.run())
    }

    // ---- answers.jsonl — 파일 ----

    @Test
    fun 정답_파일이_없으면_입력_오류() {
        good().apply { omit += "answers.jsonl" }.assertInputError()
    }

    @Test
    fun 정답_파일이_0줄이면_입력_오류() {
        good().apply { answersText = "" }.assertInputError()
    }

    @Test
    fun 정답에_빈_줄이_있으면_입력_오류() {
        good().apply { answersText = lineA() + "\n\n" + line() + "\n" }.assertInputError()
    }

    @Test
    fun 정답에_공백뿐인_줄이_있으면_입력_오류() {
        good().apply { answersText = lineA() + "\n   \n" + line() + "\n" }.assertInputError()
    }

    @Test
    fun 정답_파일_끝에_줄바꿈이_둘이면_빈_줄이라_입력_오류() {
        good().apply { answersText = lineA() + "\n" + line() + "\n\n" }.assertInputError()
    }

    @Test
    fun 정답의_줄끝이_CRLF면_입력_오류() {
        good().apply { answersText = lineA() + "\r\n" + line() + "\r\n" }.assertInputError()
    }

    @Test
    fun 정답의_마지막_줄끝만_CR이어도_입력_오류() {
        good().apply { answersText = lineA() + "\n" + line() + "\r\n" }.assertInputError()
    }

    // ---- answers.jsonl — 줄의 꼴 ----

    @Test
    fun 정답에_깨진_JSON_줄이_있으면_입력_오류() {
        good().rawAnswer(line().dropLast(1)).assertInputError()
    }

    @Test
    fun 정답_줄이_배열이면_입력_오류() {
        good().rawAnswer("[" + line() + "]").assertInputError()
    }

    @Test
    fun 정답_줄이_수면_입력_오류() {
        good().rawAnswer("5").assertInputError()
    }

    @Test
    fun 정답_줄이_문자열이면_입력_오류() {
        good().rawAnswer("\"answer\"").assertInputError()
    }

    @Test
    fun 정답의_모르는_필드는_무시한다() {
        val l = line("iteration" to "3", "sentAt" to "\"2026-11-23T16:25:43.602Z\"", "extra" to "true")
        assertEquals(0, good().rawAnswer(l).run())
    }

    // 필수 필드 — 하나씩 뺀다

    @Test
    fun 정답에_type이_없으면_입력_오류() {
        good().rawAnswer(line("type" to null)).assertInputError()
    }

    @Test
    fun 정답에_requestId가_없으면_입력_오류() {
        good().rawAnswer(line("requestId" to null)).assertInputError()
    }

    @Test
    fun 정답에_fault가_없으면_입력_오류() {
        good().rawAnswer(line("fault" to null)).assertInputError()
    }

    @Test
    fun 정답에_amount가_없으면_입력_오류() {
        good().rawAnswer(line("amount" to null)).assertInputError()
    }

    @Test
    fun 정답에_status가_없으면_입력_오류() {
        good().rawAnswer(line("status" to null)).assertInputError()
    }

    @Test
    fun 정답에_errorCode가_없으면_입력_오류() {
        good().rawAnswer(line("errorCode" to null)).assertInputError()
    }

    @Test
    fun 정답에_elapsedMs가_없으면_입력_오류() {
        good().rawAnswer(line("elapsedMs" to null)).assertInputError()
    }

    // 필수 필드가 null

    @Test
    fun 정답의_amount가_null이면_입력_오류() {
        good().rawAnswer(line("amount" to "null")).assertInputError()
    }

    @Test
    fun 정답의_fault가_null이면_입력_오류() {
        good().rawAnswer(line("fault" to "null")).assertInputError()
    }

    @Test
    fun 정답의_status가_null이면_입력_오류() {
        good().rawAnswer(line("status" to "null")).assertInputError()
    }

    // 형

    @Test
    fun 정답의_type이_answer가_아니면_입력_오류() {
        good().rawAnswer(line("type" to "\"log\"")).assertInputError()
    }

    @Test
    fun 정답의_requestId가_수면_입력_오류() {
        good().rawAnswer(line("requestId" to "7")).assertInputError()
    }

    @Test
    fun 정답의_requestId가_빈_문자열이면_입력_오류() {
        good().rawAnswer(line("requestId" to "\"\"")).assertInputError()
    }

    @Test
    fun 정답의_requestId가_공백뿐이면_입력_오류() {
        good().rawAnswer(line("requestId" to "\"   \"")).assertInputError()
    }

    @Test
    fun 정답의_fault가_수면_입력_오류() {
        good().rawAnswer(line("fault" to "5")).assertInputError()
    }

    @Test
    fun 정답의_status가_문자열이면_입력_오류() {
        good().rawAnswer(line("status" to "\"500\"")).assertInputError()
    }

    @Test
    fun 정답의_status가_소수점_있는_정수면_입력_오류() {
        good().rawAnswer(line("status" to "500.0")).assertInputError()
    }

    @Test
    fun 정답의_status가_지수_표기면_입력_오류() {
        good().rawAnswer(line("status" to "5.0e2")).assertInputError()
    }

    @Test
    fun 정답의_errorCode가_문자열이면_입력_오류() {
        good().rawAnswer(line("errorCode" to "\"0\"")).assertInputError()
    }

    @Test
    fun 정답의_errorCode가_소수점_있는_정수면_입력_오류() {
        good().rawAnswer(line("errorCode" to "0.0")).assertInputError()
    }

    @Test
    fun 정답의_amount가_문자열이면_입력_오류() {
        good().rawAnswer(line("amount" to "\"1500\"")).assertInputError()
    }

    @Test
    fun 정답의_amount가_소수점_있는_정수면_입력_오류() {
        good().rawAnswer(line("amount" to "1500.0")).assertInputError()
    }

    @Test
    fun 정답의_amount가_지수_표기면_입력_오류() {
        good().rawAnswer(line("amount" to "1.5e3")).assertInputError()
    }

    @Test
    fun 정답의_elapsedMs가_문자열이면_입력_오류() {
        good().rawAnswer(line("elapsedMs" to "\"10\"")).assertInputError()
    }

    @Test
    fun 정답의_elapsedMs는_소수도_받는다() {
        assertEquals(0, good().rawAnswer(line("elapsedMs" to "10.5")).run())
    }

    // 범위

    @Test
    fun 정답의_status가_99면_입력_오류() {
        good().rawAnswer(line("status" to "99")).assertInputError()
    }

    @Test
    fun 정답의_status가_600이면_입력_오류() {
        good().rawAnswer(line("status" to "600")).assertInputError()
    }

    @Test
    fun 정답의_status_0과_100과_599는_받는다() {
        val r = good().rawAnswer(line("status" to "0"))
            .rawAnswer(line("requestId" to "\"c\"", "status" to "100"))
            .rawAnswer(line("requestId" to "\"d\"", "status" to "599"))
        assertEquals(0, r.run())
    }

    @Test
    fun 정답의_elapsedMs가_음수면_입력_오류() {
        good().rawAnswer(line("elapsedMs" to "-1")).assertInputError()
    }

    @Test
    fun 정답의_elapsedMs_0은_받는다() {
        assertEquals(0, good().rawAnswer(line("elapsedMs" to "0")).run())
    }

    // fault 문법(0절)

    @Test
    fun fault의_서비스가_셋_밖이면_입력_오류() {
        good().rawAnswer(line("fault" to "\"shipping:error\"")).assertInputError()
    }

    @Test
    fun fault의_종류가_셋_밖이면_입력_오류() {
        good().rawAnswer(line("fault" to "\"order:crash\"")).assertInputError()
    }

    @Test
    fun fault의_delay가_0이면_입력_오류() {
        good().rawAnswer(line("fault" to "\"order:delay=0\"")).assertInputError()
    }

    @Test
    fun fault의_delay가_정수가_아니면_입력_오류() {
        good().rawAnswer(line("fault" to "\"order:delay=abc\"")).assertInputError()
    }

    @Test
    fun fault에_같은_서비스의_같은_종류가_두_번이면_입력_오류() {
        good().rawAnswer(line("fault" to "\"order:error,order:error\"")).assertInputError()
    }

    @Test
    fun fault의_delay_1과_여러_지시는_받는다() {
        val r = good().rawAnswer(line("fault" to "\"order:delay=1\""))
            .rawAnswer(line("requestId" to "\"c\"", "fault" to "\"order:delay=5,order:error,payment:fail-after-commit\""))
        assertEquals(0, r.run())
    }

    @Test
    fun fault의_지시_앞뒤_공백은_받는다() {
        assertEquals(0, good().rawAnswer(line("fault" to "\"order:error, payment:delay=5\"")).run())
    }

    @Test
    fun fault가_공백뿐이면_장애_없음으로_받는다() {
        assertEquals(0, good().rawAnswer(line("fault" to "\"   \"")).run())
    }

    // ---- db/*.csv ----

    @Test
    fun 머리줄만_있는_CSV는_행_0개로_받는다() {
        // 2절: 행이 0개여도 머리줄은 있다 — 입력 오류가 아니다
        val rec = RunRecord(root).answer("a", 500, "order:error").analyze()
        assertEquals(listOf(0L, 0L, 0L), rec.perLayer("rows"))
    }

    @Test
    fun 머리줄도_없는_빈_CSV는_입력_오류() {
        good().apply { csvText["inventory"] = "" }.assertInputError()
    }

    @Test
    fun orders_CSV가_없으면_입력_오류() {
        good().apply { omit += "db/orders.csv" }.assertInputError()
    }

    @Test
    fun reservations_CSV가_없으면_입력_오류() {
        good().apply { omit += "db/reservations.csv" }.assertInputError()
    }

    @Test
    fun payments_CSV가_없으면_입력_오류() {
        good().apply { omit += "db/payments.csv" }.assertInputError()
    }

    @Test
    fun CSV_머리줄이_다르면_입력_오류() {
        good().apply { csvText["order"] = "request_id,status,amount\na,COMPLETED,$amountA\n" }.assertInputError()
    }

    @Test
    fun CSV_칸_수가_머리줄과_다르면_입력_오류() {
        good().apply { csvText["payment"] = paymentCsv("a,$amountA") }.assertInputError()
    }

    @Test
    fun CSV의_request_id가_비면_입력_오류() {
        good().apply { csvText["inventory"] = inventoryCsv(",RESERVED") }.assertInputError()
    }

    @Test
    fun CSV의_request_id가_공백뿐이면_입력_오류() {
        good().apply { csvText["inventory"] = inventoryCsv("  ,RESERVED") }.assertInputError()
    }

    @Test
    fun CSV의_status가_비면_입력_오류() {
        good().apply { csvText["order"] = orderCsv("a,$amountA,") }.assertInputError()
    }

    @Test
    fun CSV의_status가_공백뿐이면_입력_오류() {
        good().apply { csvText["order"] = orderCsv("a,$amountA,  ") }.assertInputError()
    }

    @Test
    fun CSV_값에_따옴표가_있으면_입력_오류() {
        good().apply { csvText["order"] = orderCsv("\"a\",$amountA,COMPLETED") }.assertInputError()
    }

    @Test
    fun CSV의_줄끝이_CRLF면_입력_오류() {
        good().apply { csvText["inventory"] = "request_id,status\r\n$inventoryGood\r\n" }.assertInputError()
    }

    // status 낱말 — 층마다 받는 것만(2절)

    @Test
    fun order_CSV의_status가_RESERVED면_입력_오류() {
        good().apply { csvText["order"] = orderCsv("a,$amountA,RESERVED") }.assertInputError()
    }

    @Test
    fun inventory_CSV의_status가_COMPLETED면_입력_오류() {
        good().apply { csvText["inventory"] = inventoryCsv("a,COMPLETED") }.assertInputError()
    }

    @Test
    fun payment_CSV의_status가_PENDING이면_입력_오류() {
        good().apply { csvText["payment"] = paymentCsv("a,$amountA,PENDING") }.assertInputError()
    }

    @Test
    fun CSV의_status는_대소문자를_가린다() {
        good().apply { csvText["order"] = orderCsv("a,$amountA,completed") }.assertInputError()
    }

    @Test
    fun order_CSV는_FAILED와_UNKNOWN을_받는다() {
        val r = good().apply { csvText["order"] = orderCsv(orderGood, "a,$amountA,FAILED", "a,$amountA,UNKNOWN") }
        assertEquals(0, r.run())
    }

    @Test
    fun inventory_CSV는_FAILED와_UNKNOWN을_받는다() {
        val r = good().apply { csvText["inventory"] = inventoryCsv(inventoryGood, "a,FAILED", "a,UNKNOWN") }
        assertEquals(0, r.run())
    }

    @Test
    fun order와_inventory의_PENDING은_입력_오류가_아니라_불일치다() {
        // PENDING 은 받는 낱말이다 — 종료 코드 2(5-3 pendingRows), 1이 아니다
        val r = good().apply {
            csvText["order"] = orderCsv(orderGood, "a,$amountA,PENDING")
            csvText["inventory"] = inventoryCsv(inventoryGood, "a,PENDING")
        }
        assertEquals(2, r.run())
    }

    // amount 칸(order, payment)

    @Test
    fun order_CSV의_amount가_비면_입력_오류() {
        good().apply { csvText["order"] = orderCsv("a,,COMPLETED") }.assertInputError()
    }

    @Test
    fun order_CSV의_amount가_소수점_있는_정수면_입력_오류() {
        good().apply { csvText["order"] = orderCsv("a,$amountA.0,COMPLETED") }.assertInputError()
    }

    @Test
    fun payment_CSV의_amount가_숫자가_아니면_입력_오류() {
        good().apply { csvText["payment"] = paymentCsv("a,abc,APPROVED") }.assertInputError()
    }

    @Test
    fun CSV의_amount가_64비트를_넘으면_입력_오류() {
        good().apply { csvText["order"] = orderCsv(orderGood, "a,9223372036854775808,FAILED") }.assertInputError()
    }

    @Test
    fun CSV의_amount는_음수와_64비트_최대값을_받는다() {
        // 처리 행이 아닌 행이라 금액 불일치에 들지 않는다 — 형만 본다
        val r = good().apply {
            csvText["order"] = orderCsv(orderGood, "a,-5,FAILED", "a,9223372036854775807,UNKNOWN")
        }
        assertEquals(0, r.run())
    }

    // ---- fault-counts.json ----

    @Test
    fun 장애_수_파일이_없으면_입력_오류() {
        good().apply { omit += "fault-counts.json" }.assertInputError()
    }

    @Test
    fun 장애_수에_서비스가_빠지면_입력_오류() {
        good().apply { faultCountsText = """{"order":$faultZero,"inventory":$faultZero}""" }.assertInputError()
    }

    @Test
    fun 장애_수에_종류가_빠지면_입력_오류() {
        good().apply { faultCountsText = """{"order":$faultZero,"inventory":$faultZero,"payment":{"delay":0,"error":0}}""" }
            .assertInputError()
    }

    @Test
    fun 장애_수에_서비스_셋_밖의_키가_있으면_입력_오류() {
        good().apply {
            faultCountsText = """{"order":$faultZero,"inventory":$faultZero,"payment":$faultZero,"shipping":$faultZero}"""
        }.assertInputError()
    }

    @Test
    fun 장애_수에_종류_셋_밖의_키가_있으면_입력_오류() {
        good().apply {
            faultCountsText =
                """{"order":$faultZero,"inventory":$faultZero,"payment":{"delay":0,"error":0,"fail-after-commit":0,"timeout":0}}"""
        }.assertInputError()
    }

    @Test
    fun 장애_수가_음수면_입력_오류() {
        good().apply {
            faultCountsText = """{"order":$faultZero,"inventory":$faultZero,"payment":{"delay":-1,"error":0,"fail-after-commit":0}}"""
        }.assertInputError()
    }

    @Test
    fun 장애_수가_문자열이면_입력_오류() {
        good().apply {
            faultCountsText = """{"order":$faultZero,"inventory":$faultZero,"payment":{"delay":"0","error":0,"fail-after-commit":0}}"""
        }.assertInputError()
    }

    @Test
    fun 장애_수가_소수점_있는_정수면_입력_오류() {
        good().apply {
            faultCountsText = """{"order":$faultZero,"inventory":$faultZero,"payment":{"delay":0.0,"error":0,"fail-after-commit":0}}"""
        }.assertInputError()
    }

    @Test
    fun 장애_수가_JSON이_아니면_입력_오류() {
        good().apply { faultCountsText = "order delay 0" }.assertInputError()
    }

    // ---- 입력 오류 때 옛 보고를 지운다(5절 종료 코드 1) ----

    private fun withOldReport(r: RunRecord): RunRecord {
        r.reportJson.toFile().writeText("""{"run":"old","differences":[]}""")
        r.reportMd.toFile().writeText("# 옛 보고\n")
        return r
    }

    @Test
    fun 정답_입력_오류면_이미_있던_보고_파일을_지운다() {
        // assertInputError 가 report.json·report.md 가 없음을 단정한다
        withOldReport(good().rawAnswer(line("amount" to null))).assertInputError()
    }

    @Test
    fun CSV_입력_오류면_이미_있던_보고_파일을_지운다() {
        withOldReport(good().apply { csvText["payment"] = paymentCsv("a,abc,APPROVED") }).assertInputError()
    }

    @Test
    fun 인자_입력_오류면_이미_있던_보고_파일을_지운다() {
        withOldReport(good()).assertInputError("--planned", "0")
    }

    @Test
    fun 기대_파일_입력_오류면_이미_있던_보고_파일을_지운다() {
        val e = writeExpect(root, """{"answers":"1"}""")
        withOldReport(good()).assertInputError("--expect", e.toString())
    }
}
