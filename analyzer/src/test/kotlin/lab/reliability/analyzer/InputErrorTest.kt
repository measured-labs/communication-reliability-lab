package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 1~3절과 5절의 종료 코드 1 — 틀린 입력을 조용히 통과시키지 않는다(종료 코드 1, 보고를 쓰지 않음).
// 각 오류 테스트 옆에 경계 안쪽(받아야 하는 값) 테스트를 둔다.
class InputErrorTest {
    @TempDir
    lateinit var root: Path

    private fun good() = RunRecord(root).completedChain("a")

    private fun line(
        type: String = "\"answer\"",
        requestId: String = "\"b\"",
        fault: String = "\"\"",
        status: String = "500",
        errorCode: String = "0",
        elapsedMs: String = "10",
    ) = """{"type":$type,"requestId":$requestId,"fault":$fault,"status":$status,"errorCode":$errorCode,"elapsedMs":$elapsedMs}"""

    // ---- answers.jsonl ----

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
        val r = good()
        r.answersText = line(requestId = "\"a\"", status = "201") + "\n\n" + line() + "\n"
        r.assertInputError()
    }

    @Test
    fun 정답에_깨진_JSON_줄이_있으면_입력_오류() {
        good().rawAnswer("""{"type":"answer","requestId":"b",""").assertInputError()
    }

    @Test
    fun 정답_줄에_필수_필드가_없으면_입력_오류() {
        good().rawAnswer("""{"type":"answer","requestId":"b","fault":"","status":500,"elapsedMs":10}""").assertInputError()
    }

    @Test
    fun 정답의_status가_문자열이면_입력_오류() {
        good().rawAnswer(line(status = "\"201\"")).assertInputError()
    }

    @Test
    fun 정답의_elapsedMs가_문자열이면_입력_오류() {
        good().rawAnswer(line(elapsedMs = "\"10\"")).assertInputError()
    }

    @Test
    fun 정답의_errorCode가_문자열이면_입력_오류() {
        good().rawAnswer(line(errorCode = "\"0\"")).assertInputError()
    }

    @Test
    fun 정답의_requestId가_수면_입력_오류() {
        good().rawAnswer(line(requestId = "7")).assertInputError()
    }

    @Test
    fun 정답의_type이_answer가_아니면_입력_오류() {
        good().rawAnswer(line(type = "\"log\"")).assertInputError()
    }

    @Test
    fun 정답의_status가_99면_입력_오류() {
        good().rawAnswer(line(status = "99")).assertInputError()
    }

    @Test
    fun 정답의_status가_600이면_입력_오류() {
        good().rawAnswer(line(status = "600")).assertInputError()
    }

    @Test
    fun 정답의_status_0과_100과_599는_받는다() {
        val r = good().rawAnswer(line(requestId = "\"b\"", status = "0"))
            .rawAnswer(line(requestId = "\"c\"", status = "100")).rawAnswer(line(requestId = "\"d\"", status = "599"))
        assertEquals(0, r.run())
    }

    @Test
    fun 정답의_elapsedMs가_음수면_입력_오류() {
        good().rawAnswer(line(elapsedMs = "-1")).assertInputError()
    }

    @Test
    fun 정답의_elapsedMs_0은_받는다() {
        assertEquals(0, good().rawAnswer(line(elapsedMs = "0")).run())
    }

    @Test
    fun 정답의_모르는_필드는_무시한다() {
        val l = """{"type":"answer","requestId":"b","fault":"","status":500,"errorCode":0,"elapsedMs":10,"iteration":3,"sentAt":"x","extra":true}"""
        assertEquals(0, good().rawAnswer(l).run())
    }

    // fault 문법(0절)

    @Test
    fun fault의_서비스가_셋_밖이면_입력_오류() {
        good().rawAnswer(line(fault = "\"shipping:error\"")).assertInputError()
    }

    @Test
    fun fault의_종류가_셋_밖이면_입력_오류() {
        good().rawAnswer(line(fault = "\"order:crash\"")).assertInputError()
    }

    @Test
    fun fault의_delay가_0이면_입력_오류() {
        good().rawAnswer(line(fault = "\"order:delay=0\"")).assertInputError()
    }

    @Test
    fun fault의_delay가_정수가_아니면_입력_오류() {
        good().rawAnswer(line(fault = "\"order:delay=abc\"")).assertInputError()
    }

    @Test
    fun fault에_같은_서비스의_같은_종류가_두_번이면_입력_오류() {
        good().rawAnswer(line(fault = "\"order:error,order:error\"")).assertInputError()
    }

    @Test
    fun fault의_delay_1과_여러_지시는_받는다() {
        val r = good().rawAnswer(line(fault = "\"order:delay=1\""))
            .rawAnswer(line(requestId = "\"c\"", fault = "\"order:delay=5,order:error,payment:fail-after-commit\""))
        assertEquals(0, r.run())
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
        good().apply { csvText["order"] = "request_id,status,amount\na,COMPLETED,1000\n" }.assertInputError()
    }

    @Test
    fun CSV_칸_수가_머리줄과_다르면_입력_오류() {
        good().apply { csvText["payment"] = "request_id,amount,status\na,1000\n" }.assertInputError()
    }

    @Test
    fun CSV의_request_id가_비면_입력_오류() {
        good().apply { csvText["inventory"] = "request_id,status\n,RESERVED\n" }.assertInputError()
    }

    @Test
    fun CSV의_status가_비면_입력_오류() {
        good().apply { csvText["order"] = "request_id,amount,status\na,1000,\n" }.assertInputError()
    }

    @Test
    fun CSV_값에_따옴표가_있으면_입력_오류() {
        good().apply { csvText["order"] = "request_id,amount,status\n\"a\",1000,COMPLETED\n" }.assertInputError()
    }

    // ---- fault-counts.json ----

    @Test
    fun 장애_수_파일이_없으면_입력_오류() {
        good().apply { omit += "fault-counts.json" }.assertInputError()
    }

    @Test
    fun 장애_수에_서비스가_빠지면_입력_오류() {
        val z = """{"delay":0,"error":0,"fail-after-commit":0}"""
        good().apply { faultCountsText = """{"order":$z,"inventory":$z}""" }.assertInputError()
    }

    @Test
    fun 장애_수에_종류가_빠지면_입력_오류() {
        val z = """{"delay":0,"error":0,"fail-after-commit":0}"""
        good().apply { faultCountsText = """{"order":$z,"inventory":$z,"payment":{"delay":0,"error":0}}""" }.assertInputError()
    }

    @Test
    fun 장애_수가_음수면_입력_오류() {
        val z = """{"delay":0,"error":0,"fail-after-commit":0}"""
        good().apply { faultCountsText = """{"order":$z,"inventory":$z,"payment":{"delay":-1,"error":0,"fail-after-commit":0}}""" }
            .assertInputError()
    }

    @Test
    fun 장애_수가_정수가_아니면_입력_오류() {
        val z = """{"delay":0,"error":0,"fail-after-commit":0}"""
        good().apply { faultCountsText = """{"order":$z,"inventory":$z,"payment":{"delay":"0","error":0,"fail-after-commit":0}}""" }
            .assertInputError()
    }

    @Test
    fun 장애_수가_JSON이_아니면_입력_오류() {
        good().apply { faultCountsText = "order delay 0" }.assertInputError()
    }
}
