package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

// docs/measurement.md 7-3 — 표본 지표(samples.<서비스>.*)와 7-4 의 늘 견주는 것(maxGapMs ≤ 1000).
class TimingSamplesTest {
    @TempDir
    lateinit var root: Path

    /**
     * 주문 표본을 손으로 준 기록. 사슬 요청 둘(T, T+400 — 보내는 구간 [T, T+400], 마지막 끝 T+420).
     * 간격이 고르지 않다(100, 100, 250, 50, 100). 최대 스레드 4. 손으로 센 값:
     * - 보내는 구간 안의 표본: T, T+100, T+350, T+400 → inflight+queue = 1, 3, 4, 2 → 평균 2.5
     * - maxInflight 60(T+500), maxActive 4, maxThreads 4, maxQueue 1, maxConnections 7, maxGapMs 250
     * - active ≥ maxThreads 인 표본: T−100, T+100, T+500 → 3 × intervalMs 100 = 300
     */
    private fun uneven() = TimingRecord(root).apply {
        okChain("a", T)
        okChain("b", T + 400)
        samples["order"] = listOf(
            TestSample(T - 100, inflight = 50, active = 4, maxThreads = 4, queue = 0, connections = 2),
            TestSample(T, inflight = 1, active = 3, maxThreads = 4, queue = 0, connections = 3),
            TestSample(T + 100, inflight = 2, active = 4, maxThreads = 4, queue = 1, connections = 7),
            TestSample(T + 350, inflight = 4, active = 2, maxThreads = 4, queue = 0, connections = 4),
            TestSample(T + 400, inflight = 2, active = 0, maxThreads = 4, queue = 0, connections = 1),
            TestSample(T + 500, inflight = 60, active = 4, maxThreads = 4, queue = 0, connections = 1),
        )
    }

    @Test
    fun 처리_중인_요청_수의_최대() {
        assertEquals(60, uneven().analyze().long("samples", "order", "maxInflight"))
    }

    @Test
    fun 바쁜_스레드의_최대() {
        assertEquals(4, uneven().analyze().long("samples", "order", "maxActive"))
    }

    @Test
    fun 최대_스레드_수의_최대() {
        assertEquals(4, uneven().analyze().long("samples", "order", "maxThreads"))
    }

    @Test
    fun 큐의_최대() {
        assertEquals(1, uneven().analyze().long("samples", "order", "maxQueue"))
    }

    @Test
    fun 연결_수의_최대() {
        assertEquals(7, uneven().analyze().long("samples", "order", "maxConnections"))
    }

    @Test
    fun 이웃한_표본_간격의_최대() {
        assertEquals(250, uneven().analyze().long("samples", "order", "maxGapMs"))
    }

    @Test
    fun 꽉_찬_시간은_active가_maxThreads_이상인_표본_수_곱하기_intervalMs다_간격이_고르지_않아도() {
        assertEquals(300, uneven().analyze().long("samples", "order", "saturatedMs"))
    }

    @Test
    fun 꽉_찬_시간은_intervalMs를_곱한다() {
        val r = uneven().apply { intervalMs["order"] = 250 }.analyze()
        assertEquals(750, r.long("samples", "order", "saturatedMs"))
    }

    @Test
    fun 보내는_동안의_평균_부하는_첫_송신과_마지막_송신_사이_양끝을_포함한_표본의_inflight_더하기_queue_평균() {
        assertEquals(2.5, uneven().analyze().double("samples", "order", "meanLoadDuringSend")!!, 1e-9)
    }

    @Test
    fun 표본_지표는_서비스마다_따로다() {
        // 재고 표본에만 inflight 9 — 주문(위 표본)의 최대 60 과 섞이지 않는다
        val r = uneven().apply {
            samples["inventory"] = grid(T, T + 420).map { TestSample(it, inflight = if (it == T + 200) 9 else 0) }
        }.analyze()
        assertEquals(listOf(60L, 9L, 0L), LAYERS.map { r.long("samples", it, "maxInflight") })
    }

    // ---- 가상 스레드 — 실행기 칸이 null ----

    private fun virtual() = TimingRecord(root).apply {
        okChain("a", T)
        okChain("b", T + 400)
        samples["order"] = grid(T, T + 420).map {
            TestSample(it, inflight = if (it == T + 200) 6 else 2, active = null, poolSize = null, maxThreads = null, queue = null)
        }
    }

    @Test
    fun 가상_스레드면_꽉_찬_시간은_null() {
        assertNull(virtual().analyze().at("samples", "order", "saturatedMs"))
    }

    @Test
    fun 가상_스레드면_바쁜_스레드와_큐의_최대는_null() {
        val r = virtual().analyze()
        assertEquals(listOf(null, null, null), listOf("maxActive", "maxQueue", "maxThreads").map { r.at("samples", "order", it) })
    }

    @Test
    fun 큐가_null이면_평균_부하에서_0으로_본다() {
        // 보내는 구간 [T, T+400]: T, T+100, T+200(6), T+300, T+400 → (2+2+6+2+2)/5 = 2.8
        assertEquals(2.8, virtual().analyze().double("samples", "order", "meanLoadDuringSend")!!, 1e-9)
    }

    // ---- 표본이 하나 ----

    @Test
    fun 표본이_하나면_최대는_그_표본의_값이다() {
        // 요청 하나(T, 경과 20) — 표본 하나 T+100 이 부하 구간을 덮는다(첫 ≤ T+200, 마지막 ≥ T+20)
        val r = TimingRecord(root).apply {
            okChain("a", T)
            samples["order"] = listOf(TestSample(T + 100, inflight = 3, active = 2, queue = 1, connections = 5))
        }.analyzeAny()
        assertEquals(listOf(3L, 2L, 1L, 5L), listOf("maxInflight", "maxActive", "maxQueue", "maxConnections").map { r.long("samples", "order", it) })
    }

    // ---- 늘 견주는 것: 표본 간격 ≤ 1000ms ----

    private fun gap(ms: Long) = TimingRecord(root).apply {
        okChain("a", T)
        okChain("b", T + 1500)
        samples["order"] = listOf(TestSample(T), TestSample(T + ms), TestSample(T + 1600))
    }

    @Test
    fun 표본_간격이_1000ms면_통과한다() {
        assertEquals(0, gap(1000).runTiming())
    }

    @Test
    fun 표본_간격이_1000ms를_넘으면_종료_코드_2() {
        assertEquals(2, gap(1001).runTiming())
    }

    @Test
    fun 표본_간격이_1000ms를_넘으면_문서의_꼴로_차이를_적는다() {
        val r = gap(1001)
        r.runTiming()
        assertEquals(listOf("samples.order.maxGapMs: 늘 1000 이하, 실제 1001"), r.report().differences())
    }

    // ---- 표본이 하나(maxGapMs null) — 7-3·7-4 ----

    /**
     * 요청 하나(T, 경과 0 — 부하 구간 [T, T]). 주문 표본은 T 하나 — 덮고, 보내는 창 [T, T] 안에도 든다(평균 부하는 있다).
     * 재고·결제는 T, T+100 둘.
     */
    private fun oneSample() = TimingRecord(root).apply {
        okChain("a", T, elapsedMs = 0)
        samples["order"] = listOf(TestSample(T))
        samples["inventory"] = listOf(TestSample(T), TestSample(T + 100))
        samples["payment"] = listOf(TestSample(T), TestSample(T + 100))
    }

    @Test
    fun 표본이_하나면_최대_간격은_null이다() {
        assertNull(oneSample().analyzeAny().at("samples", "order", "maxGapMs"))
    }

    @Test
    fun 표본이_하나면_종료_코드_2() {
        assertEquals(2, oneSample().runTiming())
    }

    @Test
    fun 표본이_하나면_문서의_꼴로_차이_한_줄을_적는다() {
        assertEquals(listOf("samples.order.maxGapMs: 표본이 하나라 간격을 잴 수 없다"), oneSample().analyzeAny().differences())
    }

    // ---- 보내는 동안 표본이 없음(meanLoadDuringSend null) — 7-3·7-4 ----

    /** 사슬 요청 둘(T, T+50 — 보내는 창 [T, T+50], 부하 구간의 끝 T+70). 주문 표본은 T−10, T+80 — 덮지만 창 안에는 없다. */
    private fun noneDuringSend() = TimingRecord(root).apply {
        okChain("a", T)
        okChain("b", T + 50)
        samples["order"] = listOf(TestSample(T - 10), TestSample(T + 80))
    }

    @Test
    fun 보내는_동안의_표본이_없으면_평균_부하는_null이다() {
        assertNull(noneDuringSend().analyzeAny().at("samples", "order", "meanLoadDuringSend"))
    }

    @Test
    fun 보내는_동안의_표본이_없으면_종료_코드_2() {
        assertEquals(2, noneDuringSend().runTiming())
    }

    @Test
    fun 보내는_동안의_표본이_없으면_문서의_꼴로_차이_한_줄을_적는다() {
        assertEquals(listOf("samples.order.meanLoadDuringSend: 보내는 동안의 표본이 없다"), noneDuringSend().analyzeAny().differences())
    }

    @Test
    fun 늘_견주는_차이는_서비스마다_간격_다음_평균_부하이고_서비스는_주문_재고_결제_차례다() {
        // 보내는 창 [T, T+1500], 부하 구간의 끝 T+1520. 주문·재고 표본 T−10, T+1530 — 간격 1540, 창 안에 표본 없음
        val r = TimingRecord(root).apply {
            okChain("a", T)
            okChain("b", T + 1500)
            samples["inventory"] = listOf(TestSample(T - 10), TestSample(T + 1530))
            samples["order"] = listOf(TestSample(T - 10), TestSample(T + 1530))
        }
        assertEquals(
            listOf(
                "samples.order.maxGapMs: 늘 1000 이하, 실제 1540",
                "samples.order.meanLoadDuringSend: 보내는 동안의 표본이 없다",
                "samples.inventory.maxGapMs: 늘 1000 이하, 실제 1540",
                "samples.inventory.meanLoadDuringSend: 보내는 동안의 표본이 없다",
            ),
            r.analyzeAny().differences(),
        )
    }
}
