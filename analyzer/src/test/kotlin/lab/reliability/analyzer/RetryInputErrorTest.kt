package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals

// docs/measurement.md 8-3 — 재시도 분석의 입력 오류(종료 코드 1, report-retry.json·metrics-retry.tsv 를 쓰지 않고 있던 것은 지운다)
// 와 8-1의 X-Attempt-Fault 문법(분석기는 틀린 헤더를 입력 오류로 낸다).
// 틀린 이유로 통과하지 않게: 오류 테스트마다 기준 기록(goodRetryRecord — 종료 0)에서 한 가지만 바꾼다.
// 기준 기록이 통과하는 것과 경계의 안쪽(받아야 하는 값)이 통과하는 것은 기준_… 과 …_받는다 테스트가 지킨다.
class RetryInputErrorTest {
    @TempDir
    lateinit var root: Path

    private fun good() = goodRetryRecord(root)

    /** 기준 기록에 f(장애 지시 [af], 정상 처리)를 더한다 — 지시가 가리키는 서비스·종류의 수는 테스트가 맞춘다. */
    private fun withFault(af: String) = good().okChain("f", T + 500, af)

    /** 시도 기록 하나를 바꾼다(재고). */
    private fun RetryRecord.changeAttempt(id: String, attempt: Int, f: (Attempt) -> Attempt): RetryRecord {
        val list = attempts.getValue("inventory")
        val i = list.indexOfFirst { it.requestId == id && it.attempt == attempt }
        check(i >= 0) { "시도 $id/$attempt 가 없다" }
        list[i] = f(list[i])
        return this
    }

    /** 시도 기록의 칸을 JSON 으로 바꿔 쓴다(값이 null 이면 그 칸을 뺀다) — 꼴 오류 시험용. */
    private fun RetryRecord.rawAttempt(id: String, attempt: Int, vararg change: Pair<String, String?>): RetryRecord {
        val list = attempts.getValue("inventory")
        val items = list.map { a ->
            if (a.requestId != id || a.attempt != attempt) a.json() else {
                val f = linkedMapOf<String, String?>(
                    "requestId" to "\"${a.requestId}\"", "callId" to "${a.callId}", "attempt" to "${a.attempt}",
                    "startUs" to "${a.startUs}", "endUs" to "${a.endUs}", "outcome" to "\"${a.outcome}\"",
                    "delayBeforeMs" to "${a.delayBeforeMs}", "decision" to "\"${a.decision}\"",
                )
                change.forEach { (k, v) -> f[k] = v }
                f.filterValues { it != null }.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
            }
        }
        attemptsText["inventory"] = items.joinToString(",", "[", "]")
        return this
    }

    // ---- 기준 ----

    @Test
    fun 기준_기록은_통과한다() {
        val r = good()
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    @Test
    fun 기준_시도_기록을_칸으로_다시_써도_통과한다() {
        val r = good().rawAttempt("c", 2)
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    @Test
    fun 입력_오류면_있던_보고를_지운다() {
        val r = good()
        r.dir.resolve("report-retry.json").writeText("{}")
        r.dir.resolve("metrics-retry.tsv").writeText("old\t1\n")
        r.omit += "attempt-faults.json"
        r.assertInputError()
    }

    // ---- 인자 ----

    @Test
    fun run이_없으면_입력_오류() {
        val (code, out) = analyzerCli("retry")
        assertEquals(1, code, out)
    }

    @Test
    fun 모르는_인자는_입력_오류() {
        good().assertInputError("--planned", "5")
    }

    @Test
    fun 기대_파일이_없으면_입력_오류() {
        good().assertInputError("--expect", root.resolve("no-such.json").toString())
    }

    @Test
    fun 기대의_min이_max보다_크면_입력_오류() {
        good().assertInputError("--expect", writeExpect(root, """{"answers": {"min": 2, "max": 1}}""").toString())
    }

    // ---- 정답 ----

    @Test
    fun attemptFault가_없으면_입력_오류() {
        good().rawAnswer("""{"type":"answer","requestId":"z","fault":"","amount":1500,"sentAtMs":${T + 500},"status":502,"errorCode":0,"elapsedMs":10}""")
            .assertInputError()
    }

    @Test
    fun attemptFault가_문자열이_아니면_입력_오류() {
        good().rawAnswer("""{"type":"answer","requestId":"z","fault":"","amount":1500,"sentAtMs":${T + 500},"status":502,"errorCode":0,"elapsedMs":10,"attemptFault":1}""")
            .assertInputError()
    }

    @Test
    fun attemptFault가_null이면_입력_오류() {
        good().rawAnswer("""{"type":"answer","requestId":"z","fault":"","amount":1500,"sentAtMs":${T + 500},"status":502,"errorCode":0,"elapsedMs":10,"attemptFault":null}""")
            .assertInputError()
    }

    @Test
    fun sentAtMs가_없으면_입력_오류() {
        good().rawAnswer("""{"type":"answer","requestId":"z","fault":"","amount":1500,"status":502,"errorCode":0,"elapsedMs":10,"attemptFault":""}""")
            .assertInputError()
    }

    @Test
    fun 같은_requestId가_정답에_두_줄이면_입력_오류() {
        good().answer("a", T + 600, 201).assertInputError()
    }

    // ---- 8-1 문법 ----

    @Test
    fun 두_서비스를_가리키는_지시는_받는다() {
        val r = withFault("inventory:fail-first=1,payment:fail-first=1")
            .counted("inventory", "fail-first", 1).counted("payment", "fail-first", 5)
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    @Test
    fun slow_first_n_at_ms는_받는다() {
        val r = withFault("payment:slow-first=2@300").counted("payment", "slow-first", 2)
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    @Test
    fun 모르는_서비스는_입력_오류() {
        withFault("shipping:fail-first=1").assertInputError()
    }

    @Test
    fun 모르는_종류는_입력_오류() {
        withFault("payment:crash=1").assertInputError()
    }

    @Test
    fun fail_first가_0이면_입력_오류() {
        withFault("payment:fail-first=0").assertInputError()
    }

    @Test
    fun fail_first에_부호가_있으면_입력_오류() {
        withFault("payment:fail-first=+1").assertInputError()
    }

    @Test
    fun fail_first가_음수면_입력_오류() {
        withFault("payment:fail-first=-1").assertInputError()
    }

    @Test
    fun fail_first가_소수면_입력_오류() {
        withFault("payment:fail-first=1.5").assertInputError()
    }

    @Test
    fun fail_first에_값이_없으면_입력_오류() {
        withFault("payment:fail-first=").assertInputError()
    }

    @Test
    fun 한_서비스에_지시가_둘이면_입력_오류() {
        withFault("payment:fail-first=1,payment:slow-first=1@100").assertInputError()
    }

    @Test
    fun slow_first에_ms가_없으면_입력_오류() {
        withFault("payment:slow-first=1").assertInputError()
    }

    @Test
    fun slow_first의_ms가_0이면_입력_오류() {
        withFault("payment:slow-first=1@0").assertInputError()
    }

    @Test
    fun slow_first의_n이_0이면_입력_오류() {
        withFault("payment:slow-first=0@100").assertInputError()
    }

    @Test
    fun outage의_from이_to와_같으면_입력_오류() {
        withFault("payment:outage=${T + 100}-${T + 100}").assertInputError()
    }

    @Test
    fun outage의_from이_to보다_크면_입력_오류() {
        withFault("payment:outage=${T + 200}-${T + 100}").assertInputError()
    }

    @Test
    fun outage의_from이_0이면_받는다() {
        // from = 0 은 받는 값(0 이상의 정수). 구간 [0, 1)ms 에 든 기록은 없다
        val r = withFault("payment:outage=0-1")
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    @Test
    fun outage에_끝이_없으면_입력_오류() {
        withFault("payment:outage=${T + 100}").assertInputError()
    }

    @Test
    fun 구간_장애의_지시가_정답들_사이에_둘이면_입력_오류() {
        good().okChain("f", T + 500, "payment:outage=0-1").okChain("g", T + 600, "payment:outage=0-2")
            .assertInputError()
    }

    @Test
    fun 같은_구간_장애의_지시가_여러_정답에_있으면_받는다() {
        val r = good().okChain("f", T + 500, "payment:outage=0-1").okChain("g", T + 600, "payment:outage=0-1")
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    // ---- 시각 기록 ----

    @Test
    fun 한_층에_한_requestId의_시각_기록이_여럿이어도_받는다() {
        // 기준 기록의 결제에는 b·c·d 의 기록이 여럿이다 — 기준이 통과하는 것으로 지킨다. 재고에도 하나 더
        val r = good().timing("inventory", "a", us(T + 700), us(T + 710), 201, us(T + 701), us(T + 709), "Completed")
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    @Test
    fun DB_행은_있는데_시각_기록이_없어도_받는다() {
        // n: 502 를 받았고 주문 행(FAILED)은 있는데 어느 층에도 시각 기록이 없다
        val r = good().answer("n", T + 500, 502, elapsedMs = 30).row("order", "n", "FAILED")
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    @Test
    fun 정답에_없는_requestId의_시각_기록은_입력_오류() {
        good().timing("payment", "zz", us(T + 700), us(T + 710), 201).assertInputError()
    }

    @Test
    fun 시각_기록의_끝이_시작보다_이르면_입력_오류() {
        good().timing("payment", "a", us(T + 710), us(T + 700), 201).assertInputError()
    }

    // ---- 시도 기록: 파일과 꼴 ----

    @Test
    fun 시도_기록_파일이_없으면_입력_오류() {
        good().apply { omit += "timing/attempts-inventory.json" }.assertInputError()
    }

    @Test
    fun 시도_기록이_배열이_아니면_입력_오류() {
        good().apply { attemptsText["inventory"] = "{}" }.assertInputError()
    }

    @Test
    fun 시도_기록이_JSON이_아니면_입력_오류() {
        good().apply { attemptsText["inventory"] = "[{" }.assertInputError()
    }

    @Test
    fun 시도_기록의_칸이_없으면_입력_오류() {
        good().rawAttempt("c", 2, "decision" to null).assertInputError()
    }

    @Test
    fun 시도_기록의_requestId가_비면_입력_오류() {
        good().rawAttempt("c", 2, "requestId" to "\"\"").assertInputError()
    }

    @Test
    fun 시도_기록의_callId가_정수가_아니면_입력_오류() {
        good().rawAttempt("c", 2, "callId" to "\"3\"").assertInputError()
    }

    @Test
    fun 시도_번호가_0이면_입력_오류() {
        good().rawAttempt("a", 1, "attempt" to "0").assertInputError()
    }

    @Test
    fun 시도의_startUs가_음수면_입력_오류() {
        good().rawAttempt("a", 1, "startUs" to "-1").assertInputError()
    }

    @Test
    fun 시도의_endUs가_startUs보다_이르면_입력_오류() {
        val a = good().attempts.getValue("inventory").first { it.requestId == "a" }
        good().rawAttempt("a", 1, "endUs" to "${a.startUs - 1}").assertInputError()
    }

    @Test
    fun 시도의_startUs와_endUs가_같으면_받는다() {
        val a = good().attempts.getValue("inventory").first { it.requestId == "a" }
        val r = good().rawAttempt("a", 1, "endUs" to "${a.startUs}")
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    @Test
    fun 시도의_결과_꼴이_틀리면_입력_오류() {
        good().rawAttempt("c", 2, "outcome" to "\"Failed: HTTP_500\"").assertInputError()
    }

    @Test
    fun 시도의_결과가_모르는_낱말이면_입력_오류() {
        good().rawAttempt("c", 2, "outcome" to "\"Retrying:HTTP_500\"").assertInputError()
    }

    @Test
    fun 시도의_간격이_음수면_입력_오류() {
        good().rawAttempt("c", 2, "delayBeforeMs" to "-1").assertInputError()
    }

    @Test
    fun 첫_시도의_간격이_0이_아니면_입력_오류() {
        good().rawAttempt("c", 1, "delayBeforeMs" to "5").assertInputError()
    }

    @Test
    fun 시도의_결정이_모르는_낱말이면_입력_오류() {
        good().rawAttempt("c", 3, "decision" to "\"give-up\"").assertInputError()
    }

    // ---- 시도 기록: 정답·결정·차례 ----

    @Test
    fun 시도_기록의_requestId가_정답에_없으면_입력_오류() {
        val r = good()
        r.attempt("inventory", Attempt("zz", r.nextCallId("inventory"), 1, us(T + 700), us(T + 710), "Completed", 0, "success"))
            .assertInputError()
    }

    @Test
    fun Completed인데_결정이_success가_아니면_입력_오류() {
        good().changeAttempt("a", 1) { it.copy(decision = "max-attempts") }.assertInputError()
    }

    @Test
    fun 결정이_success인데_Completed가_아니면_입력_오류() {
        good().changeAttempt("e", 1) { it.copy(decision = "success") }.assertInputError()
    }

    @Test
    fun 한_callId에_requestId가_둘이면_입력_오류() {
        // b 의 호출에서 2번째 시도만 requestId 를 a 로
        good().changeAttempt("b", 2) { it.copy(requestId = "a") }.assertInputError()
    }

    @Test
    fun 시도_번호가_빈틈없이_이어지지_않으면_입력_오류() {
        // c 의 시도 2 를 뺀다(1, 3)
        good().apply { attempts.getValue("inventory").removeIf { it.requestId == "c" && it.attempt == 2 } }
            .assertInputError()
    }

    @Test
    fun 시도_번호가_1부터가_아니면_입력_오류() {
        // b 의 시도 1 을 뺀다(2 하나)
        good().apply { attempts.getValue("inventory").removeIf { it.requestId == "b" && it.attempt == 1 } }
            .assertInputError()
    }

    @Test
    fun 마지막이_아닌_시도의_결정이_retry가_아니면_입력_오류() {
        good().changeAttempt("c", 2) { it.copy(decision = "max-attempts") }.assertInputError()
    }

    @Test
    fun 마지막_시도의_결정이_retry면_입력_오류() {
        good().changeAttempt("c", 3) { it.copy(decision = "retry") }.assertInputError()
    }

    @Test
    fun 다음_시도의_시작이_앞_시도의_끝보다_이르면_입력_오류() {
        val r = good()
        val end1 = r.attempts.getValue("inventory").first { it.requestId == "c" && it.attempt == 1 }.endUs
        r.changeAttempt("c", 2) { it.copy(startUs = end1 - 1) }.assertInputError()
    }

    @Test
    fun 다음_시도의_시작이_앞_시도의_끝과_같으면_받는다() {
        val r = good()
        val end1 = r.attempts.getValue("inventory").first { it.requestId == "c" && it.attempt == 1 }.endUs
        r.changeAttempt("c", 2) { it.copy(startUs = end1) }
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    // ---- attempt-faults.json ----

    @Test
    fun 넣은_실패의_수_파일이_없으면_입력_오류() {
        good().apply { omit += "attempt-faults.json" }.assertInputError()
    }

    private val zeros = """{"fail-first":0,"slow-first":0,"outage":0}"""

    @Test
    fun 넣은_실패의_수에_서비스가_빠지면_입력_오류() {
        good().apply { attemptFaultsText = """{"order":$zeros,"inventory":$zeros}""" }.assertInputError()
    }

    @Test
    fun 넣은_실패의_수에_종류가_빠지면_입력_오류() {
        good().apply {
            attemptFaultsText = """{"order":$zeros,"inventory":$zeros,"payment":{"fail-first":4,"slow-first":1}}"""
        }.assertInputError()
    }

    @Test
    fun 넣은_실패의_수에_여분의_서비스가_있으면_입력_오류() {
        good().apply {
            attemptFaultsText = """{"order":$zeros,"inventory":$zeros,"payment":{"fail-first":4,"slow-first":1,"outage":0},"shipping":$zeros}"""
        }.assertInputError()
    }

    @Test
    fun 넣은_실패의_수에_여분의_종류가_있으면_입력_오류() {
        good().apply {
            attemptFaultsText = """{"order":$zeros,"inventory":$zeros,"payment":{"fail-first":4,"slow-first":1,"outage":0,"delay":0}}"""
        }.assertInputError()
    }

    @Test
    fun 넣은_실패의_수가_문자열이면_입력_오류() {
        good().apply {
            attemptFaultsText = """{"order":$zeros,"inventory":$zeros,"payment":{"fail-first":"4","slow-first":1,"outage":0}}"""
        }.assertInputError()
    }

    @Test
    fun 넣은_실패의_수가_소수면_입력_오류() {
        good().apply {
            attemptFaultsText = """{"order":$zeros,"inventory":$zeros,"payment":{"fail-first":4.0,"slow-first":1,"outage":0}}"""
        }.assertInputError()
    }

    @Test
    fun 넣은_실패의_수가_음수면_입력_오류() {
        good().apply {
            attemptFaultsText = """{"order":{"fail-first":-1,"slow-first":0,"outage":0},"inventory":$zeros,"payment":{"fail-first":4,"slow-first":1,"outage":0}}"""
        }.assertInputError()
    }

    @Test
    fun 넣은_실패의_수를_손으로_써도_통과한다() {
        val r = good().apply {
            attemptFaultsText = """{"order":$zeros,"inventory":$zeros,"payment":{"fail-first":4,"slow-first":1,"outage":0}}"""
        }
        assertEquals(0, r.runRetry(), r.lastOutput)
    }
}
