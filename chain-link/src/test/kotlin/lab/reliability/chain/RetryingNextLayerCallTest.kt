package lab.reliability.chain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// docs/retry.md 2절(결정의 차례), 3절(간격 — 첫 시도 앞에는 자지 않는다), 4절(예산), 5절(시도 기록), 6절(입구의 약속:
// random 은 jitter 일 때만 재시도마다 한 번, sleeper 는 재시도마다 한 번 — 0이어도, clock 은 시도마다 두 번,
// budget.deposit() 은 call 하나마다 처음 시도 앞에서 한 번).
class RetryingNextLayerCallTest {
    private fun none(max: Int, on: Set<RetryOn> = BOTH) = RetryPolicy(max, on, Backoff.NONE, 0, 0)

    // ---- 2절: 결정의 차례 ----

    @Test
    fun 첫_시도가_성공하면_한_번만_부르고_success다() {
        val h = Harness(none(3), outcomes = listOf(OK))
        assertEquals(nameOf(OK), nameOf(h.call()))
        assertEquals(1, h.delegateCalls)
        assertEquals(listOf("success"), h.decisions)
    }

    @Test
    fun 다시_시도하지_않는_실패는_한_번만_부르고_not_retryable이다() {
        val h = Harness(none(3), outcomes = listOf(failed("HTTP_404")))
        assertEquals(nameOf(failed("HTTP_404")), nameOf(h.call()))
        assertEquals(listOf("not-retryable"), h.decisions)
    }

    @Test
    fun retry_on에_failed가_없으면_500은_not_retryable이다() {
        val h = Harness(none(3, setOf(RetryOn.UNKNOWN)), outcomes = listOf(HTTP_500))
        assertEquals(nameOf(HTTP_500), nameOf(h.call()))
        assertEquals(listOf("not-retryable"), h.decisions)
    }

    @Test
    fun retry_on에_unknown이_없으면_타임아웃은_not_retryable이다() {
        val h = Harness(none(3, setOf(RetryOn.FAILED)), outcomes = listOf(unknown("REQUEST_TIMEOUT")))
        assertEquals(nameOf(unknown("REQUEST_TIMEOUT")), nameOf(h.call()))
        assertEquals(listOf("not-retryable"), h.decisions)
    }

    @Test
    fun failed는_다시_시도한다() {
        val h = Harness(none(3), outcomes = listOf(HTTP_500, failed("CONNECT_REFUSED"), OK))
        assertEquals(nameOf(OK), nameOf(h.call()))
        assertEquals(listOf("retry", "retry", "success"), h.decisions)
    }

    @Test
    fun unknown은_다시_시도한다() {
        val h = Harness(none(3), outcomes = listOf(unknown("REQUEST_TIMEOUT"), unknown("HTTP_504"), OK))
        assertEquals(nameOf(OK), nameOf(h.call()))
        assertEquals(listOf("retry", "retry", "success"), h.decisions)
    }

    @Test
    fun 갈래가_없는_실패가_시도_상한보다_앞선다() {
        // 최대 1에서 404 → max-attempts 가 아니라 not-retryable(차례 2가 3보다 앞)
        val h = Harness(none(1), outcomes = listOf(failed("HTTP_404")))
        h.call()
        assertEquals(listOf("not-retryable"), h.decisions)
    }

    @Test
    fun 시도_상한이_예산보다_앞선다() {
        // 최대 1, 예산 0 → budget-denied 가 아니라 max-attempts(차례 3이 4보다 앞)
        val h = Harness(none(1), RetryBudget(dec("0"), dec("0")), listOf(HTTP_500))
        h.call()
        assertEquals(listOf("max-attempts"), h.decisions)
    }

    @Test
    fun 시도_상한에서는_토큰을_빼지_않는다() {
        val budget = RetryBudget(dec("2"), dec("0"))
        Harness(none(1), budget, listOf(HTTP_500)).call()
        assertTrue(sameValue("2", budget.tokens()), "실제 ${budget.tokens()}")
    }

    @Test
    fun 다시_시도하지_않는_실패에서는_토큰을_빼지_않는다() {
        val budget = RetryBudget(dec("2"), dec("0"))
        Harness(none(3), budget, listOf(failed("HTTP_400"))).call()
        assertTrue(sameValue("2", budget.tokens()), "실제 ${budget.tokens()}")
    }

    @Test
    fun 성공에서는_토큰을_빼지_않는다() {
        val budget = RetryBudget(dec("2"), dec("0"))
        Harness(none(3), budget, listOf(OK)).call()
        assertTrue(sameValue("2", budget.tokens()), "실제 ${budget.tokens()}")
    }

    @Test
    fun 재시도마다_토큰을_하나_뺀다() {
        val budget = RetryBudget(dec("5"), dec("0"))
        Harness(none(3), budget, listOf(HTTP_500, HTTP_500, OK)).call()
        assertTrue(sameValue("3", budget.tokens()), "실제 ${budget.tokens()}")
    }

    @Test
    fun 예산이_비면_budget_denied이고_다시_부르지_않는다() {
        val h = Harness(none(3), RetryBudget(dec("0"), dec("0")), listOf(HTTP_500))
        assertEquals(nameOf(HTTP_500), nameOf(h.call()))
        assertEquals(1, h.delegateCalls)
        assertEquals(listOf("budget-denied"), h.decisions)
        assertEquals(emptyList<Long>(), h.sleeps, "budget-denied 면 자지 않는다")
    }

    @Test
    fun 토큰을_다_쓰면_그때의_결과로_budget_denied() {
        // K=2: 재시도 둘 뒤 셋째 재시도는 거절 — 셋째 시도의 결과(503)를 돌려준다
        val budget = RetryBudget(dec("2"), dec("0"))
        val h = Harness(none(5), budget, listOf(HTTP_500, unknown("REQUEST_TIMEOUT"), failed("HTTP_503"), OK))
        assertEquals(nameOf(failed("HTTP_503")), nameOf(h.call()))
        assertEquals(listOf("retry", "retry", "budget-denied"), h.decisions)
        assertTrue(sameValue("0", budget.tokens()), "budget-denied 는 토큰을 빼지 않는다: ${budget.tokens()}")
    }

    @Test
    fun 토큰이_정확히_1이면_한_번_재시도한다() {
        val h = Harness(none(3), RetryBudget(dec("1"), dec("0")), listOf(HTTP_500, HTTP_500))
        h.call()
        assertEquals(listOf("retry", "budget-denied"), h.decisions)
    }

    @Test
    fun 토큰이_1_미만이면_재시도하지_않는다() {
        val h = Harness(none(3), RetryBudget(dec("0.9"), dec("0")), listOf(HTTP_500))
        h.call()
        assertEquals(listOf("budget-denied"), h.decisions)
    }

    @Test
    fun 예산이_없으면_시도_상한까지_재시도한다() {
        val h = Harness(none(5), null, listOf(HTTP_500, HTTP_500, HTTP_500, HTTP_500, OK))
        assertEquals(nameOf(OK), nameOf(h.call()))
        assertEquals(5, h.delegateCalls)
    }

    // ---- 1·2절: 시도 상한, 마지막 시도의 결과 ----

    @Test
    fun 최대_시도가_1이면_재시도하지_않는다() {
        val h = Harness(none(1), outcomes = listOf(HTTP_500))
        assertEquals(nameOf(HTTP_500), nameOf(h.call()))
        assertEquals(1, h.delegateCalls)
        assertEquals(listOf("max-attempts"), h.decisions)
        assertEquals(emptyList<Long>(), h.sleeps)
    }

    @Test
    fun 최대_시도_3은_처음과_재시도_둘이다() {
        val h = Harness(none(3), outcomes = listOf(HTTP_500, HTTP_500, HTTP_500))
        h.call()
        assertEquals(3, h.delegateCalls)
        assertEquals(listOf("retry", "retry", "max-attempts"), h.decisions)
    }

    @Test
    fun 시도_상한에_딱_닿은_시도가_성공하면_success다() {
        val h = Harness(none(3), outcomes = listOf(HTTP_500, HTTP_500, OK))
        assertEquals(nameOf(OK), nameOf(h.call()))
        assertEquals(listOf("retry", "retry", "success"), h.decisions)
    }

    @Test
    fun 시도를_다_쓰면_마지막_시도의_결과를_돌려준다() {
        // 문서의 예: 1번째 타임아웃, 2번째 500 → Failed:HTTP_500
        val h = Harness(none(2), outcomes = listOf(unknown("REQUEST_TIMEOUT"), HTTP_500))
        assertEquals(nameOf(HTTP_500), nameOf(h.call()))
    }

    @Test
    fun 시도를_다_쓰면_앞_시도가_아니라_마지막_시도의_결과다() {
        val h = Harness(none(3), outcomes = listOf(HTTP_500, failed("CONNECT_REFUSED"), unknown("REQUEST_TIMEOUT")))
        assertEquals(nameOf(unknown("REQUEST_TIMEOUT")), nameOf(h.call()))
    }

    @Test
    fun 재시도에도_같은_금액을_넘긴다() {
        val h = Harness(none(3), outcomes = listOf(HTTP_500, HTTP_500, OK))
        h.call(4321)
        assertEquals(listOf(4321L, 4321L, 4321L), h.amounts)
    }

    // ---- 4절·6절: deposit 은 call 하나마다 처음 시도 앞에서 ----

    @Test
    fun 호출마다_처음_시도_앞에서_토큰을_넣는다() {
        // K=2, r=1. 토큰을 0으로 비운 뒤 부르면 처음 시도 앞의 deposit 으로 1 이 되어 재시도할 수 있다
        val budget = RetryBudget(dec("2"), dec("1"))
        budget.tryWithdraw()
        budget.tryWithdraw()
        val h = Harness(none(2), budget, listOf(HTTP_500, OK))
        assertEquals(nameOf(OK), nameOf(h.call()))
        assertEquals(listOf("retry", "success"), h.decisions)
        assertTrue(sameValue("0", budget.tokens()), "실제 ${budget.tokens()}")
    }

    @Test
    fun 호출_하나에_토큰은_한_번만_넣는다() {
        // K=10, r=0.5. 7 로 낮춘 뒤 시도 3번(재시도 2)짜리 호출: 7 + 0.5 − 2 = 5.5 (시도마다 넣으면 6.5)
        val budget = RetryBudget(dec("10"), dec("0.5"))
        repeat(3) { budget.tryWithdraw() }
        Harness(none(3), budget, listOf(HTTP_500, HTTP_500, OK)).call()
        assertTrue(sameValue("5.5", budget.tokens()), "실제 ${budget.tokens()}")
    }

    @Test
    fun 재시도_수는_K_더하기_r_곱하기_호출_수를_넘지_않는다() {
        // K=1, r=0.5, 모든 호출이 실패(최대 2): 1 → (넣기 1, 빼기 0) → (0.5, 거절) → (1, 빼기 0) → (0.5, 거절)
        val budget = RetryBudget(dec("1"), dec("0.5"))
        val h = Harness(none(2), budget, List(8) { HTTP_500 })
        repeat(4) { h.call() }
        assertEquals(
            listOf("retry", "max-attempts", "budget-denied", "retry", "max-attempts", "budget-denied"),
            h.decisions,
        )
    }

    // ---- 5절: 시도 기록 ----

    @Test
    fun 시도마다_기록을_하나_남긴다() {
        val h = Harness(none(3), outcomes = listOf(HTTP_500, unknown("REQUEST_TIMEOUT"), OK), requestId = "abc")
        h.call()
        assertEquals(3, h.records.size)
        assertEquals(listOf("abc", "abc", "abc"), h.records.map { it.requestId })
        assertEquals(listOf(1, 2, 3), h.records.map { it.attempt })
    }

    @Test
    fun 기록의_결과_이름은_2절의_이름이다() {
        val h = Harness(none(3), outcomes = listOf(HTTP_500, unknown("REQUEST_TIMEOUT"), OK))
        h.call()
        assertEquals(listOf("Failed:HTTP_500", "Unknown:REQUEST_TIMEOUT", "Completed"), h.records.map { it.outcome })
    }

    @Test
    fun 한_호출의_시도는_같은_callId다() {
        val h = Harness(none(3), outcomes = listOf(HTTP_500, HTTP_500, OK))
        h.call()
        assertEquals(1, h.records.map { it.callId }.toSet().size)
    }

    @Test
    fun 다음_호출의_callId는_1_크다() {
        val h = Harness(none(2), outcomes = listOf(HTTP_500, OK, OK))
        h.call()
        h.call()
        val ids = h.records.map { it.callId }
        assertEquals(ids[0], ids[1])
        assertEquals(ids[0] + 1, ids[2])
    }

    @Test
    fun 기록의_시작과_끝은_시도의_앞뒤_시계다() {
        // 시계는 1000, 2000, … — 시도 1 은 (1000, 2000), 시도 2 는 (3000, 4000)
        val h = Harness(none(2), outcomes = listOf(HTTP_500, OK))
        h.call()
        assertEquals(listOf(1000L to 2000L, 3000L to 4000L), h.records.map { it.startUs to it.endUs })
    }

    @Test
    fun 시계는_시도마다_두_번_아래_층_호출의_앞뒤에서_불린다() {
        val h = Harness(RetryPolicy(3, BOTH, Backoff.FIXED, 10, 10), outcomes = listOf(HTTP_500, HTTP_500, OK))
        h.call()
        assertEquals(6, h.clockCalls)
        // 잠은 앞 시도의 끝 시계 뒤, 다음 시도의 시작 시계 앞
        assertEquals(
            listOf("clock", "call", "clock", "sleep", "clock", "call", "clock", "sleep", "clock", "call", "clock"),
            h.events,
        )
    }

    @Test
    fun 기록의_결정은_마지막만_retry가_아니다() {
        val h = Harness(none(4), outcomes = listOf(HTTP_500, HTTP_500, HTTP_500, failed("HTTP_404")))
        h.call()
        assertEquals(listOf("retry", "retry", "retry", "not-retryable"), h.decisions)
    }

    // ---- 3절·6절: 잠과 난수 ----

    @Test
    fun 첫_시도의_간격은_0이고_자지_않는다() {
        val h = Harness(RetryPolicy(3, BOTH, Backoff.FIXED, 200, 200), outcomes = listOf(OK))
        h.call()
        assertEquals(0L, h.records.single().delayBeforeMs)
        assertEquals(emptyList<Long>(), h.sleeps)
    }

    @Test
    fun 간격_없음이어도_재시도마다_0으로_잔다() {
        val h = Harness(none(3), outcomes = listOf(HTTP_500, HTTP_500, OK))
        h.call()
        assertEquals(listOf(0L, 0L), h.sleeps)
        assertEquals(listOf(0L, 0L, 0L), h.records.map { it.delayBeforeMs })
    }

    @Test
    fun 고정_간격은_재시도마다_B로_잔다() {
        val h = Harness(RetryPolicy(3, BOTH, Backoff.FIXED, 200, 0), outcomes = listOf(HTTP_500, HTTP_500, OK))
        h.call()
        assertEquals(listOf(200L, 200L), h.sleeps)
        assertEquals(listOf(0L, 200L, 200L), h.records.map { it.delayBeforeMs })
    }

    @Test
    fun 지수_간격은_재시도마다_E_n으로_잔다() {
        val h = Harness(
            RetryPolicy(5, BOTH, Backoff.EXPONENTIAL, 100, 300),
            outcomes = listOf(HTTP_500, HTTP_500, HTTP_500, HTTP_500, HTTP_500),
        )
        h.call()
        assertEquals(listOf(100L, 200L, 300L, 300L), h.sleeps)
        assertEquals(listOf(0L, 100L, 200L, 300L, 300L), h.records.map { it.delayBeforeMs })
    }

    @Test
    fun 마지막_시도_뒤에는_자지_않는다() {
        val h = Harness(RetryPolicy(2, BOTH, Backoff.FIXED, 50, 50), outcomes = listOf(HTTP_500, HTTP_500))
        h.call()
        assertEquals(listOf(50L), h.sleeps)
    }

    @Test
    fun 지터는_재시도마다_난수를_한_번_부르고_u_곱하기_E_n으로_잔다() {
        // B=100, C=1000: 2번째 앞 ⌊0.5·100⌋ = 50, 3번째 앞 ⌊0.25·200⌋ = 50, 4번째 앞 ⌊0.999·400⌋ = 399
        val h = Harness(
            RetryPolicy(4, BOTH, Backoff.EXPONENTIAL_JITTER, 100, 1000),
            outcomes = listOf(HTTP_500, HTTP_500, HTTP_500, OK),
            randoms = listOf(0.5, 0.25, 0.999),
        )
        h.call()
        assertEquals(3, h.randomCalls)
        assertEquals(listOf(50L, 50L, 399L), h.sleeps)
        assertEquals(listOf(0L, 50L, 50L, 399L), h.records.map { it.delayBeforeMs })
    }

    @Test
    fun 지터가_아니면_난수를_부르지_않는다() {
        for (backoff in listOf(Backoff.NONE, Backoff.FIXED, Backoff.EXPONENTIAL)) {
            val h = Harness(RetryPolicy(3, BOTH, backoff, 100, 1000), outcomes = listOf(HTTP_500, HTTP_500, OK))
            h.call()
            assertEquals(0, h.randomCalls, "$backoff")
        }
    }

    @Test
    fun 지터에서_재시도가_없으면_난수를_부르지_않는다() {
        val h = Harness(RetryPolicy(3, BOTH, Backoff.EXPONENTIAL_JITTER, 100, 1000), outcomes = listOf(OK))
        h.call()
        assertEquals(0, h.randomCalls)
    }

    @Test
    fun 예산_거절에서는_잠도_난수도_없다() {
        val h = Harness(
            RetryPolicy(3, BOTH, Backoff.EXPONENTIAL_JITTER, 100, 1000),
            RetryBudget(dec("0"), dec("0")),
            listOf(HTTP_500),
            randoms = listOf(0.5),
        )
        h.call()
        assertEquals(0, h.randomCalls)
        assertEquals(emptyList<Long>(), h.sleeps)
    }
}
