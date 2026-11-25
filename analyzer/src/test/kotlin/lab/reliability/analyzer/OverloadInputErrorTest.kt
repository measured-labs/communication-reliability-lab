package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// docs/measurement.md 10-4 입력 오류(종료 코드 1, report-overload.json·metrics-overload.tsv 를 쓰지 않고 있던 것은 지운다)와 10-2 입력.
// 틀린 이유로 통과하지 않게: 오류 테스트마다 기준 기록(goodOverloadRecord — 종료 0, 기준_기록은_통과한다)에서 칸 하나만 바꾼다.
// 받아야 하는 값(경계의 안쪽)은 …_받는다 테스트가 지킨다.
class OverloadInputErrorTest {
    @TempDir
    lateinit var root: Path

    private fun good() = goodOverloadRecord(root)

    /** 기준 기록의 정답 줄들을 [f]로 바꿔 answersText 로 쓴다. */
    private fun OverloadRecord.answersEdited(f: (List<String>) -> List<String>): OverloadRecord {
        write()
        val lines = dir.resolve("answers.jsonl").toFile().readLines()
        answersText = f(lines).joinToString("\n", postfix = "\n")
        return this
    }

    /** 정답 [id]의 줄에서 [from]을 [to]로. */
    private fun OverloadRecord.answerReplace(id: String, from: String, to: String): OverloadRecord = answersEdited { lines ->
        lines.map { if ("\"requestId\":\"$id\"" in it) it.replace(from, to).also { n -> check(n != it) { "바꿀 것이 없다: $from" } } else it }
    }

    private fun payment(json: String) = """{"service":"payment","overload":$json}"""

    // ---- 기준과 보고 지우기 ----

    @Test
    fun 기준_기록은_통과한다() {
        val r = good()
        assertEquals(0, r.runOverload(), r.lastOutput)
    }

    @Test
    fun 입력_오류면_있던_보고를_지운다() {
        val r = good()
        r.dir.resolve("report-overload.json").writeText("{}")
        r.dir.resolve("metrics-overload.tsv").writeText("old\t1\n")
        r.omit += "overload-counts.json"
        r.assertInputError()
    }

    // ---- 인자 ----

    @Test
    fun run이_없으면_입력_오류() {
        val (code, out) = analyzerCli("overload")
        assertEquals(1, code, out)
    }

    @Test
    fun 모르는_인자는_입력_오류() {
        good().assertInputError("--planned", "14")
    }

    @Test
    fun expect_파일이_없으면_입력_오류() {
        good().assertInputError("--expect", root.resolve("없는.json").toString())
    }

    @Test
    fun expect의_min이_max보다_크면_입력_오류() {
        val e = writeExpect(root, """{"payment.rejectedReplayed":{"min":6,"max":5}}""", "expect.overload.json")
        good().assertInputError("--expect", e.toString())
    }

    // ---- 파일 없음 ----

    @Test
    fun 정답_파일이_없으면_입력_오류() {
        good().apply { omit += "answers.jsonl" }.assertInputError()
    }

    @Test
    fun 결제의_시각_기록이_없으면_입력_오류() {
        good().apply { omit += "timing/timings-payment.json" }.assertInputError()
    }

    @Test
    fun 재고의_시각_기록이_없으면_입력_오류() {
        good().apply { omit += "timing/timings-inventory.json" }.assertInputError()
    }

    @Test
    fun 결제의_설정_파일이_없으면_입력_오류() {
        good().apply { omit += "settings-payment.json" }.assertInputError()
    }

    @Test
    fun 주문의_설정_파일이_없으면_입력_오류() {
        good().apply { omit += "settings-order.json" }.assertInputError()
    }

    @Test
    fun 센_수_파일이_없으면_입력_오류() {
        good().apply { omit += "overload-counts.json" }.assertInputError()
    }

    // ---- 정답: phase ----

    @Test
    fun phase가_없으면_입력_오류() {
        good().answerReplace("a3", ""","phase":"base"""", "").assertInputError()
    }

    @Test
    fun phase가_null이면_입력_오류() {
        good().answerReplace("a3", """"phase":"base"""", """"phase":null""").assertInputError()
    }

    @Test
    fun phase가_모르는_값이면_입력_오류() {
        good().answerReplace("a3", """"phase":"base"""", """"phase":"Base"""").assertInputError()
    }

    @Test
    fun phase가_문자열이_아니면_입력_오류() {
        good().answerReplace("a3", """"phase":"base"""", """"phase":1""").assertInputError()
    }

    @Test
    fun spike_정답이_없으면_입력_오류() {
        good().answersEdited { lines -> lines.map { it.replace(""""phase":"spike"""", """"phase":"base"""") } }.assertInputError()
    }

    @Test
    fun spike_정답이_하나여도_받는다() {
        // s2~s6 을 base 로 — 구간은 300~300. 구간 동안의 거절이 0 이라 ⑦(종료 2)이어도 입력 오류는 아니다
        val r = good().answersEdited { lines ->
            lines.map { if ("\"requestId\":\"s1\"" in it) it else it.replace(""""phase":"spike"""", """"phase":"base"""") }
        }
        val code = r.runOverload()
        assertTrue(code == 0 || code == 2, "입력 오류가 아니다: $code ${r.lastOutput}")
        assertEquals(300L, r.report().long("spike", "endMs"))
    }

    @Test
    fun 같은_requestId가_두_줄이면_입력_오류() {
        good().answersEdited { lines -> lines + lines.first { "\"requestId\":\"a5\"" in it }.replace("\"sentAtMs\":${T + 840}", "\"sentAtMs\":${T + 900}") }
            .assertInputError()
    }

    @Test
    fun sentAtMs가_없으면_입력_오류() {
        good().answerReplace("a3", ""","sentAtMs":${T + 600}""", "").assertInputError()
    }

    @Test
    fun 정답에_CR이_있으면_입력_오류() {
        good().answersEdited { lines -> lines.mapIndexed { i, l -> if (i == 0) "$l\r" else l } }.assertInputError()
    }

    @Test
    fun attemptFault가_없어도_이_분석은_읽지_않으므로_받는다() {
        // 10-2: attemptFault 는 8절의 재시도 분석이 읽는다 — 이 분석은 읽지 않는다
        val r = good().answersEdited { lines -> lines.map { it.replace(""","attemptFault":""""", "") } }
        assertEquals(0, r.runOverload(), r.lastOutput)
    }

    // ---- 시각 기록 ----

    @Test
    fun 정답에_없는_requestId의_결제_기록은_입력_오류() {
        good().paymentTiming("zz", Pay(payAt(T + 900), 201)).assertInputError()
    }

    @Test
    fun 정답에_없는_requestId의_재고_기록은_입력_오류() {
        good().rawTiming(
            "inventory",
            """{"requestId":"zz","startUs":${us(T + 900)},"endUs":${us(T + 901)},"status":201,"callStartUs":${us(T + 900) + 100},"callEndUs":${us(T + 900) + 900},"callOutcome":"Completed"}""",
        ).assertInputError()
    }

    @Test
    fun 결제_기록에_status가_없으면_입력_오류() {
        good().rawTiming("payment", """{"requestId":"a5","startUs":${us(T + 900)},"endUs":${us(T + 901)},"callStartUs":null,"callEndUs":null,"callOutcome":null}""")
            .assertInputError()
    }

    @Test
    fun 결제_기록에_call_칸이_있으면_입력_오류() {
        good().rawTiming(
            "payment",
            """{"requestId":"a5","startUs":${us(T + 900)},"endUs":${us(T + 901)},"status":201,"callStartUs":${us(T + 900) + 100},"callEndUs":${us(T + 900) + 200},"callOutcome":"Completed"}""",
        ).assertInputError()
    }

    @Test
    fun 결제_기록의_endUs가_startUs보다_이르면_입력_오류() {
        good().rawTiming("payment", """{"requestId":"a5","startUs":${us(T + 900)},"endUs":${us(T + 899)},"status":201,"callStartUs":null,"callEndUs":null,"callOutcome":null}""")
            .assertInputError()
    }

    @Test
    fun 결제_기록이_한_requestId에_여럿이어도_받는다() {
        // 재시도 — a5 가 840.6 에 받아진 뒤 900.6 에 한 번 더 들어 받는다(센 든 수 15)
        val r = good().paymentTiming("a5", Pay(payAt(T + 900), 201))
        assertEquals(0, r.runOverload(), r.lastOutput)
        assertEquals(15L, r.report().long("payment", "arrivals"))
    }

    // ---- 설정 ----

    @Test
    fun 결제의_한도가_꺼져_있으면_입력_오류() {
        good().apply { settingsText["payment"] = payment("""{"enabled":false}""") }.assertInputError()
    }

    @Test
    fun 결제의_overload_칸이_없으면_입력_오류() {
        good().apply { settingsText["payment"] = """{"service":"payment"}""" }.assertInputError()
    }

    @Test
    fun 결제의_enabled가_불이_아니면_입력_오류() {
        good().apply { settingsText["payment"] = payment("""{"enabled":"true","limit":3,"windowMs":100}""") }.assertInputError()
    }

    @Test
    fun 결제의_limit이_없으면_입력_오류() {
        good().apply { settingsText["payment"] = payment("""{"enabled":true,"windowMs":100}""") }.assertInputError()
    }

    @Test
    fun 결제의_windowMs가_없으면_입력_오류() {
        good().apply { settingsText["payment"] = payment("""{"enabled":true,"limit":3}""") }.assertInputError()
    }

    @Test
    fun 결제의_limit이_0이면_입력_오류() {
        good().apply { limit = 0 }.assertInputError()
    }

    @Test
    fun 결제의_windowMs가_0이면_입력_오류() {
        good().apply { windowMs = 0 }.assertInputError()
    }

    @Test
    fun 결제의_limit이_소수면_입력_오류() {
        good().apply { limit = "3.0" }.assertInputError()
    }

    @Test
    fun 결제의_limit이_문자열이면_입력_오류() {
        good().apply { limit = "\"3\"" }.assertInputError()
    }

    @Test
    fun 결제의_limit_1_windowMs_1은_받는다() {
        // 기준 기록의 503 은 이 한도로는 맞지 않는다 — 불일치(2)여도 입력 오류(1)가 아니면 된다
        val r = good().apply { limit = 1; windowMs = 1 }
        val code = r.runOverload()
        assertTrue(code == 0 || code == 2, "입력 오류가 아니다: $code ${r.lastOutput}")
        assertEquals(1L, r.report().long("limit", "limit"))
        assertEquals(1L, r.report().long("limit", "windowMs"))
    }

    @Test
    fun 설정의_다른_칸은_읽지_않는다() {
        val r = good()
        r.settingsText["payment"] = payment("""{"enabled":true,"limit":3,"windowMs":100}""")
        r.settingsText["order"] = """{"overload":{"enabled":false},"nextLayer":"아무거나","x":[1]}"""
        assertEquals(0, r.runOverload(), r.lastOutput)
    }

    @Test
    fun 주문의_한도가_켜져_있으면_입력_오류() {
        good().apply { settingsText["order"] = """{"service":"order","overload":{"enabled":true,"limit":50,"windowMs":1000}}""" }
            .assertInputError()
    }

    @Test
    fun 재고의_한도가_켜져_있으면_입력_오류() {
        good().apply { settingsText["inventory"] = """{"service":"inventory","overload":{"enabled":true,"limit":50,"windowMs":1000}}""" }
            .assertInputError()
    }

    @Test
    fun 재고의_enabled가_불이_아니면_입력_오류() {
        good().apply { settingsText["inventory"] = """{"service":"inventory","overload":{"enabled":"false"}}""" }.assertInputError()
    }

    @Test
    fun 주문의_overload_칸이_없으면_입력_오류() {
        good().apply { settingsText["order"] = """{"service":"order"}""" }.assertInputError()
    }

    // ---- overload-counts.json ----

    private fun countsWith(payment: String, order: String = """{"arrivals":0,"rejected":0}""", inventory: String = """{"arrivals":0,"rejected":0}""") =
        """{"order":$order,"inventory":$inventory,"payment":$payment}"""

    @Test
    fun 센_수를_손으로_써도_맞으면_받는다() {
        val r = good().apply { countsText = countsWith("""{"arrivals":14,"rejected":5}""") }
        assertEquals(0, r.runOverload(), r.lastOutput)
    }

    @Test
    fun 센_수에_서비스가_없으면_입력_오류() {
        good().apply { countsText = """{"order":{"arrivals":0,"rejected":0},"payment":{"arrivals":14,"rejected":5}}""" }.assertInputError()
    }

    @Test
    fun 센_수에_칸이_없으면_입력_오류() {
        good().apply { countsText = countsWith("""{"arrivals":14}""") }.assertInputError()
    }

    @Test
    fun 센_수에_여분의_서비스_키가_있으면_입력_오류() {
        good().apply { countsText = countsWith("""{"arrivals":14,"rejected":5}""").dropLast(1) + ""","gateway":{"arrivals":0,"rejected":0}}""" }
            .assertInputError()
    }

    @Test
    fun 센_수에_여분의_칸이_있으면_입력_오류() {
        good().apply { countsText = countsWith("""{"arrivals":14,"rejected":5,"dropped":0}""") }.assertInputError()
    }

    @Test
    fun 센_수가_음수면_입력_오류() {
        good().apply { countsText = countsWith("""{"arrivals":14,"rejected":5}""", order = """{"arrivals":-1,"rejected":0}""") }
            .assertInputError()
    }

    @Test
    fun 센_수가_소수면_입력_오류() {
        good().apply { countsText = countsWith("""{"arrivals":14.0,"rejected":5}""") }.assertInputError()
    }

    @Test
    fun 센_수가_문자열이면_입력_오류() {
        good().apply { countsText = countsWith("""{"arrivals":14,"rejected":"5"}""") }.assertInputError()
    }
}
