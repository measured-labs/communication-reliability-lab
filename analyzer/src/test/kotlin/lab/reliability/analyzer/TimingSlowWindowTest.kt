package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

// docs/measurement.md 7-3 — 느린 구간(--slow-label)의 지표: 구간의 시작·끝, 조회의 앞·안·뒤, 사슬 요청의 판정,
// 기준 부하와 회복 ①(표본) ②(클라이언트) — 회복했는지(불)와 회복 시간(회복하지 못했으면 null), 끝난 뒤의 나쁜 요청(badAfter).
// 설계 docs/experiments/task-02-design.md 1절(회복 시간)과 4절.
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

    @Test
    fun 조회의_분위수에는_실패한_조회도_넣는다() {
        // 안: 200(4) · 0(20000) · 503(9) → 4, 9, 20000. p50 순위 (500·3+999)/1000 = 2 → 9, p90 순위 3 → 20000
        val r = slow { lookups(Triple(T + 1100, 200, 4), Triple(T + 1200, 0, 20000), Triple(T + 1300, 503, 9)) }.analyzeSlow()
        assertEquals(
            listOf(3.0, 9.0, 20000.0, 20000.0, 20000.0),
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

    // ---- 정상 요청 — 느린 구간 안(양끝 포함) ----

    /** 느린 구간 [T+1000, T+2000] 안에 정상 요청 셋: m0(시작 시각, 201), m1(T+1500, 500), m2(끝 시각, 504). */
    private fun normalDuring() = slow {
        okChain("m0", T + 1000)
        answer("m1", T + 1500, 500)
        answer("m2", T + 2000, 504)
    }

    @Test
    fun 느린_구간_안의_정상_요청은_양끝을_포함해_판정마다_센다() {
        assertEquals(listOf(1L, 1L, 1L), normalDuring().analyzeSlow().verdicts("normalChain", "during"))
    }

    @Test
    fun 느린_구간_시작_시각의_정상_요청은_앞에_들지_않는다() {
        assertEquals(listOf(2L, 0L, 0L), normalDuring().analyzeSlow().verdicts("normalChain", "before"))
    }

    @Test
    fun 느린_구간_끝_시각의_정상_요청은_뒤에_들지_않는다() {
        assertEquals(listOf(1L, 0L, 0L), normalDuring().analyzeSlow().verdicts("normalChain", "after"))
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

    // 7-3(바뀜): 마지막 주문 표본이 끝 뒤이고 기준을 넘으면 회복하지 못한 것 — 회복 시간은 값이 아니라 null.
    private val neverBack = base3 + grid(T + 2100, T + 3200).associateWith { 5 to 0 }

    @Test
    fun 끝까지_회복하지_못하면_표본_회복_시간은_null이다() {
        assertNull(slow(neverBack).analyzeSlow().at("slow", "recoveryBySamplesMs"))
    }

    @Test
    fun 마지막_표본이_기준을_넘으면_표본으로는_회복하지_못했다() {
        assertEquals(false, slow(neverBack).analyzeSlow().at("slow", "recoveredBySamples"))
    }

    @Test
    fun 끝나기_전에_기준으로_돌아오면_표본으로_회복했다() {
        assertEquals(true, slow(base3).analyzeSlow().at("slow", "recoveredBySamples"))
    }

    /** 끝 뒤 T+2100~T+3100 은 5, 마지막 표본 T+3200 은 꼭 기준(3) — 기준 이하이므로 회복했다. */
    private val backAtLast = base3 + grid(T + 2100, T + 3100).associateWith { 5 to 0 } + mapOf(T + 3200 to (3 to 0))

    @Test
    fun 마지막_표본이_꼭_기준이면_표본으로_회복했다() {
        assertEquals(true, slow(backAtLast).analyzeSlow().at("slow", "recoveredBySamples"))
    }

    @Test
    fun 마지막_표본이_꼭_기준이면_회복_시간은_기준을_넘은_마지막_표본까지다() {
        // 기준을 넘은 마지막 표본 T+3100 − 끝 T+2000
        assertEquals(1100, slow(backAtLast).analyzeSlow().long("slow", "recoveryBySamplesMs"))
    }

    /**
     * 끝 뒤에 보낸 것이 없는 기록: 정상 n0(T)·n1(T+100), 느린 s0(T+1000)·s1(T+2000, 경과 0 — 부하 구간의 끝 = 느린 구간의 끝),
     * 조회는 T+50·T+1200. 주문 표본은 T~T+2000(마지막이 끝 시각), 기준 3(T+500), 끝 시각 T+2000 의 표본은 50.
     */
    private fun nothingAfter() = TimingRecord(root).apply {
        okChain("n0", T)
        okChain("n1", T + 100)
        okChain("s0", T + 1000, SLOW)
        okChain("s1", T + 2000, SLOW, elapsedMs = 0)
        lookup(T + 50).lookup(T + 1200)
        samples["order"] = grid(T, T + 2000).map {
            when (it) {
                T + 500 -> TestSample(it, inflight = 2, queue = 1)
                T + 2000 -> TestSample(it, inflight = 50)
                else -> TestSample(it)
            }
        }
    }

    @Test
    fun 마지막_표본이_끝_시각이면_부하가_높아도_표본으로_회복했다() {
        assertEquals(true, nothingAfter().analyzeSlow().at("slow", "recoveredBySamples"))
    }

    @Test
    fun 끝_뒤의_표본이_없으면_표본_회복_시간은_0() {
        assertEquals(0, nothingAfter().analyzeSlow().long("slow", "recoveryBySamplesMs"))
    }

    // ---- 회복 ② 클라이언트 ----

    @Test
    fun 끝난_뒤_나쁜_요청이_없으면_클라이언트_회복_시간은_0() {
        assertEquals(0, slow().analyzeSlow().long("slow", "recoveryByClientsMs"))
    }

    // 7-3(바뀜): 마지막 나쁜 요청의 sentAtMs 이상에 보낸 좋은 요청이 있어야 회복했다 — 그래서 아래의 나쁜 요청 테스트는
    // 나쁜 요청 뒤에 좋은 조회 하나(T+2800)를 둔다. 없을 때는 …_회복하지_못했다 테스트가 본다.
    private val goodLater = Triple<Long, Int, Number>(T + 2800, 200, 5)

    @Test
    fun 끝난_뒤_1000ms를_넘긴_조회는_나쁜_요청이다() {
        val r = slow { lookups(Triple(T + 2500, 200, 1001), goodLater) }.analyzeSlow()
        assertEquals(500, r.long("slow", "recoveryByClientsMs"))
    }

    @Test
    fun 경과가_꼭_1000ms인_조회는_나쁜_요청이_아니다() {
        val r = slow { lookups(Triple(T + 2500, 200, 1000)) }.analyzeSlow()
        assertEquals(0, r.long("slow", "recoveryByClientsMs"))
    }

    @Test
    fun 응답을_받지_못한_조회는_나쁜_요청이다() {
        val r = slow { lookups(Triple(T + 2400, 0, 20), goodLater) }.analyzeSlow()
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
            lookups(Triple(T + 2600, 0, 20), Triple(T + 2200, 0, 20), goodLater)
            answer("a1", T + 2400, 500)
        }.analyzeSlow()
        assertEquals(600, r.long("slow", "recoveryByClientsMs"))
    }

    // ---- 회복 ② — 회복했는지(불) ----

    /** 끝 뒤의 나쁜 조회 T+2500. 그 앞의 좋은 요청은 a0(T+2100)뿐. */
    private fun lastIsBad() = slow { lookups(Triple(T + 2500, 0, 20)) }

    @Test
    fun 마지막_나쁜_요청_뒤에_좋은_요청이_없으면_클라이언트로는_회복하지_못했다() {
        assertEquals(false, lastIsBad().analyzeSlow().at("slow", "recoveredByClients"))
    }

    @Test
    fun 마지막_나쁜_요청_뒤에_좋은_요청이_없으면_클라이언트_회복_시간은_null이다() {
        assertNull(lastIsBad().analyzeSlow().at("slow", "recoveryByClientsMs"))
    }

    @Test
    fun 마지막_나쁜_요청과_같은_시각에_보낸_좋은_요청이_있으면_회복했다() {
        val r = slow { lookups(Triple(T + 2500, 0, 20), Triple(T + 2500, 200, 5)) }.analyzeSlow()
        assertEquals(true, r.at("slow", "recoveredByClients"))
    }

    @Test
    fun 끝난_뒤_나쁜_요청이_없으면_클라이언트로_회복했다() {
        assertEquals(true, slow().analyzeSlow().at("slow", "recoveredByClients"))
    }

    @Test
    fun 끝_뒤에_보낸_요청이_없으면_클라이언트로는_회복하지_못했다() {
        assertEquals(false, nothingAfter().analyzeSlow().at("slow", "recoveredByClients"))
    }

    @Test
    fun 끝_뒤에_보낸_요청이_없으면_클라이언트_회복_시간은_null이다() {
        assertNull(nothingAfter().analyzeSlow().at("slow", "recoveryByClientsMs"))
    }

    // ---- 끝난 뒤의 나쁜 요청 — 까닭마다(badAfter) ----

    private fun Map<String, Any?>.badAfter() =
        listOf("chainNot2xx", "chainSlow2xx", "lookupsNot200", "lookupsSlow200").map { long("slow", "badAfter", it) }

    @Test
    fun 끝난_뒤_2xx가_아닌_사슬_요청은_chainNot2xx다() {
        assertEquals(listOf(1L, 0L, 0L, 0L), slow { answer("a1", T + 2200, 500) }.analyzeSlow().badAfter())
    }

    @Test
    fun 끝난_뒤_응답을_받지_못한_사슬_요청은_chainNot2xx다() {
        assertEquals(listOf(1L, 0L, 0L, 0L), slow { answer("a1", T + 2200, 0, elapsedMs = 30) }.analyzeSlow().badAfter())
    }

    @Test
    fun 끝난_뒤_2xx가_아니고_느리기도_한_사슬_요청은_chainNot2xx로만_센다() {
        // 경과 1500 → 부하 구간의 끝 T+3700 — 표본을 그 뒤까지
        val r = slow(until = T + 3800) { answer("a1", T + 2200, 500, elapsedMs = 1500) }.analyzeSlow()
        assertEquals(listOf(1L, 0L, 0L, 0L), r.badAfter())
    }

    @Test
    fun 끝난_뒤_1000ms를_넘긴_2xx_사슬_요청은_chainSlow2xx다() {
        val r = slow(until = T + 3400) { okChain("a1", T + 2300, elapsedMs = 1000.5) }.analyzeSlow()
        assertEquals(listOf(0L, 1L, 0L, 0L), r.badAfter())
    }

    @Test
    fun 끝난_뒤_경과가_꼭_1000ms인_2xx_사슬_요청은_나쁘지_않다() {
        val r = slow(until = T + 3400) { okChain("a1", T + 2300, elapsedMs = 1000) }.analyzeSlow()
        assertEquals(listOf(0L, 0L, 0L, 0L), r.badAfter())
    }

    @Test
    fun 끝난_뒤_200이_아닌_조회는_lookupsNot200이다() {
        assertEquals(listOf(0L, 0L, 1L, 0L), slow { lookups(Triple(T + 2500, 503, 9)) }.analyzeSlow().badAfter())
    }

    @Test
    fun 끝난_뒤_응답을_받지_못한_조회는_lookupsNot200이다() {
        assertEquals(listOf(0L, 0L, 1L, 0L), slow { lookups(Triple(T + 2500, 0, 20)) }.analyzeSlow().badAfter())
    }

    @Test
    fun 끝난_뒤_1000ms를_넘긴_200_조회는_lookupsSlow200이다() {
        assertEquals(listOf(0L, 0L, 0L, 1L), slow { lookups(Triple(T + 2500, 200, 1001)) }.analyzeSlow().badAfter())
    }

    @Test
    fun 끝_시각에_보낸_나쁜_요청은_badAfter에_들지_않는다() {
        val r = slow {
            lookups(Triple(T + 2000, 0, 20))
            answer("x", T + 2000, 500)
        }.analyzeSlow()
        assertEquals(listOf(0L, 0L, 0L, 0L), r.badAfter())
    }
}
