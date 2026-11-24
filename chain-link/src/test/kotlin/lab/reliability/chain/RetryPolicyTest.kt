package lab.reliability.chain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// docs/retry.md 2절(실패의 갈래 — RetryPolicy.classOf)과 3절(간격의 식 — exponentialCeilingMs, delayBeforeMs).
// 6절: 순수 함수도 따로 시험할 수 있다. delayBeforeMs(n, u)는 n < 2 이거나 jitter 에서 u 가 [0, 1) 밖이면 IllegalArgumentException.
class RetryPolicyTest {
    private val policy = RetryPolicy(3, BOTH, Backoff.NONE, 0, 0)

    // ---- 2절: 갈래 ----

    @Test
    fun 성공은_갈래가_없다() {
        assertNull(policy.classOf(OK))
    }

    @Test
    fun HTTP_500은_failed다() {
        assertEquals(RetryOn.FAILED, policy.classOf(failed("HTTP_500")))
    }

    @Test
    fun HTTP_503은_failed다() {
        assertEquals(RetryOn.FAILED, policy.classOf(failed("HTTP_503")))
    }

    @Test
    fun HTTP_599는_failed다_5xx의_위_끝() {
        assertEquals(RetryOn.FAILED, policy.classOf(failed("HTTP_599")))
    }

    @Test
    fun HTTP_499는_갈래가_없다_5xx의_아래_바깥() {
        assertNull(policy.classOf(failed("HTTP_499")))
    }

    @Test
    fun 연결_거부는_failed다() {
        assertEquals(RetryOn.FAILED, policy.classOf(failed("CONNECT_REFUSED")))
    }

    @Test
    fun 연결_타임아웃은_failed다() {
        assertEquals(RetryOn.FAILED, policy.classOf(failed("CONNECT_TIMEOUT")))
    }

    @Test
    fun HTTP_400은_다시_시도하지_않는다() {
        assertNull(policy.classOf(failed("HTTP_400")))
    }

    @Test
    fun HTTP_404는_다시_시도하지_않는다() {
        assertNull(policy.classOf(failed("HTTP_404")))
    }

    @Test
    fun 요청_타임아웃은_unknown이다() {
        assertEquals(RetryOn.UNKNOWN, policy.classOf(unknown("REQUEST_TIMEOUT")))
    }

    @Test
    fun HTTP_504는_unknown이다() {
        assertEquals(RetryOn.UNKNOWN, policy.classOf(unknown("HTTP_504")))
    }

    @Test
    fun IO_예외는_unknown이다() {
        assertEquals(RetryOn.UNKNOWN, policy.classOf(unknown("IO_EOFException")))
    }

    // ---- 3절: E(n) = min(C, B·2^(n−2)) ----

    @Test
    fun 지수_간격의_위_끝은_두_배씩_커진다() {
        val p = RetryPolicy(10, BOTH, Backoff.EXPONENTIAL, 100, 1000)
        assertEquals(listOf(100L, 200L, 400L, 800L), (2..5).map { p.exponentialCeilingMs(it) })
    }

    @Test
    fun 지수_간격의_위_끝은_C를_넘지_않는다() {
        val p = RetryPolicy(10, BOTH, Backoff.EXPONENTIAL, 100, 1000)
        assertEquals(listOf(1000L, 1000L), (6..7).map { p.exponentialCeilingMs(it) })
    }

    @Test
    fun 지수_간격이_C에_딱_닿는_n에서는_C다() {
        // 125·2^3 = 1000 = C, 그 앞은 500
        val p = RetryPolicy(10, BOTH, Backoff.EXPONENTIAL, 125, 1000)
        assertEquals(listOf(500L, 1000L, 1000L), (4..6).map { p.exponentialCeilingMs(it) })
    }

    @Test
    fun 지수_간격은_n이_커도_넘치지_않고_C에서_멈춘다() {
        // 100·2^62 은 Long 을 넘는다 — 두 배씩 키우다 C 이상이면 C 에서 멈춘다
        val p = RetryPolicy(10, BOTH, Backoff.EXPONENTIAL, 100, 1000)
        assertEquals(listOf(1000L, 1000L, 1000L), listOf(64, 65, 100).map { p.exponentialCeilingMs(it) })
    }

    @Test
    fun B가_0이면_지수_간격은_0이다() {
        assertEquals(0L, RetryPolicy(10, BOTH, Backoff.EXPONENTIAL, 0, 0).exponentialCeilingMs(5))
    }

    // ---- 3절: backoff 마다 d(n) ----

    @Test
    fun 간격_없음은_늘_0이다() {
        val p = RetryPolicy(5, BOTH, Backoff.NONE, 100, 1000)
        assertEquals(listOf(0L, 0L, 0L), listOf(2, 3, 5).map { p.delayBeforeMs(it, 0.5) })
    }

    @Test
    fun 고정_간격은_n과_무관하게_B다() {
        val p = RetryPolicy(10, BOTH, Backoff.FIXED, 200, 1000)
        assertEquals(listOf(200L, 200L, 200L), listOf(2, 3, 10).map { p.delayBeforeMs(it, 0.5) })
    }

    @Test
    fun 고정_간격은_C를_쓰지_않는다() {
        // C(=50) < B(=200)여도 고정 간격은 B — "C ≥ B" 조건은 지수 간격에만 있다
        val p = RetryPolicy(3, BOTH, Backoff.FIXED, 200, 50)
        assertEquals(200L, p.delayBeforeMs(3, 0.5))
    }

    @Test
    fun 지수_간격은_E_n이다() {
        val p = RetryPolicy(10, BOTH, Backoff.EXPONENTIAL, 100, 1000)
        assertEquals(listOf(100L, 200L, 400L, 800L, 1000L, 1000L), (2..7).map { p.delayBeforeMs(it, 0.5) })
    }

    private val jitter = RetryPolicy(10, BOTH, Backoff.EXPONENTIAL_JITTER, 100, 1000)

    @Test
    fun 지터_u가_0이면_간격은_0이다() {
        assertEquals(0L, jitter.delayBeforeMs(2, 0.0))
    }

    @Test
    fun 지터는_u_곱하기_E_n이다() {
        assertEquals(listOf(50L, 100L, 200L), listOf(2, 3, 4).map { jitter.delayBeforeMs(it, 0.5) })
    }

    @Test
    fun 지터는_반올림이_아니라_내림이다() {
        // 0.996·100 = 99.6 → 99, 0.999·200 = 199.8 → 199
        assertEquals(listOf(99L, 199L), listOf(jitter.delayBeforeMs(2, 0.996), jitter.delayBeforeMs(3, 0.999)))
    }

    @Test
    fun 지터는_u가_1에_가까워도_E_n_미만이다() {
        val u = Math.nextDown(1.0)
        assertEquals(listOf(99L, 999L), listOf(jitter.delayBeforeMs(2, u), jitter.delayBeforeMs(7, u)))
    }

    @Test
    fun 지터는_C에서_멈춘_E_n을_쓴다() {
        // n=7: E = min(1000, 3200) = 1000 → 0.5·1000
        assertEquals(500L, jitter.delayBeforeMs(7, 0.5))
    }

    @Test
    fun 지터에서_E_n이_0이면_간격은_0이다() {
        val p = RetryPolicy(3, BOTH, Backoff.EXPONENTIAL_JITTER, 0, 0)
        assertEquals(0L, p.delayBeforeMs(2, 0.9))
    }

    @Test
    fun 지터의_범위는_0_이상_E_n_미만이다() {
        val us = listOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99, Math.nextDown(1.0))
        for (n in 2..8) {
            val e = jitter.exponentialCeilingMs(n)
            for (u in us) {
                val d = jitter.delayBeforeMs(n, u)
                kotlin.test.assertTrue(d in 0 until e, "n=$n u=$u: $d 가 [0, $e) 밖")
            }
        }
    }

    // ---- 6절: 입력의 범위 ----

    @Test
    fun n이_1이면_IllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { RetryPolicy(3, BOTH, Backoff.FIXED, 100, 100).delayBeforeMs(1, 0.5) }
    }

    @Test
    fun n이_2면_받는다() {
        assertEquals(100L, RetryPolicy(3, BOTH, Backoff.FIXED, 100, 100).delayBeforeMs(2, 0.5))
    }

    @Test
    fun 지터에서_u가_1이면_IllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { jitter.delayBeforeMs(2, 1.0) }
    }

    @Test
    fun 지터에서_u가_음수면_IllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { jitter.delayBeforeMs(2, -0.01) }
    }
}
