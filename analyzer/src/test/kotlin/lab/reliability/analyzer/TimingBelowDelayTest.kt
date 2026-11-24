package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 7-3 — edges.<U>-><L>.belowDelay.*: 타임아웃 가운데 L에 넣은 지연(정답의 fault, 없으면 0)이
// timeoutMs 보다 짧은 것과, 그것을 차례대로 처음 맞는 갈래 넷으로 가른 수
// (calleeNotStarted → calleeServiceAtLeastTimeout → calleeEndedAfterGiveUp → calleeEndedBeforeGiveUp),
// 그리고 L의 시각 기록이 있는 것의 start(L) − callStart(U), end(L) − start(L) − 넣은 지연의 분위수.
class TimingBelowDelayTest {
    @TempDir
    lateinit var root: Path

    private fun rec() = TimingRecord(root)

    private val IP = "inventory->payment"
    private val OI = "order->inventory"

    /** [timeouts, calleeNotStarted, calleeServiceAtLeastTimeout, calleeEndedAfterGiveUp, calleeEndedBeforeGiveUp] */
    private fun Map<String, Any?>.below(edge: String) =
        listOf("timeouts", "calleeNotStarted", "calleeServiceAtLeastTimeout", "calleeEndedAfterGiveUp", "calleeEndedBeforeGiveUp")
            .map { long("edges", edge, "belowDelay", it) }

    private fun quant(report: Map<String, Any?>, vararg path: String): List<Double?> =
        listOf("count", "p50", "p90", "p99", "max").map { report.double(*path, it) }

    /**
     * 재고→결제가 요청 타임아웃(재고의 callStart s+4, callEnd s+1004ms)으로 끊겼는데 결제의 입구에 들지 않은 요청 —
     * 결제의 시각 기록·행이 없다. 재고 504, 주문 Unknown:HTTP_504, k6 504.
     */
    private fun TimingRecord.paymentNotStarted(id: String, s: Long) = apply {
        answer(id, s, 504, elapsedMs = 1010)
        timing("order", id, us(s + 1), us(s + 1007), 504, us(s + 2), us(s + 1006), "Unknown:HTTP_504")
        timing("inventory", id, us(s + 3), us(s + 1005), 504, us(s + 4), us(s + 1004), "Unknown:REQUEST_TIMEOUT")
        row("order", id, "UNKNOWN").row("inventory", id, "UNKNOWN")
    }

    // ---- 갈래 — 재고→결제(타임아웃 1000ms, 재고의 callEnd = s+1004ms) ----

    @Test
    fun 결제의_시각_기록이_없으면_calleeNotStarted다() {
        assertEquals(listOf(1L, 1L, 0L, 0L, 0L), rec().paymentNotStarted("a", T).analyze().below(IP))
    }

    @Test
    fun 결제_안의_시간이_꼭_타임아웃이면_calleeServiceAtLeastTimeout이고_뒤_갈래보다_먼저다() {
        // 결제 5~1005ms = 1000ms(≥ 1000). 끝 1005 > 재고의 callEnd 1004 이기도 하다 — 차례대로 처음 맞는 것
        val r = rec().inventoryTimesOut("a", T, paymentStartUs = us(T + 5), paymentEndUs = us(T + 1005)).analyze()
        assertEquals(listOf(1L, 0L, 1L, 0L, 0L), r.below(IP))
    }

    @Test
    fun 결제_안의_시간이_타임아웃보다_짧고_재고가_포기한_뒤에_끝나면_calleeEndedAfterGiveUp이다() {
        // 결제 5ms~(1005ms − 1μs) = 999.999ms(< 1000), 끝 > 재고의 callEnd 1004ms
        val r = rec().inventoryTimesOut("a", T, paymentStartUs = us(T + 5), paymentEndUs = us(T + 1005) - 1).analyze()
        assertEquals(listOf(1L, 0L, 0L, 1L, 0L), r.below(IP))
    }

    @Test
    fun 결제가_재고의_callEnd와_같은_때_끝나면_calleeEndedBeforeGiveUp이다() {
        // 결제 5~1004ms = 999ms, 끝 = 재고의 callEnd(>가 아니다) → 나머지
        val r = rec().inventoryTimesOut("a", T, paymentStartUs = us(T + 5), paymentEndUs = us(T + 1004)).analyze()
        assertEquals(listOf(1L, 0L, 0L, 0L, 1L), r.below(IP))
    }

    // ---- 넣은 지연이 타임아웃보다 짧은 것만 ----

    @Test
    fun 넣은_지연이_꼭_타임아웃이면_belowDelay가_아니다() {
        val r = rec().inventoryTimesOut("a", T, fault = "payment:delay=1000").analyze()
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), r.below(IP))
    }

    @Test
    fun 넣은_지연이_타임아웃보다_1ms_짧으면_belowDelay다() {
        // 결제 5~1500ms = 1495ms ≥ 1000 → calleeServiceAtLeastTimeout
        val r = rec().inventoryTimesOut("a", T, fault = "payment:delay=999").analyze()
        assertEquals(listOf(1L, 0L, 1L, 0L, 0L), r.below(IP))
    }

    @Test
    fun 다른_서비스에_넣은_지연은_L에_넣은_지연이_아니다() {
        // 재고에 5000ms 를 넣었어도 재고→결제 구간의 L(결제)에 넣은 지연은 0 → belowDelay
        val r = rec().inventoryTimesOut("a", T, fault = "inventory:delay=5000").analyze()
        assertEquals(listOf(1L, 0L, 1L, 0L, 0L), r.below(IP))
    }

    @Test
    fun 타임아웃이_없으면_belowDelay는_모두_0() {
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), rec().okChain("a", T).analyze().below(IP))
    }

    // ---- 주문→재고 구간 ----

    @Test
    fun 주문_재고_구간도_같은_규칙으로_가른다() {
        // 주문의 타임아웃 1000ms, 재고 3~1510ms = 1507ms ≥ 1000 → calleeServiceAtLeastTimeout
        val r = rec().apply { orderTimeoutMs = 1000 }.orderTimesOut("a", T).analyze()
        assertEquals(listOf(1L, 0L, 1L, 0L, 0L), r.below(OI))
    }

    // ---- 분위수 ----

    @Test
    fun 부른_때부터_결제의_입구까지의_시간은_start_L_빼기_callStart_U다() {
        // 결제 시작 54ms − 재고의 callStart 4ms = 50ms
        val r = rec().inventoryTimesOut("a", T, paymentStartUs = us(T + 54)).analyze()
        assertEquals(listOf(1.0, 50.0, 50.0, 50.0, 50.0), quant(r, "edges", IP, "belowDelay", "callToCalleeStartMs"))
    }

    @Test
    fun 넣은_지연_밖의_결제_시간은_end_빼기_start_빼기_넣은_지연이다() {
        // 결제 5~1500ms = 1495ms − 넣은 지연 300ms = 1195ms
        val r = rec().inventoryTimesOut("a", T, fault = "payment:delay=300").analyze()
        assertEquals(listOf(1.0, 1195.0, 1195.0, 1195.0, 1195.0), quant(r, "edges", IP, "belowDelay", "calleeServiceBeyondDelayMs"))
    }

    @Test
    fun 분위수에는_결제의_시각_기록이_있는_것만_든다() {
        // a 는 결제에 들지 않았다, b 는 들었다 → 1건
        val r = rec().paymentNotStarted("a", T).inventoryTimesOut("b", T + 100).analyze()
        assertEquals(1, r.long("edges", IP, "belowDelay", "callToCalleeStartMs", "count"))
    }
}
