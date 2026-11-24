package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// docs/measurement.md 8-4 — 몰림(bins: 50ms·1s 칸, 재시도만의 칸), 구간 장애(outage.*), 회복 ①②, series.
// t0 = 정답의 첫 sentAtMs(여기서는 늘 T, 정답은 보낸 차례로 쓴다). 든 시각 = startUs ÷ 1000.
class RetryBinsOutageTest {
    @TempDir
    lateinit var root: Path

    private val INV = "inventory->payment"

    // ---- 몰림 ----

    /** 결제에 T+5, T+45, T+49, T+50, T+995, T+1000 에 든다(50ms 칸: 0칸 3, 1칸 1 / 1s 칸: 0칸 5, 1칸 1). */
    private fun burst() = RetryRecord(root)
        .okChain("p1", T).okChain("p2", T + 40).okChain("p3", T + 44).okChain("p4", T + 45)
        .okChain("p5", T + 990).okChain("p6", T + 995)

    @Test
    fun 칸의_너비는_50ms다() {
        assertEquals(50L, burst().analyze().long("bins", "binMs"))
    }

    @Test
    fun 오십ms_칸의_꼭대기는_t0부터의_칸마다의_수의_최대다() {
        // T+49 는 0칸, T+50 은 1칸
        assertEquals(3L, burst().analyze().long("bins", "payment", "peakPer50ms"))
    }

    @Test
    fun 일초_칸의_꼭대기는_t0부터_1000ms_칸마다의_수의_최대다() {
        // T+995 는 0칸, T+1000 은 1칸
        assertEquals(5L, burst().analyze().long("bins", "payment", "peakPer1s"))
    }

    @Test
    fun 층마다_따로_든_시각으로_센다() {
        // 재고는 s+3: T+3, T+43, T+47, T+48(0칸 4), T+993, T+998
        val r = burst().analyze()
        assertEquals(4L, r.long("bins", "inventory", "peakPer50ms"))
        assertEquals(6L, r.long("bins", "inventory", "peakPer1s"))
    }

    @Test
    fun 몰림에는_재시도로_든_것도_든다() {
        // t0 = T+30. q1: 시도 1 결제 T+35, 시도 2(간격 100) 결제 T+145. q2 결제 T+145, q3 T+146 → t0+100~t0+150 칸에 셋
        val r = RetryRecord(root)
            .inventoryRetried("q1", T + 30, listOf("Failed:HTTP_500", "Completed"))
            .okChain("q2", T + 140).okChain("q3", T + 141)

        assertEquals(3L, r.analyze().long("bins", "payment", "peakPer50ms"))
    }

    @Test
    fun 재시도만의_50ms_칸의_꼭대기() {
        // 간격 100: 재시도 시작 = s+114 → T+114, T+144, T+149(2칸 [100,150)), T+150(3칸)
        val r = RetryRecord(root)
            .inventoryRetried("q1", T, listOf("Failed:HTTP_500", "Completed"))
            .inventoryRetried("q2", T + 30, listOf("Failed:HTTP_500", "Completed"))
            .inventoryRetried("q3", T + 35, listOf("Failed:HTTP_500", "Completed"))
            .inventoryRetried("q4", T + 36, listOf("Failed:HTTP_500", "Completed"))
        assertEquals(3L, r.analyze().long("edges", INV, "retriesPeakPer50ms"))
    }

    @Test
    fun 재시도가_없으면_재시도만의_칸의_꼭대기는_0이다() {
        assertEquals(0L, burst().analyze().long("edges", INV, "retriesPeakPer50ms"))
    }

    // ---- series ----

    @Test
    fun series의_첫_칸은_t0에서_시작한다() {
        assertEquals(T, burst().analyze().long("series", "originSentAtMs"))
    }

    @Test
    fun series는_100ms_칸마다_결제에_든_수다() {
        // T+5, T+45, T+49, T+50 → 0칸 4, T+995 → 9칸, T+1000 → 10칸
        @Suppress("UNCHECKED_CAST")
        val arrivals = (burst().analyze().at("series", "paymentArrivals") as List<Number>).map { it.toLong() }
        assertTrue(arrivals.size >= 11, "칸이 모자란다: $arrivals")
        assertEquals(listOf(4L, 1L, 1L), listOf(arrivals[0], arrivals[9], arrivals[10]))
        assertEquals(6L, arrivals.sum())
    }

    @Test
    fun series는_재고의_재시도_시작을_100ms_칸마다_센다() {
        // 재시도 시작 T+114, T+144 → 1칸 2
        val r = RetryRecord(root)
            .inventoryRetried("q1", T, listOf("Failed:HTTP_500", "Completed"))
            .inventoryRetried("q2", T + 30, listOf("Failed:HTTP_500", "Completed"))
            .analyze()
        @Suppress("UNCHECKED_CAST")
        val retries = (r.at("series", "inventoryRetries") as List<Number>).map { it.toLong() }
        assertEquals(2L, retries[1])
        assertEquals(2L, retries.sum())
        @Suppress("UNCHECKED_CAST")
        assertEquals(0L, (r.at("series", "orderRetries") as List<Number>).sumOf { it.toLong() })
    }

    // ---- 구간 장애 ----

    private val FROM = T + 1000
    private val TO = T + 2000
    private val OUTAGE = "payment:outage=$FROM-$TO"

    /**
     * 결제에 구간 장애 [T+1000, T+2000), 재고 최대 2·고정 100ms. 모든 정답이 같은 지시.
     * - r1(T) 정상, 결제 T+5(앞).
     * - r2·r5·r7(T+1000·1001·1002) 두 번 모두 500: 결제 T+1005~1007(20칸), 재시도 시작 T+1114~1116, 결제 T+1115~1117(22칸). 502.
     * - r3(T+1950) 시도 1 결제 T+1955(39칸 — 칸 끝 = 구간 끝) 500, 재시도 시작 T+2064, 결제 T+2065 성공. 201.
     * - r4·r6(T+2000·2001) 정상, 결제 T+2005·2006(40칸).
     * 구간 안 결제 도착 7 → 서비스가 센 outage 7.
     */
    private fun outage() = RetryRecord(root)
        .okChain("r1", T, OUTAGE)
        .inventoryRetried("r2", T + 1000, listOf("Failed:HTTP_500", "Failed:HTTP_500"), attemptFault = OUTAGE)
        .inventoryRetried("r5", T + 1001, listOf("Failed:HTTP_500", "Failed:HTTP_500"), attemptFault = OUTAGE)
        .inventoryRetried("r7", T + 1002, listOf("Failed:HTTP_500", "Failed:HTTP_500"), attemptFault = OUTAGE)
        .inventoryRetried("r3", T + 1950, listOf("Failed:HTTP_500", "Completed"), attemptFault = OUTAGE)
        .okChain("r4", T + 2000, OUTAGE)
        .okChain("r6", T + 2001, OUTAGE)
        .counted("payment", "outage", 7)

    @Test
    fun 구간_장애_기록은_통과한다() {
        val r = outage()
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    @Test
    fun 구간의_시작과_끝은_t0부터의_ms다() {
        val r = outage().analyze()
        assertEquals(listOf(1000L, 2000L), listOf(r.long("outage", "startMs"), r.long("outage", "endMs")))
    }

    @Test
    fun 구간_동안_보낸_정답은_from_이상_to_미만이다() {
        // r2(=from)·r5·r7·r3 — r4(=to)는 들지 않는다
        val r = outage().analyze()
        assertEquals(4L, r.long("outage", "requestsSentDuring"))
        assertEquals(1L, r.long("outage", "completedSentDuring"))
    }

    @Test
    fun 구간_동안_불린_쪽에_든_수() {
        val r = outage().analyze()
        assertEquals(7L, r.long("outage", "calleeArrivalsDuring"))
        assertEquals(7L, r.long("attemptFaults", "payment", "outage", "expected"))
    }

    @Test
    fun 불린_쪽의_50ms_칸의_꼭대기를_구간_앞_안_뒤로_나눈다() {
        val r = outage().analyze()
        assertEquals(
            listOf(1L, 3L, 2L),
            listOf("calleePeakPer50msBefore", "calleePeakPer50msDuring", "calleePeakPer50msAfter").map { r.long("outage", it) },
        )
        assertEquals(3L, r.long("bins", "payment", "peakPer50ms"))
        assertEquals(7L, r.long("bins", "payment", "peakPer1s"))
    }

    @Test
    fun 구간_안_재시도만의_칸의_꼭대기는_바로_위_층의_재시도_시작이다() {
        assertEquals(3L, outage().analyze().long("outage", "retriesPeakPer50msDuring"))
    }

    @Test
    fun 회복_1은_재시도_시작의_최대_빼기_구간_끝이다() {
        // 마지막 재시도 시작 T+2064 − T+2000
        assertEquals(64L, outage().analyze().long("outage", "retryTailMs"))
    }

    @Test
    fun 회복_2는_완료가_아닌_정답의_마지막_송신_빼기_구간_끝이다() {
        // r7(T+1002) − T+2000
        assertEquals(-998L, outage().analyze().long("outage", "clientRecoveryMs"))
    }

    /**
     * 경계: 구간 [T+100, T+300), 재고 최대 1(재시도 없음).
     * - p(T+95) 결제 T+100(= from, 든다) 500. q(T+100 = from에 보냄) 결제 T+105 500.
     * - v(T+295) 결제 T+300(= to, 들지 않는다) 성공. w(T+300 = to에 보냄) 성공.
     */
    private fun edges(): RetryRecord {
        val o = "payment:outage=${T + 100}-${T + 300}"
        return RetryRecord(root)
            .inventoryRetried("p", T + 95, listOf("Failed:HTTP_500"), attemptFault = o)
            .inventoryRetried("q", T + 100, listOf("Failed:HTTP_500"), attemptFault = o)
            .okChain("v", T + 295, o)
            .okChain("w", T + 300, o)
            .counted("payment", "outage", 2)
    }

    @Test
    fun 구간의_든_시각은_from_이상_to_미만이다() {
        val r = edges().analyze()
        assertEquals(2L, r.long("outage", "calleeArrivalsDuring"))
        assertEquals(2L, r.long("attemptFaults", "payment", "outage", "expected"))
    }

    @Test
    fun 경계에서_보낸_정답의_수() {
        // q(=from), v — w(=to)·p(앞) 는 들지 않는다
        val r = edges().analyze()
        assertEquals(2L, r.long("outage", "requestsSentDuring"))
        assertEquals(1L, r.long("outage", "completedSentDuring"))
    }

    @Test
    fun 재시도가_없으면_회복_1은_null이다() {
        assertNull(edges().analyze().at("outage", "retryTailMs"))
    }

    @Test
    fun 완료가_아닌_정답이_구간_앞에_보낸_것뿐이면_회복_2는_음수다() {
        // 마지막 실패 q(T+100) − T+300
        assertEquals(-200L, edges().analyze().long("outage", "clientRecoveryMs"))
    }

    @Test
    fun 완료가_아닌_정답이_없으면_회복_2는_null이다() {
        val o = "payment:outage=${T + 5000}-${T + 6000}"
        val r = RetryRecord(root).okChain("a", T, o).okChain("b", T + 100, o).analyze()
        assertNull(r.at("outage", "clientRecoveryMs"))
        assertNull(r.at("outage", "retryTailMs"))
    }

    @Test
    fun 재시도가_구간_끝_앞에_멎으면_회복_1은_음수다() {
        // 구간 [T+0, T+1000): x(T) 재시도 시작 T+114 → 114 − 1000
        val o = "payment:outage=$T-${T + 1000}"
        val r = RetryRecord(root)
            .inventoryRetried("x", T, listOf("Failed:HTTP_500", "Failed:HTTP_500"), attemptFault = o)
            .counted("payment", "outage", 2)
            .analyze()
        assertEquals(-886L, r.long("outage", "retryTailMs"))
    }
}
