package lab.reliability.chain

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// docs/idempotency.md 4절 — 커밋 뒤 끊김(X-Drop-Fault).
// 해석(fault-header의 DropFaultDirectives.parse — chain-link 가 api 로 기대므로 여기서 시험한다):
//   `<service>:drop-after-commit=<n>[,…]`, n ≥ 1 정수. 빈 값·공백뿐·없음 = 없음. 한 서비스에 지시 하나. 공백은 지시의 앞뒤와 `:`의
//   앞뒤에만(X-Attempt-Fault 와 같다 — docs/measurement.md 8-1). 그 밖은 IllegalArgumentException.
// 정하기(DropFaultInjection.decide): 지시가 있으면 그 requestId 로 든 횟수 k(이번 것 포함, 1부터)를 세고 k ≤ n 이면 끊는다.
//   지시가 없으면 세지 않는다. counts() = {"drop-after-commit": 끊은 수} — counted() 로 센다. reset() 은 카운터와 든 횟수를 비운다.
// 이 파일은 구현을 보지 않고 문서만으로 쓴다.
class DropFaultTest {
    // ---- 해석: 받는 것 ----

    @Test
    fun 없음_빈_값_공백뿐은_지시가_없다() {
        assertEquals(emptyList(), DropFaultDirectives.parse(null))
        assertEquals(emptyList(), DropFaultDirectives.parse(""))
        assertEquals(emptyList(), DropFaultDirectives.parse("   "))
    }

    @Test
    fun 지시_하나() {
        assertEquals(listOf(DropFault("order", 1)), DropFaultDirectives.parse("order:drop-after-commit=1"))
    }

    @Test
    fun 세_서비스를_모두_가리킬_수_있다() {
        val got = DropFaultDirectives.parse("order:drop-after-commit=1,inventory:drop-after-commit=2,payment:drop-after-commit=30")
        assertEquals(
            setOf(DropFault("order", 1), DropFault("inventory", 2), DropFault("payment", 30)),
            got.toSet(),
        )
        assertEquals(3, got.size)
    }

    @Test
    fun 지시의_앞뒤와_쌍점_앞뒤의_공백은_받는다() {
        val got = DropFaultDirectives.parse(" payment : drop-after-commit=2 ,  order:drop-after-commit=1")
        assertEquals(setOf(DropFault("payment", 2), DropFault("order", 1)), got.toSet())
    }

    @Test
    fun 종류의_이름() {
        assertEquals("drop-after-commit", DropFault.KIND)
    }

    // ---- 해석: 틀린 헤더 ----

    private fun bad(raw: String) {
        assertFailsWith<IllegalArgumentException>("'$raw' 는 틀린 헤더") { DropFaultDirectives.parse(raw) }
    }

    @Test
    fun n이_0이면_틀린다() = bad("order:drop-after-commit=0")

    @Test
    fun n이_음수면_틀린다() = bad("order:drop-after-commit=-1")

    @Test
    fun n에_부호가_있으면_틀린다() = bad("order:drop-after-commit=+1")

    @Test
    fun n이_소수면_틀린다() = bad("order:drop-after-commit=1.5")

    @Test
    fun n이_숫자가_아니면_틀린다() = bad("order:drop-after-commit=abc")

    @Test
    fun n이_비면_틀린다() = bad("order:drop-after-commit=")

    @Test
    fun 값이_없으면_틀린다() = bad("order:drop-after-commit")

    @Test
    fun 등호_앞뒤의_공백은_틀린다() {
        bad("order:drop-after-commit =1")
        bad("order:drop-after-commit= 1")
    }

    @Test
    fun 값_안의_공백은_틀린다() = bad("order:drop-after-commit=1 2")

    @Test
    fun 종류_안의_공백은_틀린다() = bad("order:drop-after -commit=1")

    @Test
    fun 한_서비스를_두_번_가리키면_틀린다() = bad("order:drop-after-commit=1,order:drop-after-commit=2")

    @Test
    fun 같은_지시를_두_번_써도_틀린다() = bad("payment:drop-after-commit=1, payment:drop-after-commit=1")

    @Test
    fun 모르는_서비스는_틀린다() = bad("shipping:drop-after-commit=1")

    @Test
    fun 모르는_종류는_틀린다() = bad("order:fail-after-commit=1")

    @Test
    fun 서비스가_없으면_틀린다() = bad(":drop-after-commit=1")

    @Test
    fun 쌍점이_없으면_틀린다() = bad("order drop-after-commit=1")

    @Test
    fun 끝의_쉼표는_빈_지시라_틀린다() = bad("order:drop-after-commit=1,")

    @Test
    fun 앞의_쉼표도_틀린다() = bad(",order:drop-after-commit=1")

    @Test
    fun 쉼표뿐이면_틀린다() = bad(",")

    @Test
    fun 대소문자를_가린다() {
        bad("Order:drop-after-commit=1")
        bad("order:DROP-AFTER-COMMIT=1")
    }

    // ---- 정하기 ----

    @Test
    fun 처음_n번은_끊고_그_뒤는_끊지_않는다() {
        val d = DropFaultInjection()
        val f = DropFault("order", 2)
        assertEquals(listOf(true, true, false, false), List(4) { d.decide("r1", f) })
    }

    @Test
    fun n이_1이면_처음만_끊는다() {
        val d = DropFaultInjection()
        val f = DropFault("payment", 1)
        assertTrue(d.decide("r1", f))
        assertFalse(d.decide("r1", f))
    }

    @Test
    fun 든_횟수는_requestId마다_따로_센다() {
        val d = DropFaultInjection()
        val f = DropFault("order", 1)
        assertTrue(d.decide("r1", f))
        assertTrue(d.decide("r2", f))
        assertFalse(d.decide("r1", f))
        assertFalse(d.decide("r2", f))
    }

    @Test
    fun 지시가_없으면_끊지_않고_세지도_않는다() {
        val d = DropFaultInjection()
        assertFalse(d.decide("r1", null))
        assertFalse(d.decide("r1", null))
        // 지시 없이 두 번 들었어도 지시가 있는 첫 것은 k = 1
        assertTrue(d.decide("r1", DropFault("order", 1)))
    }

    @Test
    fun 정하기만_해서는_끊은_수가_늘지_않는다() {
        // 끊은 수는 실제로 끊을 때(counted) 센다 — 처리 코드가 응답을 내는 자리에 이르지 못하면 세지 않는다
        val d = DropFaultInjection()
        d.decide("r1", DropFault("order", 3))
        d.decide("r2", DropFault("order", 3))
        assertEquals(mapOf("drop-after-commit" to 0L), d.counts())
    }

    @Test
    fun counted는_끊은_수를_하나씩_센다() {
        val d = DropFaultInjection()
        d.counted()
        d.counted()
        assertEquals(mapOf("drop-after-commit" to 2L), d.counts())
    }

    @Test
    fun 처음의_끊은_수는_0이다() {
        assertEquals(mapOf("drop-after-commit" to 0L), DropFaultInjection().counts())
    }

    @Test
    fun reset은_끊은_수와_든_횟수를_비운다() {
        val d = DropFaultInjection()
        val f = DropFault("order", 1)
        assertTrue(d.decide("r1", f))
        d.counted()
        assertFalse(d.decide("r1", f))
        d.reset()
        assertEquals(mapOf("drop-after-commit" to 0L), d.counts())
        assertTrue(d.decide("r1", f), "든 횟수도 비웠으면 k = 1 로 다시 끊는다")
    }

    @Test
    fun 동시에_들어도_정확히_n번만_끊는다() {
        val d = DropFaultInjection()
        val f = DropFault("inventory", 10)
        val threads = 64
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val dropped = AtomicInteger()
        try {
            repeat(threads * 4) {
                pool.submit {
                    start.await()
                    if (d.decide("same", f)) dropped.incrementAndGet()
                }
            }
            start.countDown()
        } finally {
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
        assertEquals(10, dropped.get())
    }

    @Test
    fun 동시에_센_끊은_수를_잃지_않는다() {
        val d = DropFaultInjection()
        val pool = Executors.newFixedThreadPool(32)
        val start = CountDownLatch(1)
        try {
            repeat(1000) { pool.submit { start.await(); d.counted() } }
            start.countDown()
        } finally {
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
        assertEquals(mapOf("drop-after-commit" to 1000L), d.counts())
    }
}
