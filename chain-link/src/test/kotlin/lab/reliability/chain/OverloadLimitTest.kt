package lab.reliability.chain

import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// docs/measurement.md 10-1(시각과 차례, 끝점의 값)·10-6(서비스 쪽 입구) — OverloadLimit(enabled, limit, windowMs, clock).
// 시각 = max(clock(), 앞 시각)(μs), 그 시각으로 판정. 꺼져 있으면 arrive 는 IllegalStateException, 센 수는 늘 0.
// reset 은 두 수와 창을 비우고 앞 시각은 남긴다. settings 는 /settings 의 overload.
// 이 파일은 구현을 보지 않고 문서만으로 쓴다.
class OverloadLimitTest {
    /** 차례대로 값을 내는 시계(μs). 다 쓰면 마지막 값. */
    private fun clockOf(vararg values: Long): () -> Long {
        var i = 0
        return { values[minOf(i++, values.lastIndex)] }
    }

    private fun Map<String, Any?>.normalized(): Map<String, Any?> =
        mapValues { (_, v) -> if (v is Number) v.toLong() else v }

    // ---- 시각과 판정 ----

    @Test
    fun 시각은_시계의_값이다() {
        val l = OverloadLimit(true, 5, 1, clockOf(1_000, 2_000))
        assertEquals(1_000L, l.arrive().atUs)
        assertEquals(2_000L, l.arrive().atUs)
    }

    @Test
    fun 시계가_거꾸로_가면_앞_시각을_쓴다() {
        val l = OverloadLimit(true, 5, 1, clockOf(100, 50, 200))
        assertEquals(listOf(100L, 100L, 200L), List(3) { l.arrive().atUs })
    }

    @Test
    fun 창은_windowMs_를_us로_바꾼_길이다() {
        // windowMs 1 = 1000us. 0 과 999 는 한 창 → 한도 1 이면 둘째 거절(창을 1us로 읽으면 받았을 것이다)
        val l = OverloadLimit(true, 1, 1, clockOf(0, 999))
        assertFalse(l.arrive().rejected)
        assertTrue(l.arrive().rejected)
    }

    @Test
    fun 정확히_windowMs_뒤의_호출은_앞_호출을_세지_않는다() {
        val l = OverloadLimit(true, 1, 1, clockOf(0, 1000))
        assertFalse(l.arrive().rejected)
        assertFalse(l.arrive().rejected)
    }

    @Test
    fun 거절한_호출도_세어_다음을_거절한다() {
        // 한도 2, 창 1ms, 400us 간격: 0, 400 받음, 800 거절, 1200 의 창 (200, 1200] = 400, 800, 1200 → 거절
        val l = OverloadLimit(true, 2, 1, clockOf(0, 400, 800, 1200))
        assertEquals(listOf(false, false, true, true), List(4) { l.arrive().rejected })
    }

    @Test
    fun 시계가_거꾸로_가서_같은_시각이_된_호출도_창에서_센다() {
        val l = OverloadLimit(true, 1, 1, clockOf(500, 10))
        assertFalse(l.arrive().rejected)
        val second = l.arrive()
        assertEquals(500L, second.atUs)
        assertTrue(second.rejected)
    }

    // ---- 센 수 ----

    @Test
    fun 센_수는_든_수와_거절_수다() {
        val l = OverloadLimit(true, 2, 1, clockOf(0, 1, 2, 3))
        repeat(4) { l.arrive() }
        assertEquals(mapOf("arrivals" to 4L, "rejected" to 2L), l.counts().normalized())
    }

    @Test
    fun 처음의_센_수는_0이다() {
        assertEquals(mapOf("arrivals" to 0L, "rejected" to 0L), OverloadLimit(true, 2, 1, clockOf(0)).counts().normalized())
    }

    // ---- reset ----

    @Test
    fun reset은_두_수를_비운다() {
        val l = OverloadLimit(true, 1, 1, clockOf(0, 0))
        l.arrive(); l.arrive()
        l.reset()
        assertEquals(mapOf("arrivals" to 0L, "rejected" to 0L), l.counts().normalized())
    }

    @Test
    fun reset은_창을_비운다() {
        // 한도 1: 1000 에 둘 들어 둘째 거절. 비운 뒤 같은 시각 1000 의 호출은 빈 창이라 받는다.
        val l = OverloadLimit(true, 1, 1, clockOf(1000, 1000, 1000))
        l.arrive(); l.arrive()
        l.reset()
        val a = l.arrive()
        assertFalse(a.rejected, "비운 창에 하나만 있다")
        assertEquals(mapOf("arrivals" to 1L, "rejected" to 0L), l.counts().normalized())
    }

    @Test
    fun reset은_앞_시각을_남긴다() {
        val l = OverloadLimit(true, 5, 1, clockOf(1000, 500))
        l.arrive()
        l.reset()
        assertEquals(1000L, l.arrive().atUs, "비운 뒤에도 시각은 거꾸로 가지 않는다")
    }

    // ---- 꺼짐 ----

    @Test
    fun 꺼져_있으면_arrive는_IllegalStateException() {
        val l = OverloadLimit(false, 50, 1000, clockOf(0))
        assertFailsWith<IllegalStateException> { l.arrive() }
    }

    @Test
    fun 꺼져_있으면_센_수는_늘_0이다() {
        val l = OverloadLimit(false, 50, 1000, clockOf(0))
        runCatching { l.arrive() }
        assertEquals(mapOf("arrivals" to 0L, "rejected" to 0L), l.counts().normalized())
    }

    @Test
    fun 꺼져_있으면_한도와_창을_검사하지_않는다() {
        OverloadLimit(false, 0, 0, clockOf(0)) // 예외가 아니다
    }

    @Test
    fun 켜져_있고_한도가_1보다_작으면_IllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { OverloadLimit(true, 0, 1000, clockOf(0)) }
    }

    @Test
    fun 켜져_있고_창이_1ms보다_작으면_IllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { OverloadLimit(true, 50, 0, clockOf(0)) }
    }

    @Test
    fun 켜져_있고_한도_1_창_1ms는_받는다() {
        OverloadLimit(true, 1, 1, clockOf(0)).arrive()
    }

    // ---- settings ----

    @Test
    fun 꺼진_설정은_enabled_false_하나다() {
        assertEquals(mapOf<String, Any?>("enabled" to false), OverloadLimit(false, 50, 1000, clockOf(0)).settings())
    }

    @Test
    fun 켠_설정은_떠_있는_값이다() {
        assertEquals(
            mapOf("enabled" to true, "limit" to 50L, "windowMs" to 1000L, "rule" to "sliding-window"),
            OverloadLimit(true, 50, 1000, clockOf(0)).settings().normalized(),
        )
    }

    @Test
    fun 켠_설정은_한도와_창이_바뀌면_따라_바뀐다() {
        assertEquals(
            mapOf("enabled" to true, "limit" to 7L, "windowMs" to 250L, "rule" to "sliding-window"),
            OverloadLimit(true, 7, 250, clockOf(0)).settings().normalized(),
        )
    }

    // ---- 동시에 부를 때: 판정의 차례 = 시각의 오름차순(10-4 ③·④의 전제) ----

    @Test
    fun 동시에_불러도_같은_us_묶음마다의_거절_수가_규칙과_같다() {
        // 시계는 세 번 부를 때마다 1us 나아간다 → 같은 μs 묶음이 생긴다. 서비스가 잠금 안에서 시계를 읽고 판정하면
        // 시각의 오름차순으로 규칙을 다시 적용한 거절 수가 묶음마다 서비스의 거절 수와 같다.
        val ticks = AtomicLong()
        val l = OverloadLimit(true, 5, 1, { ticks.getAndIncrement() / 3 * 37 })
        val got = ConcurrentLinkedQueue<OverloadLimit.Admission>()
        val threads = 8
        val each = 500
        val pool = Executors.newFixedThreadPool(threads)
        val go = CountDownLatch(1)
        repeat(threads) { pool.submit { go.await(); repeat(each) { got += l.arrive() } } }
        go.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))

        val all = got.toList()
        assertEquals(threads * each, all.size)
        val sorted = all.sortedBy { it.atUs }
        val replay = OverloadRule.rejections(sorted.map { it.atUs }, 5, 1000)
        val replayByUs = sorted.indices.groupBy({ sorted[it].atUs }, { replay[it] }).mapValues { (_, v) -> v.count { it } }
        val servedByUs = all.groupBy { it.atUs }.mapValues { (_, v) -> v.count { it.rejected } }
        assertEquals(replayByUs, servedByUs)
        assertEquals(
            mapOf("arrivals" to (threads * each).toLong(), "rejected" to all.count { it.rejected }.toLong()),
            l.counts().normalized(),
        )
        assertTrue(all.any { it.rejected } && all.any { !it.rejected }, "받은 것과 거절한 것이 모두 있어야 시험이 된다")
    }
}
