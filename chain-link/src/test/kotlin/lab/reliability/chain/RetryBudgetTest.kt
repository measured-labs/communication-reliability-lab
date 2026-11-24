package lab.reliability.chain

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// docs/retry.md 4절 — 재시도 예산(토큰 버킷 RetryBudget(capacity K, depositPerCall r)).
// 처음 토큰 = K, deposit: 토큰 ← min(K, 토큰 + r), tryWithdraw: 토큰 ≥ 1 이면 1을 빼고 true, 아니면 빼지 않고 false.
// 소수는 정확히 더한다(0.1 을 열 번 넣으면 정확히 1). deposit·tryWithdraw·tokens 는 서로 배타적.
class RetryBudgetTest {
    @Test
    fun 처음_토큰은_용량이다() {
        assertTrue(sameValue("10", RetryBudget(dec("10"), dec("0.1")).tokens()))
    }

    @Test
    fun 넣으면_r만큼_는다() {
        val b = RetryBudget(dec("3"), dec("0.5"))
        b.tryWithdraw()
        b.tryWithdraw() // 1
        b.deposit()
        assertTrue(sameValue("1.5", b.tokens()), "실제 ${b.tokens()}")
    }

    @Test
    fun 넣어도_용량을_넘지_않는다() {
        val b = RetryBudget(dec("10"), dec("0.1"))
        b.deposit()
        assertTrue(sameValue("10", b.tokens()), "실제 ${b.tokens()}")
    }

    @Test
    fun 넣어서_용량에_딱_닿으면_용량이다() {
        // 9.5 + 0.5 = 10 = K
        val b = RetryBudget(dec("10"), dec("0.5"))
        b.tryWithdraw() // 9
        b.deposit() // 9.5
        b.deposit() // 10
        b.deposit() // 넘지 않는다
        assertTrue(sameValue("10", b.tokens()), "실제 ${b.tokens()}")
    }

    @Test
    fun 빼면_1이_준다() {
        val b = RetryBudget(dec("2.5"), dec("0"))
        assertTrue(b.tryWithdraw())
        assertTrue(sameValue("1.5", b.tokens()), "실제 ${b.tokens()}")
    }

    @Test
    fun 토큰이_정확히_1이면_뺄_수_있다() {
        val b = RetryBudget(dec("1"), dec("0"))
        assertTrue(b.tryWithdraw())
        assertTrue(sameValue("0", b.tokens()))
    }

    @Test
    fun 토큰이_1_미만이면_빼지_못하고_토큰은_그대로다() {
        val b = RetryBudget(dec("0.99"), dec("0"))
        assertFalse(b.tryWithdraw())
        assertTrue(sameValue("0.99", b.tokens()), "실제 ${b.tokens()}")
    }

    @Test
    fun 용량_0_넣는_양_0이면_한_번도_뺄_수_없다() {
        val b = RetryBudget(dec("0"), dec("0"))
        b.deposit()
        assertFalse(b.tryWithdraw())
        assertTrue(sameValue("0", b.tokens()))
    }

    @Test
    fun 소수_0_1을_열_번_넣으면_정확히_1이다() {
        val b = RetryBudget(dec("1"), dec("0.1"))
        assertTrue(b.tryWithdraw()) // 0
        repeat(10) { b.deposit() }
        assertTrue(sameValue("1", b.tokens()), "실제 ${b.tokens()}")
        assertTrue(b.tryWithdraw(), "정확히 1 이면 뺄 수 있다")
        assertTrue(sameValue("0", b.tokens()), "실제 ${b.tokens()}")
    }

    @Test
    fun 소수_0_1을_아홉_번_넣으면_0_9라서_빼지_못한다() {
        val b = RetryBudget(dec("1"), dec("0.1"))
        assertTrue(b.tryWithdraw())
        repeat(9) { b.deposit() }
        assertTrue(sameValue("0.9", b.tokens()), "실제 ${b.tokens()}")
        assertFalse(b.tryWithdraw())
    }

    @Test
    fun 여러_스레드가_동시에_빼도_용량만큼만_빠진다() {
        val b = RetryBudget(dec("1000"), dec("0"))
        val ok = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(8) {
            pool.execute {
                start.await()
                repeat(200) { if (b.tryWithdraw()) ok.incrementAndGet() }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        assertEquals(1000, ok.get())
        assertTrue(sameValue("0", b.tokens()), "실제 ${b.tokens()}")
    }

    @Test
    fun 여러_스레드가_동시에_넣고_빼도_소수가_정확하다() {
        // K = 1000, 처음 1000 → 4 스레드 × 250번 빼기(1000 빠짐) + 4 스레드 × 250번 0.1 넣기(용량에 닿지 않게 먼저 뺀다)
        val b = RetryBudget(dec("1000"), dec("0.1"))
        repeat(500) { assertTrue(b.tryWithdraw()) } // 500
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(4) { pool.execute { start.await(); repeat(250) { b.deposit() } } } // +100
        repeat(4) { pool.execute { start.await(); repeat(100) { check(b.tryWithdraw()) } } } // −400 (늘 ≥ 100 남는다)
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        assertTrue(sameValue("200", b.tokens()), "실제 ${b.tokens()}")
    }
}
