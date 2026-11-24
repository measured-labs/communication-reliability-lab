package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

// docs/measurement.md 7-3 — 구간(edges)의 지표: 호출 수, 타임아웃, 거짓 타임아웃과 그 비율, 결과마다의 수, 호출 시간,
// 넣은 지연. 설계 docs/experiments/task-02-design.md 1절의 표(거짓 타임아웃 = 타임아웃 가운데 아래 층 DB에 처리 행이 있는 것).
class TimingEdgeTest {
    @TempDir
    lateinit var root: Path

    private fun rec() = TimingRecord(root)

    /** 분위수 꼴 {"count","p50","p90","p99","max"}를 [count, p50, p90, p99, max] 로. */
    private fun quant(report: Map<String, Any?>, vararg path: String): List<Double?> =
        listOf("count", "p50", "p90", "p99", "max").map { report.double(*path, it) }

    private val IP = "inventory->payment"
    private val OI = "order->inventory"

    // ---- 거짓 타임아웃 — 재고→결제 ----

    @Test
    fun 재고가_결제를_타임아웃으로_끊었고_결제_행이_있으면_거짓_타임아웃으로_센다() {
        val r = rec().inventoryTimesOut("a", T).analyze()
        assertEquals(1, r.long("edges", IP, "falseTimeouts"))
    }

    @Test
    fun 요청_타임아웃은_타임아웃으로_센다() {
        val r = rec().inventoryTimesOut("a", T).analyze()
        assertEquals(1, r.long("edges", IP, "timeouts"))
    }

    @Test
    fun 타임아웃이지만_결제_행이_없으면_거짓_타임아웃이_아니다() {
        val r = rec().inventoryTimesOut("a", T, paymentProcessed = false).analyze()
        assertEquals(listOf(1L, 0L), listOf(r.long("edges", IP, "timeouts"), r.long("edges", IP, "falseTimeouts")))
    }

    @Test
    fun 타임아웃이_아닌_실패_응답은_결제_행이_있어도_거짓_타임아웃이_아니다() {
        // 결제 fail-after-commit: 행은 남고 500 → 재고는 Failed:HTTP_500 으로 502, 주문은 Failed:HTTP_502
        val r = rec()
            .answer("a", T, 502, "payment:fail-after-commit", 30)
            .timing("order", "a", us(T + 1), us(T + 25), 502, us(T + 2), us(T + 24), "Failed:HTTP_502")
            .timing("inventory", "a", us(T + 3), us(T + 23), 502, us(T + 4), us(T + 22), "Failed:HTTP_500")
            .timing("payment", "a", us(T + 5), us(T + 21), 500)
            .row("order", "a", "FAILED").row("inventory", "a", "FAILED").row("payment", "a", "APPROVED")
            .analyze()
        assertEquals(0, r.long("edges", IP, "falseTimeouts"))
    }

    @Test
    fun 요청_타임아웃이_아닌_결과_모름은_결제_행이_있어도_타임아웃이_아니다() {
        // 연결이 끊김(Unknown:<까닭> 이지만 REQUEST_TIMEOUT 이 아님) — 결제는 처리했다
        val r = rec()
            .answer("a", T, 504, elapsedMs = 30)
            .timing("order", "a", us(T + 1), us(T + 25), 504, us(T + 2), us(T + 24), "Unknown:HTTP_504")
            .timing("inventory", "a", us(T + 3), us(T + 23), 504, us(T + 4), us(T + 22), "Unknown:CONNECTION_CLOSED")
            .timing("payment", "a", us(T + 5), us(T + 21), 201)
            .row("order", "a", "UNKNOWN").row("inventory", "a", "UNKNOWN").row("payment", "a", "APPROVED")
            .analyze()
        assertEquals(listOf(0L, 0L), listOf(r.long("edges", IP, "timeouts"), r.long("edges", IP, "falseTimeouts")))
    }

    // ---- 거짓 타임아웃 — 주문→재고, 층마다 따로 ----

    @Test
    fun 주문이_재고를_타임아웃으로_끊었고_재고가_RESERVED면_주문_재고_구간의_거짓_타임아웃() {
        val r = rec().orderTimesOut("a", T).analyze()
        assertEquals(1, r.long("edges", OI, "falseTimeouts"))
    }

    @Test
    fun 재고의_행이_처리_행이_아니면_주문_재고_구간의_거짓_타임아웃이_아니다() {
        // 재고 행이 FAILED — 4-2절의 처리 행(RESERVED)이 아니다
        val r = rec().orderTimesOut("a", T, inventoryRow = "FAILED").analyze()
        assertEquals(listOf(1L, 0L), listOf(r.long("edges", OI, "timeouts"), r.long("edges", OI, "falseTimeouts")))
    }

    @Test
    fun 주문_재고_구간의_타임아웃은_재고_결제_구간의_거짓_타임아웃이_아니다() {
        // 재고의 호출은 Completed — 결제 행이 있어도 재고→결제 구간에는 타임아웃이 없다
        val r = rec().orderTimesOut("a", T).analyze()
        assertEquals(listOf(0L, 0L), listOf(r.long("edges", IP, "timeouts"), r.long("edges", IP, "falseTimeouts")))
    }

    @Test
    fun 재고_결제_구간의_타임아웃은_주문_재고_구간의_타임아웃이_아니다() {
        // 주문의 결과는 Unknown:HTTP_504(아래가 504) — 요청 타임아웃이 아니다
        val r = rec().inventoryTimesOut("a", T).analyze()
        assertEquals(listOf(0L, 0L), listOf(r.long("edges", OI, "timeouts"), r.long("edges", OI, "falseTimeouts")))
    }

    // ---- 호출 수, 비율, 결과마다의 수 ----

    /**
     * 다섯 요청: a·d 정상, b 재고→결제 거짓 타임아웃, c 재고→결제 타임아웃(결제 행 없음),
     * e 재고가 `inventory:error`로 결제를 부르지 않음(재고 call 칸 null, 주문은 Failed:HTTP_500).
     * 재고→결제: calls 4, timeouts 2, falseTimeouts 1 → 비율 0.25.
     */
    private fun mixed() = rec()
        .okChain("a", T)
        .inventoryTimesOut("b", T + 100)
        .inventoryTimesOut("c", T + 200, paymentProcessed = false)
        .okChain("d", T + 300)
        .answer("e", T + 400, 502, "inventory:error", 30)
        .timing("order", "e", us(T + 401), us(T + 420), 502, us(T + 402), us(T + 418), "Failed:HTTP_500")
        .timing("inventory", "e", us(T + 403), us(T + 416), 500)
        .row("order", "e", "FAILED")

    @Test
    fun 호출_수는_call_결과가_있는_시각_기록의_수다() {
        val r = mixed().analyze()
        assertEquals(4, r.long("edges", IP, "calls"))
    }

    @Test
    fun 거짓_타임아웃_비율은_거짓_타임아웃_나누기_호출_수다() {
        val r = mixed().analyze()
        assertEquals(0.25, r.double("edges", IP, "falseTimeoutRatio")!!, 1e-12)
    }

    @Test
    fun 결과마다의_수를_callOutcome_낱말_그대로_센다() {
        val r = mixed().analyze()
        val outcomes: Map<String, Long> =
            (r.at("edges", OI, "outcomes") as Map<*, *>).entries.associate { it.key as String to (it.value as Number).toLong() }
        assertEquals(mapOf("Completed" to 2L, "Unknown:HTTP_504" to 2L, "Failed:HTTP_500" to 1L), outcomes)
    }

    @Test
    fun 호출이_없으면_거짓_타임아웃_비율은_null() {
        val r = rec()
            .answer("e", T, 502, "inventory:error", 30)
            .timing("order", "e", us(T + 1), us(T + 20), 502, us(T + 2), us(T + 18), "Failed:HTTP_500")
            .timing("inventory", "e", us(T + 3), us(T + 16), 500)
            .row("order", "e", "FAILED")
            .analyze()
        assertNull(r.at("edges", IP, "falseTimeoutRatio"))
    }

    @Test
    fun 구간의_타임아웃_값은_위_층_설정의_requestTimeoutMs다() {
        val r = rec().apply { inventoryTimeoutMs = 1234 }.okChain("a", T).analyze()
        assertEquals(1234, r.long("edges", IP, "timeoutMs"))
    }

    @Test
    fun 호출_시간은_위_층이_잰_callEnd_빼기_callStart의_분위수다() {
        // 재고의 호출: a 4~16ms → 12ms, b 4~1004ms → 1000ms. n=2: p50은 1번째, p90·p99는 2번째(nearest-rank)
        val r = rec().okChain("a", T).inventoryTimesOut("b", T + 100).analyze()
        assertEquals(listOf(2.0, 12.0, 1000.0, 1000.0, 1000.0), quant(r, "edges", IP, "callMs"))
    }

    // ---- 넣은 지연 ----

    /** 결제 지연 999·1000·1500ms 와 지연 없는 요청 하나. 재고→결제 타임아웃 1000ms. */
    private fun delays() = rec()
        .okChain("a", T, "payment:delay=999")
        .okChain("b", T + 100, "payment:delay=1000")
        .okChain("c", T + 200, "payment:delay=1500")
        .okChain("d", T + 300)

    @Test
    fun 타임아웃_이상인_넣은_지연은_같은_값도_센다() {
        val r = delays().analyze()
        assertEquals(2, r.long("edges", IP, "injectedCalleeDelayAtLeastTimeout"))
    }

    @Test
    fun 넣은_지연의_분위수는_그_서비스를_가리키는_delay_값만으로_센다() {
        // 999, 1000, 1500 → p50 은 2번째(1000), p90·p99·max 는 1500
        val r = delays().analyze()
        assertEquals(listOf(3.0, 1000.0, 1500.0, 1500.0, 1500.0), quant(r, "delays", "payment"))
    }

    @Test
    fun 지연을_넣지_않은_서비스의_넣은_지연은_0건과_null() {
        val r = delays().analyze()
        assertEquals(listOf(0.0, null, null, null, null), quant(r, "delays", "order"))
    }

    // ---- 층의 시각 기록 ----

    @Test
    fun 층의_응답_시간은_end_빼기_start의_분위수다() {
        // 결제: a 5~15ms → 10ms, b 5~1500ms → 1495ms
        val r = rec().okChain("a", T).inventoryTimesOut("b", T + 100).analyze()
        assertEquals(listOf(2.0, 10.0, 1495.0, 1495.0, 1495.0), quant(r, "layers", "payment", "serviceMs"))
    }

    @Test
    fun 층의_기록_수는_그_층의_시각_기록_수다() {
        val r = mixed().analyze()
        assertEquals(listOf(5L, 5L, 4L), LAYERS.map { r.long("layers", it, "records") })
    }
}
