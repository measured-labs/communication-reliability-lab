package lab.reliability.chain

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// docs/measurement.md 10-1(규칙)·10-6(테스트 입구) — 한도의 순수 규칙 OverloadWindow(limit L, windowUs W)·OverloadRule.
// 규칙: 시각 t에 든 호출은 (t − W, t] 동안 든 호출의 수(이 호출 포함, 거절한 호출 포함)가 L보다 크면 거절.
// 창의 아래 끝은 열려 있다 — 정확히 W 전에 든 호출은 창 밖이다.
// 이 파일은 구현을 보지 않고 문서(10절, docs/experiments/task-04b-design.md 1·2절)만으로 쓴다.
class OverloadWindowTest {
    /** 문서의 문장을 그대로 옮긴 셈: i번째 호출의 창 = 앞의 모든 호출(거절 포함)과 자기 가운데 시각이 (t − W, t]인 것. */
    private fun byDefinition(arrivals: List<Long>, limit: Int, windowUs: Long): List<Boolean> =
        arrivals.indices.map { i ->
            val t = arrivals[i]
            arrivals.subList(0, i + 1).count { it > t - windowUs && it <= t } > limit
        }

    private fun OverloadWindow.arriveAll(vararg atUs: Long): List<Boolean> = atUs.map { arrive(it) }

    // ---- 한도와 같은 수는 받고 하나 넘으면 거절 ----

    @Test
    fun 창_안의_수가_한도와_같으면_받는다() {
        val w = OverloadWindow(3, 1000)
        assertEquals(listOf(false, false, false), w.arriveAll(0, 1, 2))
        assertEquals(3, w.size())
    }

    @Test
    fun 창_안의_수가_한도를_하나_넘으면_거절한다() {
        val w = OverloadWindow(3, 1000)
        w.arriveAll(0, 1, 2)
        assertTrue(w.arrive(3), "네 번째(창 안 4 > 3)는 거절")
        assertEquals(4, w.size())
    }

    @Test
    fun 한도_1이면_창_안의_두_번째를_거절한다() {
        val w = OverloadWindow(1, 1000)
        assertEquals(listOf(false, true), w.arriveAll(0, 999))
    }

    // ---- 창의 경계: (t − W, t] ----

    @Test
    fun 정확히_W_전에_든_호출은_창_밖이다() {
        // t = 1000 의 창 (0, 1000] 에 0 은 들지 않는다 → 500, 1000 의 둘 = 한도 2 → 받는다
        val w = OverloadWindow(2, 1000)
        assertEquals(listOf(false, false, false), w.arriveAll(0, 500, 1000))
        assertEquals(2, w.size())
    }

    @Test
    fun W보다_1us_덜_전에_든_호출은_창_안이다() {
        // t = 999 의 창 (−1, 999] 에 0 이 든다 → 0, 500, 999 의 셋 > 2 → 거절
        val w = OverloadWindow(2, 1000)
        assertEquals(listOf(false, false, true), w.arriveAll(0, 500, 999))
        assertEquals(3, w.size())
    }

    @Test
    fun 창이_1us이면_같은_시각의_호출만_센다() {
        val w = OverloadWindow(1, 1)
        assertEquals(listOf(false, true, false), w.arriveAll(5, 5, 6))
        assertEquals(1, w.size())
    }

    // ---- 거절한 호출도 센다(되먹임) ----

    @Test
    fun 거절한_호출도_다음_판정에서_센다() {
        // L 2, W 1000, 400us 간격: 0 받음, 400 받음, 800 거절(3).
        // 1200 의 창 (200, 1200] = 400, 800(거절), 1200 = 3 > 2 → 거절. 받은 것만 세면 400, 1200 = 2 로 받았을 것이다.
        val w = OverloadWindow(2, 1000)
        assertEquals(listOf(false, false, true, true), w.arriveAll(0, 400, 800, 1200))
        assertEquals(3, w.size())
    }

    @Test
    fun 비율이_한도를_넘는_동안은_모두_거절되고_창이_비지_않는다() {
        // 설계 1절: 든 호출의 비율이 L/W를 넘는 동안은 모든 호출이 거절된다. 100/s(10ms 간격), L 50, W 1s, 3초 동안.
        // 앞의 50건(0~490ms)만 받는다. 받은 것만 세면 1000ms(창 (0, 1000]에 받은 것 49 + 자기)에서 다시 받았을 것이다.
        val w = OverloadWindow(50, 1_000_000)
        val out = (0 until 300).map { w.arrive(it * 10_000L) }
        assertEquals(List(50) { false } + List(250) { true }, out)
        assertEquals(100, w.size(), "창 (t − 1s, t] 에 10ms 간격으로 100건 — 거절한 것도 창에 남는다")
    }

    // ---- 오래된 도착이 빠지면 다시 받는다 ----

    @Test
    fun 오래된_도착이_창에서_빠지면_다시_받는다() {
        // L 2, W 1000: 0, 1 받음, 2 거절. 1001 의 창 (1, 1001] = 2(거절), 1001 = 2 → 받는다.
        val w = OverloadWindow(2, 1000)
        assertEquals(listOf(false, false, true, false), w.arriveAll(0, 1, 2, 1001))
        assertEquals(2, w.size())
    }

    @Test
    fun 오래된_도착이_빠지기_전에는_아직_거절한다() {
        // 위와 같고 마지막이 1000: 창 (0, 1000] = 1, 2, 1000 = 3 > 2 → 거절.
        val w = OverloadWindow(2, 1000)
        assertEquals(listOf(false, false, true, true), w.arriveAll(0, 1, 2, 1000))
    }

    @Test
    fun 오래_비면_창의_수는_자기_하나다() {
        val w = OverloadWindow(2, 1000)
        w.arriveAll(0, 1, 2, 3)
        assertFalse(w.arrive(10_000))
        assertEquals(1, w.size())
    }

    // ---- 같은 시각에 든 여럿 ----

    @Test
    fun 같은_시각에_든_여럿은_차례대로_하나씩_센다() {
        val w = OverloadWindow(2, 1000)
        assertEquals(listOf(false, false, true, true), w.arriveAll(7, 7, 7, 7))
        assertEquals(4, w.size())
    }

    @Test
    fun 앞_호출과_같은_시각은_받는다() {
        val w = OverloadWindow(5, 1000)
        w.arrive(100)
        w.arrive(100) // 예외가 아니다
        assertEquals(2, w.size())
    }

    // ---- 크기, 입력의 검사 ----

    @Test
    fun 넣은_것이_없으면_크기는_0이다() {
        assertEquals(0, OverloadWindow(3, 1000).size())
    }

    @Test
    fun 앞_호출보다_이른_시각은_IllegalArgumentException() {
        val w = OverloadWindow(3, 1000)
        w.arrive(100)
        assertFailsWith<IllegalArgumentException> { w.arrive(99) }
    }

    @Test
    fun 한도가_1보다_작으면_IllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { OverloadWindow(0, 1000) }
    }

    @Test
    fun 창이_1us보다_작으면_IllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { OverloadWindow(3, 0) }
    }

    @Test
    fun 한도_1_창_1us는_받는다() {
        val w = OverloadWindow(1, 1)
        assertEquals(1, w.limit)
        assertEquals(1L, w.windowUs)
    }

    // ---- OverloadRule — 오름차순의 든 시각 → 같은 차례의 거절 여부 ----

    @Test
    fun 규칙은_빈_목록에_빈_목록을_낸다() {
        assertEquals(emptyList(), OverloadRule.rejections(emptyList(), 3, 1000))
    }

    @Test
    fun 규칙은_빈_창에서_시작한다() {
        // 같은 목록을 두 번 불러도 같은 답 — 앞 부름의 창이 남지 않는다
        val arr = listOf(0L, 1, 2, 3)
        assertEquals(listOf(false, false, false, true), OverloadRule.rejections(arr, 3, 1000))
        assertEquals(listOf(false, false, false, true), OverloadRule.rejections(arr, 3, 1000))
    }

    @Test
    fun 규칙은_경계와_되먹임을_창과_같게_판정한다() {
        assertEquals(listOf(false, false, false), OverloadRule.rejections(listOf(0L, 500, 1000), 2, 1000))
        assertEquals(listOf(false, false, true, true), OverloadRule.rejections(listOf(0L, 400, 800, 1200), 2, 1000))
        assertEquals(listOf(false, false, true, false), OverloadRule.rejections(listOf(0L, 1, 2, 1001), 2, 1000))
    }

    @Test
    fun 설계의_손계산_재시도_없이_구간_뒤_약_0_67초에_거절이_멈춘다() {
        // 설계 2절: 평소 25/s, 구간 100/s, L 50, W 1s. 구간이 끝난 뒤 x초에 창 ≈ 100(1 − x) + 25x ≤ 50 → x = 2/3.
        // 고른 도착으로: 0~10s 평소(40ms 간격), 10~15s 구간(10ms 간격), 15~20s 평소(40ms 간격). 마지막 거절은 15s + 약 0.67s.
        val arr = mutableListOf<Long>()
        var t = 0L
        while (t < 10_000_000) { arr += t; t += 40_000 }
        while (t < 15_000_000) { arr += t; t += 10_000 }
        while (t < 20_000_000) { arr += t; t += 40_000 }
        val rej = OverloadRule.rejections(arr, 50, 1_000_000)
        assertEquals(byDefinition(arr, 50, 1_000_000), rej)
        val lastRejectedUs = arr.indices.last { rej[it] }
        val stopAfterEndMs = (arr[lastRejectedUs] - 15_000_000) / 1000
        assertTrue(stopAfterEndMs in 620..700, "구간 끝 뒤 마지막 거절 ${stopAfterEndMs}ms — 손계산 ≈ 667")
        assertTrue(rej.subList(0, 250).none { it }, "평소 25/s 동안은 거절이 없다")
    }

    @Test
    fun 규칙은_정의대로_센_것과_같다_무작위_도착() {
        // 같은 시각 묶음이 생기게 작은 간격(0~3us)으로, 한도·창을 바꿔 가며
        val rnd = Random(20261125)
        repeat(200) {
            val limit = 1 + rnd.nextInt(6)
            val windowUs = 1L + rnd.nextInt(20)
            var t = rnd.nextLong(0, 1000)
            val arr = List(1 + rnd.nextInt(60)) { t += rnd.nextInt(4); t }
            assertEquals(byDefinition(arr, limit, windowUs), OverloadRule.rejections(arr, limit, windowUs), "L $limit W $windowUs $arr")
            val w = OverloadWindow(limit, windowUs)
            assertEquals(byDefinition(arr, limit, windowUs), arr.map { w.arrive(it) }, "창: L $limit W $windowUs $arr")
            val last = arr.last()
            assertEquals(arr.count { it > last - windowUs }, w.size(), "크기: L $limit W $windowUs $arr")
        }
    }
}
