package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 7-3 — 느린 구간(--slow-label)의 지표: 구간의 시작·끝, 조회의 앞·안·뒤, 사슬 요청의 판정,
// 기준 부하와 회복 시간 ①(표본) ②(클라이언트). 설계 docs/experiments/task-02-design.md 1절(회복 시간)과 4절.
class TimingSlowWindowTest {
    @TempDir
    lateinit var root: Path

    private val SLOW = "payment:delay=5000"

    /**
     * 느린 구간 [T+1000, T+2000]: 앞에 정상 사슬 n0(T)·n1(T+100), 느린 사슬 s0(T+1000)·s1(T+1500)·s2(T+2000),
     * 뒤에 정상 사슬 a0(T+2100). 모두 201. 주문 표본은 T 부터 [until]까지 100ms 간격,
     * [loads]의 시각에 (inflight, queue)를 주고 나머지는 0.
     */
    private fun slow(
        loads: Map<Long, Pair<Int, Int>> = emptyMap(),
        until: Long = T + 3200,
        block: TimingRecord.() -> Unit = {},
    ) = TimingRecord(root).apply {
        okChain("n0", T)
        okChain("n1", T + 100)
        okChain("s0", T + 1000, SLOW)
        okChain("s1", T + 1500, SLOW)
        okChain("s2", T + 2000, SLOW)
        okChain("a0", T + 2100)
        block()
        if (!lookupsGiven) lookup(T + 50).lookup(T + 1200).lookup(T + 2300)
        samples["order"] = grid(T, until).map { TestSample(it, inflight = loads[it]?.first ?: 0, queue = loads[it]?.second ?: 0) }
    }

    private var lookupsGiven = false

    /** 조회를 손으로 준다(기본 조회 셋을 넣지 않는다). */
    private fun TimingRecord.lookups(vararg l: Triple<Long, Int, Number>) {
        lookupsGiven = true
        l.forEach { (at, status, elapsed) -> lookup(at, status, elapsed) }
    }

    private fun TimingRecord.analyzeSlow() = analyze("--slow-label", SLOW)

    private fun Map<String, Any?>.verdicts(vararg path: String) =
        listOf("completed", "failed", "unknown").map { long("slow", *path, it) }

    // ---- 느린 구간 ----

    @Test
    fun 느린_구간은_느린_라벨_정답의_sentAtMs_최소와_최대다() {
        val r = slow().analyzeSlow()
        assertEquals(listOf(T + 1000, T + 2000), listOf(r.long("slow", "startMs"), r.long("slow", "endMs")))
    }

    // ---- 조회 — 앞·안·뒤 ----

    @Test
    fun 조회는_시작보다_이르면_앞_시작과_끝_사이는_양끝을_포함해_안_끝보다_늦으면_뒤다() {
        val r = slow {
            lookups(Triple(T + 999, 200, 5), Triple(T + 1000, 200, 5), Triple(T + 2000, 200, 5), Triple(T + 2001, 200, 5))
        }.analyzeSlow()
        assertEquals(listOf(1L, 2L, 1L), listOf("before", "during", "after").map { r.long("slow", "lookups", it, "count") })
    }

    @Test
    fun 조회의_실패는_200이_아닌_것이고_응답_없음_0도_든다() {
        val r = slow { lookups(Triple(T + 1100, 200, 5), Triple(T + 1200, 0, 20000), Triple(T + 1300, 503, 9)) }.analyzeSlow()
        assertEquals(2, r.long("slow", "lookups", "during", "failed"))
    }

    @Test
    fun 조회의_응답_시간은_구간마다의_분위수다() {
        // 안: 4, 7, 20000 → p50 순위 2 → 7, p90·p99·max 20000
        val r = slow { lookups(Triple(T + 1100, 200, 4), Triple(T + 1200, 200, 20000), Triple(T + 1300, 200, 7)) }.analyzeSlow()
        assertEquals(
            listOf(3.0, 7.0, 20000.0, 20000.0, 20000.0),
            listOf("count", "p50", "p90", "p99", "max").map { r.double("slow", "lookups", "during", "elapsedMs", it) },
        )
    }

    // ---- 사슬 요청의 판정 ----

    private fun verdictMix() = slow {
        // s 에 504 하나 더(결과 모름), 뒤에 500(실패)과 0(결과 모름) 하나씩 — 행·시각 기록 없음
        okChain("s3", T + 1700, SLOW, status = 504)
        answer("a1", T + 2200, 500)
        answer("a2", T + 2300, 0, elapsedMs = 30)
    }

    @Test
    fun 느린_요청의_판정마다의_수() {
        assertEquals(listOf(3L, 0L, 1L), verdictMix().analyzeSlow().verdicts("slowChain"))
    }

    @Test
    fun 느린_구간_앞의_정상_요청의_판정마다의_수() {
        assertEquals(listOf(2L, 0L, 0L), verdictMix().analyzeSlow().verdicts("normalChain", "before"))
    }

    @Test
    fun 느린_구간_뒤의_정상_요청의_판정마다의_수() {
        assertEquals(listOf(1L, 1L, 1L), verdictMix().analyzeSlow().verdicts("normalChain", "after"))
    }

    // ---- 기준 부하와 회복 ① 표본 ----

    @Test
    fun 기준_부하는_시작_전_주문_표본의_inflight_더하기_queue_최대이고_시작_시각의_표본은_들지_않는다() {
        val r = slow(mapOf(T + 500 to (2 to 1), T + 1000 to (50 to 0))).analyzeSlow()
        assertEquals(3, r.long("slow", "baselineOrderLoad"))
    }

    private val base3 = mapOf(T + 500 to (2 to 1), T + 1500 to (80 to 20))

    @Test
    fun 끝난_뒤_곧바로_기준_이하면_표본_회복_시간은_0() {
        assertEquals(0, slow(base3).analyzeSlow().long("slow", "recoveryBySamplesMs"))
    }

    @Test
    fun 끝난_뒤_기준을_넘은_마지막_표본까지가_표본_회복_시간이다() {
        val r = slow(base3 + mapOf(T + 2200 to (9 to 0), T + 2400 to (4 to 0))).analyzeSlow()
        assertEquals(400, r.long("slow", "recoveryBySamplesMs"))
    }

    @Test
    fun 기준과_같은_부하는_기준을_넘은_것이_아니다() {
        assertEquals(0, slow(base3 + mapOf(T + 2300 to (3 to 0))).analyzeSlow().long("slow", "recoveryBySamplesMs"))
    }

    @Test
    fun 표본_회복은_queue도_부하로_더한다() {
        assertEquals(300, slow(base3 + mapOf(T + 2300 to (2 to 2))).analyzeSlow().long("slow", "recoveryBySamplesMs"))
    }

    @Test
    fun 끝_시각의_표본은_끝난_뒤가_아니다() {
        assertEquals(0, slow(base3 + mapOf(T + 2000 to (9 to 0))).analyzeSlow().long("slow", "recoveryBySamplesMs"))
    }

    @Test
    fun 끝까지_회복하지_못하면_마지막_표본_시각_빼기_끝이다() {
        val r = slow(base3 + grid(T + 2100, T + 3200).associateWith { 5 to 0 }).analyzeSlow()
        assertEquals(1200, r.long("slow", "recoveryBySamplesMs"))
    }

    // ---- 회복 ② 클라이언트 ----

    @Test
    fun 끝난_뒤_나쁜_요청이_없으면_클라이언트_회복_시간은_0() {
        assertEquals(0, slow().analyzeSlow().long("slow", "recoveryByClientsMs"))
    }

    @Test
    fun 끝난_뒤_1000ms를_넘긴_조회는_나쁜_요청이다() {
        val r = slow { lookups(Triple(T + 2500, 200, 1001)) }.analyzeSlow()
        assertEquals(500, r.long("slow", "recoveryByClientsMs"))
    }

    @Test
    fun 경과가_꼭_1000ms인_조회는_나쁜_요청이_아니다() {
        val r = slow { lookups(Triple(T + 2500, 200, 1000)) }.analyzeSlow()
        assertEquals(0, r.long("slow", "recoveryByClientsMs"))
    }

    @Test
    fun 응답을_받지_못한_조회는_나쁜_요청이다() {
        val r = slow { lookups(Triple(T + 2400, 0, 20)) }.analyzeSlow()
        assertEquals(400, r.long("slow", "recoveryByClientsMs"))
    }

    @Test
    fun 끝난_뒤_2xx가_아닌_정상_사슬_요청은_나쁜_요청이다() {
        val r = slow { answer("a1", T + 2200, 500) }.analyzeSlow()
        assertEquals(200, r.long("slow", "recoveryByClientsMs"))
    }

    @Test
    fun 끝난_뒤_2xx라도_1000ms를_넘긴_사슬_요청은_나쁜_요청이다() {
        // 경과가 길어 부하 구간의 끝이 T+3301 — 표본을 그 뒤까지 둔다(7-2 의 덮음)
        val r = slow(until = T + 3400) { okChain("a1", T + 2300, elapsedMs = 1000.5) }.analyzeSlow()
        assertEquals(300, r.long("slow", "recoveryByClientsMs"))
    }

    @Test
    fun 끝_시각에_보낸_나쁜_조회는_끝난_뒤가_아니다() {
        val r = slow { lookups(Triple(T + 2000, 0, 20)) }.analyzeSlow()
        assertEquals(0, r.long("slow", "recoveryByClientsMs"))
    }

    @Test
    fun 느린_구간_안의_나쁜_요청은_클라이언트_회복에_들지_않는다() {
        val r = slow {
            lookups(Triple(T + 1500, 0, 20))
            answer("x", T + 1600, 0, elapsedMs = 30)
        }.analyzeSlow()
        assertEquals(0, r.long("slow", "recoveryByClientsMs"))
    }

    @Test
    fun 클라이언트_회복은_나쁜_요청_가운데_마지막_송신_시각으로_잰다() {
        val r = slow {
            lookups(Triple(T + 2600, 0, 20), Triple(T + 2200, 0, 20))
            answer("a1", T + 2400, 500)
        }.analyzeSlow()
        assertEquals(600, r.long("slow", "recoveryByClientsMs"))
    }
}
