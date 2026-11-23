package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 7-3 — 위 층이 포기한 시각 giveUp(L, id)와 헛일(waste).
// giveUp(L) = 가장 이른 것: k6 status 0 이면 sentAtMs + elapsedMs · L보다 위의 층 U마다 U의 callOutcome 이
// Unknown:REQUEST_TIMEOUT 이면 U의 callEnd. 헛일 = end > giveUp, 시간 = end − max(start, giveUp).
// giveUp 은 보고에 따로 나오지 않으므로 헛일 시간(waste.<L>.wastedMs)으로 본다.
class TimingWasteTest {
    @TempDir
    lateinit var root: Path

    private fun rec() = TimingRecord(root)

    private fun quant(report: Map<String, Any?>, vararg path: String): List<Double?> =
        listOf("count", "p50", "p90", "p99", "max").map { report.double(*path, it) }

    private fun wastedMsMax(r: Map<String, Any?>, layer: String) = r.double("waste", layer, "wastedMs", "max")!!

    // ---- 헛일 — 건수, 처리 행, 시간 ----

    @Test
    fun 재고가_포기한_뒤에_끝난_결제는_헛일이다() {
        val r = rec().inventoryTimesOut("a", T).analyze()
        assertEquals(1, r.long("waste", "payment", "wasted"))
    }

    @Test
    fun 헛일_시간은_끝_빼기_포기한_시각이다() {
        // 결제 5~1500ms, 재고의 callEnd(타임아웃) 1004ms → 496ms
        val r = rec().inventoryTimesOut("a", T).analyze()
        assertEquals(496.0, wastedMsMax(r, "payment"), 1e-9)
    }

    @Test
    fun 헛일_가운데_그_층에_처리_행이_있는_것만_wastedProcessed로_센다() {
        val r = rec()
            .inventoryTimesOut("a", T)
            .inventoryTimesOut("b", T + 100, paymentProcessed = false)
            .analyze()
        assertEquals(listOf(2L, 1L), listOf(r.long("waste", "payment", "wasted"), r.long("waste", "payment", "wastedProcessed")))
    }

    @Test
    fun 끝이_포기한_시각과_같으면_헛일이_아니다() {
        val r = rec().inventoryTimesOut("a", T, paymentEndUs = us(T + 1004)).analyze()
        assertEquals(0, r.long("waste", "payment", "wasted"))
    }

    @Test
    fun 끝이_포기한_시각보다_1마이크로초_늦으면_헛일이고_시간은_0_001ms() {
        val r = rec().inventoryTimesOut("a", T, paymentEndUs = us(T + 1004) + 1).analyze()
        assertEquals(listOf(1.0, 0.001), listOf(r.long("waste", "payment", "wasted").toDouble(), wastedMsMax(r, "payment")))
    }

    // ---- 포기한 뒤에 시작한 일 ----

    @Test
    fun 포기하기_전에_시작한_일은_startedAfterGiveUp이_아니다() {
        val r = rec().inventoryTimesOut("a", T).analyze()
        assertEquals(listOf(1L, 0L), listOf(r.long("waste", "payment", "wasted"), r.long("waste", "payment", "startedAfterGiveUp")))
    }

    @Test
    fun 포기한_시각에_시작한_일은_startedAfterGiveUp이다() {
        val r = rec().inventoryTimesOut("a", T, paymentStartUs = us(T + 1004), paymentEndUs = us(T + 1300)).analyze()
        assertEquals(1, r.long("waste", "payment", "startedAfterGiveUp"))
    }

    @Test
    fun 포기한_뒤에_시작한_일의_헛일_시간은_끝_빼기_시작이다() {
        // 포기 1004ms, 결제 1100~1300ms → max(start, giveUp) = 1100 → 200ms (end − giveUp 이면 296)
        val r = rec().inventoryTimesOut("a", T, paymentStartUs = us(T + 1100), paymentEndUs = us(T + 1300)).analyze()
        assertEquals(200.0, wastedMsMax(r, "payment"), 1e-9)
    }

    // ---- giveUp — 셋 가운데 가장 이른 것 ----

    /**
     * 셋이 모두 포기한 요청: k6 status 0(경과 [k6ElapsedMs]), 주문→재고 타임아웃(callEnd [orderCallEndMs]),
     * 재고→결제 타임아웃(callEnd [inventoryCallEndMs]). 결제는 5~3000ms 에 처리(APPROVED).
     */
    private fun allGaveUp(orderCallEndMs: Long, inventoryCallEndMs: Long, k6ElapsedMs: Long) = rec()
        .answer("a", T, 0, elapsedMs = k6ElapsedMs)
        .timing("order", "a", us(T + 1), us(T + orderCallEndMs + 1), 504, us(T + 2), us(T + orderCallEndMs), "Unknown:REQUEST_TIMEOUT")
        .timing("inventory", "a", us(T + 3), us(T + inventoryCallEndMs + 1), 504, us(T + 4), us(T + inventoryCallEndMs), "Unknown:REQUEST_TIMEOUT")
        .timing("payment", "a", us(T + 5), us(T + 3000), 201)
        .row("order", "a", "UNKNOWN").row("inventory", "a", "UNKNOWN").row("payment", "a", "APPROVED")

    @Test
    fun 재고의_타임아웃이_가장_이르면_그것이_결제의_포기_시각이다() {
        val r = allGaveUp(orderCallEndMs = 2003, inventoryCallEndMs = 1004, k6ElapsedMs = 2500).analyze()
        assertEquals(3000.0 - 1004, wastedMsMax(r, "payment"), 1e-9)
    }

    @Test
    fun 주문의_타임아웃이_가장_이르면_그것이_결제의_포기_시각이다() {
        val r = allGaveUp(orderCallEndMs = 800, inventoryCallEndMs = 1004, k6ElapsedMs = 2500).analyze()
        assertEquals(3000.0 - 800, wastedMsMax(r, "payment"), 1e-9)
    }

    @Test
    fun k6가_가장_이르면_그것이_결제의_포기_시각이다() {
        val r = allGaveUp(orderCallEndMs = 2003, inventoryCallEndMs = 1004, k6ElapsedMs = 500).analyze()
        assertEquals(3000.0 - 500, wastedMsMax(r, "payment"), 1e-9)
    }

    @Test
    fun 아무도_포기하지_않았으면_오래_걸려도_헛일이_아니다() {
        // 모두 Completed, k6 201(경과 3100ms) — 결제 5~3000ms
        val r = rec()
            .answer("a", T, 201, elapsedMs = 3100)
            .timing("order", "a", us(T + 1), us(T + 3010), 201, us(T + 2), us(T + 3008), "Completed")
            .timing("inventory", "a", us(T + 3), us(T + 3006), 201, us(T + 4), us(T + 3004), "Completed")
            .timing("payment", "a", us(T + 5), us(T + 3000), 201)
            .row("order", "a", "COMPLETED").row("inventory", "a", "RESERVED").row("payment", "a", "APPROVED")
            .analyze()
        assertEquals(listOf(0L, 0L, 0L), LAYERS.map { r.long("waste", it, "wasted") })
    }

    @Test
    fun k6가_응답을_받았으면_경과가_짧아도_포기가_아니다() {
        // k6 504, 경과 10ms 인데 주문은 1~1007ms — status 0 이 아니므로 giveUp 이 없다
        val r = rec()
            .answer("a", T, 504, elapsedMs = 10)
            .timing("order", "a", us(T + 1), us(T + 1007), 504, us(T + 2), us(T + 1006), "Unknown:HTTP_504")
            .timing("inventory", "a", us(T + 3), us(T + 1005), 504, us(T + 4), us(T + 1004), "Unknown:HTTP_504")
            .timing("payment", "a", us(T + 5), us(T + 1003), 201)
            .row("order", "a", "UNKNOWN").row("inventory", "a", "UNKNOWN").row("payment", "a", "APPROVED")
            .analyze()
        assertEquals(listOf(0L, 0L, 0L), LAYERS.map { r.long("waste", it, "wasted") })
    }

    @Test
    fun 요청_타임아웃이_아닌_결과_모름은_포기한_시각이_아니다() {
        // 주문이 500ms 에 연결이 끊겨(Unknown:CONNECTION_CLOSED) 돌아왔지만 재고는 1005ms 까지 일했다
        val r = rec()
            .answer("a", T, 504, elapsedMs = 510)
            .timing("order", "a", us(T + 1), us(T + 501), 504, us(T + 2), us(T + 500), "Unknown:CONNECTION_CLOSED")
            .timing("inventory", "a", us(T + 3), us(T + 1005), 201, us(T + 4), us(T + 1004), "Completed")
            .timing("payment", "a", us(T + 5), us(T + 1003), 201)
            .row("order", "a", "UNKNOWN").row("inventory", "a", "RESERVED").row("payment", "a", "APPROVED")
            .analyze()
        assertEquals(listOf(0L, 0L), listOf(r.long("waste", "inventory", "wasted"), r.long("waste", "payment", "wasted")))
    }

    @Test
    fun 자기_층의_타임아웃은_자기_층의_포기_시각이_아니다() {
        // 재고의 callEnd(타임아웃) 1004ms < 재고의 end 1005ms — 재고 위(주문, k6)는 포기하지 않았다
        val r = rec().inventoryTimesOut("a", T).analyze()
        assertEquals(0, r.long("waste", "inventory", "wasted"))
    }

    @Test
    fun k6가_포기한_뒤에_끝난_주문은_주문의_헛일이다() {
        // k6 status 0, 경과 500ms → 포기 500ms. 주문 1~800ms → 300ms
        val r = rec()
            .answer("a", T, 0, elapsedMs = 500)
            .timing("order", "a", us(T + 1), us(T + 800), 201, us(T + 2), us(T + 790), "Completed")
            .timing("inventory", "a", us(T + 3), us(T + 400), 201, us(T + 4), us(T + 300), "Completed")
            .timing("payment", "a", us(T + 5), us(T + 200), 201)
            .row("order", "a", "COMPLETED").row("inventory", "a", "RESERVED").row("payment", "a", "APPROVED")
            .analyze()
        assertEquals(listOf(1.0, 300.0), listOf(r.long("waste", "order", "wasted").toDouble(), wastedMsMax(r, "order")))
    }

    // ---- 층마다 따로 ----

    @Test
    fun 주문이_먼저_포기하면_재고와_결제의_헛일을_층마다_따로_센다() {
        // 주문의 callEnd(타임아웃) 1002ms. 재고 3~1510ms → 508ms, 결제 5~1500ms → 498ms. 주문은 k6가 받았으므로 0
        val r = rec().orderTimesOut("a", T).analyze()
        assertEquals(
            listOf(0.0, 508.0, 498.0),
            listOf(r.double("waste", "order", "wastedMsSum"), wastedMsMax(r, "inventory"), wastedMsMax(r, "payment")),
        )
    }

    @Test
    fun 재고의_헛일_가운데_RESERVED_행이_없는_것은_wastedProcessed가_아니다() {
        val r = rec().orderTimesOut("a", T, inventoryRow = null).analyze()
        assertEquals(listOf(1L, 0L), listOf(r.long("waste", "inventory", "wasted"), r.long("waste", "inventory", "wastedProcessed")))
    }

    // ---- 합과 분위수 ----

    @Test
    fun 헛일_시간의_합은_소수_첫째_자리로_적는다() {
        // 496.123 + 100.456 = 596.579 → 596.6
        val r = rec()
            .inventoryTimesOut("a", T, paymentEndUs = us(T + 1004) + 496_123)
            .inventoryTimesOut("b", T + 100, paymentEndUs = us(T + 100 + 1004) + 100_456)
            .analyze()
        assertEquals(596.6, r.double("waste", "payment", "wastedMsSum")!!, 1e-9)
    }

    @Test
    fun 헛일_시간의_분위수는_천분율_nearest_rank다() {
        // 100ms, 300ms 두 건: p50 순위 (500·2+999)/1000 = 1 → 100, p90 순위 2 → 300
        val r = rec()
            .inventoryTimesOut("a", T, paymentEndUs = us(T + 1004 + 100))
            .inventoryTimesOut("b", T + 100, paymentEndUs = us(T + 100 + 1004 + 300))
            .analyze()
        assertEquals(listOf(2.0, 100.0, 300.0, 300.0, 300.0), quant(r, "waste", "payment", "wastedMs"))
    }

    @Test
    fun 헛일이_없으면_건수_0_합_0_분위수_null() {
        val r = rec().okChain("a", T).analyze()
        assertEquals(
            listOf(0.0, 0.0, 0.0, null, null, null, null),
            listOf(r.double("waste", "payment", "wasted"), r.double("waste", "payment", "wastedMsSum")) +
                quant(r, "waste", "payment", "wastedMs"),
        )
    }
}
