package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 7-3의 (task 05) 줄과 11-2 — gRPC 경로의 시각 지표.
// "포기한 결과" = Unknown:REQUEST_TIMEOUT · Unknown:DEADLINE_EXCEEDED · Unknown:CANCELLED(그 밖의 Unknown 은 아니다).
// waste.<L>.stopped(헛일 가운데 stopped 가 있는 것), cancelSeen(헛일 가운데 cancelledUs 가 있는 것),
// cancelAfterGiveUpMs = cancelledUs − giveUp(음수일 수 있다), endAfterCancelMs = end − cancelledUs,
// layers.<L>.stopped.<자리>(자리 셋 모두 — 없으면 0). 그리고 상수(11-5).
// 이 파일은 분석기의 구현을 보지 않고 문서만으로 쓴다.
class TimingGrpcWasteTest {
    @TempDir
    lateinit var root: Path

    private fun rec() = TimingRecord(root)

    private fun TimingRecord.add(t: GrpcTiming): TimingRecord = rawTiming(t.layer, t.json())

    /**
     * 재고 → 결제 gRPC 호출이 [outcome]으로 끝난 요청(재고의 callEnd = s+1004ms — 포기한 시각). 재고 504, 주문 Unknown:CHAIN_504,
     * k6 504(경과 1010ms). 결제는 [paymentStartMs]~[paymentEndUs], [paymentProcessed]면 결제 행 APPROVED.
     * 결제의 [cancelledUs]·[stopped](멈췄으면 status 0).
     */
    private fun TimingRecord.inventoryGrpcEnds(
        id: String,
        s: Long,
        outcome: String = "Unknown:DEADLINE_EXCEEDED",
        paymentStartMs: Long = 5,
        paymentEndUs: Long = us(s + 1500),
        paymentProcessed: Boolean = true,
        cancelledUs: Long? = null,
        stopped: String? = null,
        paymentDeadlineUs: Long? = us(s + 1004),
        paymentAbsentKeys: Set<String> = emptySet(),
    ): TimingRecord {
        answer(id, s, 504, "", 1010)
        add(GrpcTiming("order", id, us(s + 1), us(s + 1007), 504, us(s + 2), us(s + 1006), "Unknown:CHAIN_504", "http"))
        add(GrpcTiming("inventory", id, us(s + 3), us(s + 1005), 504, us(s + 4), us(s + 1004), outcome, "grpc", deadlineUs = us(s + 2002)))
        add(
            GrpcTiming(
                "payment", id, us(s + paymentStartMs), paymentEndUs, if (stopped != null) 0 else if (paymentProcessed) 201 else 500,
                transport = "grpc", deadlineUs = paymentDeadlineUs, cancelledUs = cancelledUs, stopped = stopped,
                absentKeys = paymentAbsentKeys,
            ),
        )
        row("order", id, "UNKNOWN").row("inventory", id, "UNKNOWN")
        if (paymentProcessed && stopped == null) row("payment", id, "APPROVED")
        return this
    }

    private fun wastedMsMax(r: Map<String, Any?>, layer: String) = r.double("waste", layer, "wastedMs", "max")!!

    private fun quant(r: Map<String, Any?>, vararg path: String): List<Double?> =
        listOf("count", "p50", "p90", "p99", "max").map { r.double(*path, it) }

    // ---- 포기한 결과의 갈래 ----

    @Test
    fun DEADLINE_EXCEEDED는_포기한_결과다() {
        val r = rec().inventoryGrpcEnds("a", T, "Unknown:DEADLINE_EXCEEDED").analyze()
        assertEquals(listOf(1L, 1L), listOf(r.long("edges", "inventory->payment", "timeouts"), r.long("waste", "payment", "wasted")))
        assertEquals(496.0, wastedMsMax(r, "payment"), 1e-9)
    }

    @Test
    fun CANCELLED는_포기한_결과다() {
        val r = rec().inventoryGrpcEnds("a", T, "Unknown:CANCELLED").analyze()
        assertEquals(listOf(1L, 1L), listOf(r.long("edges", "inventory->payment", "timeouts"), r.long("waste", "payment", "wasted")))
    }

    @Test
    fun UNAVAILABLE은_결과_모름이지만_포기한_결과가_아니다() {
        val r = rec().inventoryGrpcEnds("a", T, "Unknown:UNAVAILABLE").analyze()
        assertEquals(listOf(0L, 0L), listOf(r.long("edges", "inventory->payment", "timeouts"), r.long("waste", "payment", "wasted")))
    }

    @Test
    fun 트레일러의_504는_포기한_결과가_아니다() {
        // Unknown:CHAIN_504 — 아래 층의 처리 코드가 504로 답했다(위가 포기한 것이 아니다)
        val r = rec().inventoryGrpcEnds("a", T, "Unknown:CHAIN_504").analyze()
        assertEquals(listOf(0L, 0L), listOf(r.long("edges", "inventory->payment", "timeouts"), r.long("waste", "payment", "wasted")))
    }

    @Test
    fun 주문의_DEADLINE_EXCEEDED는_재고와_결제의_포기_시각이다() {
        // 주문 → 재고 callEnd 1002ms(DEADLINE_EXCEEDED). 재고 3~1510ms → 508ms, 결제 5~1500ms → 498ms
        val r = rec()
            .answer("a", T, 504, "", 1010)
            .add(GrpcTiming("order", "a", us(T + 1), us(T + 1003), 504, us(T + 2), us(T + 1002), "Unknown:DEADLINE_EXCEEDED", "http"))
            .add(GrpcTiming("inventory", "a", us(T + 3), us(T + 1510), 201, us(T + 4), us(T + 1504), "Completed", "grpc", deadlineUs = us(T + 1002)))
            .add(GrpcTiming("payment", "a", us(T + 5), us(T + 1500), 201, transport = "grpc", deadlineUs = us(T + 1004)))
            .row("order", "a", "UNKNOWN").row("inventory", "a", "RESERVED").row("payment", "a", "APPROVED")
            .analyze()
        assertEquals(listOf(508.0, 498.0), listOf(wastedMsMax(r, "inventory"), wastedMsMax(r, "payment")))
    }

    // ---- 멈춘 일 ----

    @Test
    fun 헛일_가운데_멈춘_것을_waste_stopped로_센다() {
        // 결제가 지연 중에 멈춤: 5~1010ms(포기 1004ms 뒤) — 헛일이고 멈췄다. 처리 행 없음
        val r = rec()
            .inventoryGrpcEnds("a", T, cancelledUs = us(T + 1005), paymentEndUs = us(T + 1010), stopped = "cancelled-in-delay")
            .inventoryGrpcEnds("b", T + 100) // 멈추지 않은 헛일
            .analyze()
        assertEquals(
            listOf(2L, 1L, 1L),
            listOf(r.long("waste", "payment", "wasted"), r.long("waste", "payment", "stopped"), r.long("waste", "payment", "wastedProcessed")),
        )
    }

    @Test
    fun 포기_전에_멈춘_기록은_헛일이_아니어서_waste_stopped가_아니지만_layers에는_센다() {
        // 결제가 1000ms 에 멈춤(포기 1004ms 전 — 받은 기한이 먼저 지났다)
        val r = rec()
            .inventoryGrpcEnds("a", T, cancelledUs = us(T + 999), paymentEndUs = us(T + 1000), stopped = "cancelled-in-delay")
            .analyze()
        assertEquals(
            listOf(0L, 0L, 1L),
            listOf(r.long("waste", "payment", "wasted"), r.long("waste", "payment", "stopped"), r.long("layers", "payment", "stopped", "cancelled-in-delay")),
        )
    }

    @Test
    fun layers_stopped는_자리_셋을_모두_내고_없는_자리는_0이다() {
        val r = rec()
            .inventoryGrpcEnds("a", T, paymentStartMs = 1004, paymentEndUs = us(T + 1004) + 10, stopped = "deadline-before-start")
            .inventoryGrpcEnds("b", T + 100, paymentEndUs = us(T + 100 + 1006), stopped = "deadline-before-work")
            .inventoryGrpcEnds("c", T + 200, paymentEndUs = us(T + 200 + 1006), stopped = "deadline-before-work")
            .analyze()
        assertEquals(
            listOf(1L, 0L, 2L),
            listOf("deadline-before-start", "cancelled-in-delay", "deadline-before-work").map { r.long("layers", "payment", "stopped", it) },
        )
        assertEquals(
            listOf(0L, 0L, 0L),
            listOf("deadline-before-start", "cancelled-in-delay", "deadline-before-work").map { r.long("layers", "inventory", "stopped", it) },
        )
    }

    @Test
    fun 새_칸이_없는_옛_기록에서도_layers_stopped는_0이고_취소_지표는_비었다() {
        val r = rec().inventoryTimesOut("a", T).analyze()
        assertEquals(
            listOf(0L, 0L, 0L, 0L),
            listOf(
                r.long("waste", "payment", "stopped"),
                r.long("waste", "payment", "cancelSeen"),
                r.long("layers", "payment", "stopped", "cancelled-in-delay"),
                r.long("waste", "payment", "cancelAfterGiveUpMs", "count"),
            ),
        )
        assertEquals(null, r.double("waste", "payment", "cancelAfterGiveUpMs", "p50"))
    }

    // ---- 취소를 안 것 ----

    @Test
    fun 헛일_가운데_취소_시각이_있는_것을_cancelSeen으로_센다() {
        val r = rec()
            .inventoryGrpcEnds("a", T, cancelledUs = us(T + 1010))
            .inventoryGrpcEnds("b", T + 100)
            .analyze()
        assertEquals(listOf(2L, 1L), listOf(r.long("waste", "payment", "wasted"), r.long("waste", "payment", "cancelSeen")))
    }

    @Test
    fun 헛일이_아닌_기록의_취소_시각은_cancelSeen에_들지_않는다() {
        // 포기하지 않은 정상 사슬의 결제가 취소 시각을 가졌다(꼴은 맞다) — 헛일이 아니므로 세지 않는다
        val r = rec()
            .answer("a", T, 201, "", 20)
            .add(GrpcTiming("order", "a", us(T + 1), us(T + 19), 201, us(T + 2), us(T + 18), "Completed", "http"))
            .add(GrpcTiming("inventory", "a", us(T + 3), us(T + 17), 201, us(T + 4), us(T + 16), "Completed", "grpc", deadlineUs = us(T + 2002)))
            .add(GrpcTiming("payment", "a", us(T + 5), us(T + 15), 201, transport = "grpc", deadlineUs = us(T + 1004), cancelledUs = us(T + 10)))
            .row("order", "a", "COMPLETED").row("inventory", "a", "RESERVED").row("payment", "a", "APPROVED")
            .analyze()
        assertEquals(listOf(0L, 0L), listOf(r.long("waste", "payment", "wasted"), r.long("waste", "payment", "cancelSeen")))
    }

    @Test
    fun 취소가_닿기까지의_시간은_취소_시각_빼기_포기한_시각이다() {
        // 포기 1004ms, 결제가 안 시각 1010ms → 6ms, 끝 1500ms → 끝 − 안 시각 490ms
        val r = rec().inventoryGrpcEnds("a", T, cancelledUs = us(T + 1010)).analyze()
        assertEquals(listOf(1.0, 6.0, 6.0, 6.0, 6.0), quant(r, "waste", "payment", "cancelAfterGiveUpMs"))
        assertEquals(listOf(1.0, 490.0, 490.0, 490.0, 490.0), quant(r, "waste", "payment", "endAfterCancelMs"))
    }

    @Test
    fun 취소가_포기보다_먼저면_닿기까지의_시간은_음수다() {
        // 받은 기한이 위의 포기(1004ms)보다 먼저 지났다: 안 시각 1000ms → −4ms. 끝 1500ms → 500ms
        val r = rec().inventoryGrpcEnds("a", T, cancelledUs = us(T + 1000)).analyze()
        assertEquals(listOf(1.0, -4.0, -4.0), quant(r, "waste", "payment", "cancelAfterGiveUpMs").take(3))
        assertEquals(500.0, r.double("waste", "payment", "endAfterCancelMs", "p50")!!, 1e-9)
    }

    @Test
    fun 취소가_닿기까지의_시간은_마이크로초까지_ms로_적는다() {
        // 안 시각 = 포기 + 1μs → 0.001ms
        val r = rec().inventoryGrpcEnds("a", T, cancelledUs = us(T + 1004) + 1).analyze()
        assertEquals(0.001, r.double("waste", "payment", "cancelAfterGiveUpMs", "p50")!!, 1e-9)
    }

    @Test
    fun 취소_지표의_분위수는_천분율_nearest_rank다() {
        // 6ms, 20ms 두 건: p50 순위 1 → 6, p90 순위 2 → 20
        val r = rec()
            .inventoryGrpcEnds("a", T, cancelledUs = us(T + 1010))
            .inventoryGrpcEnds("b", T + 100, cancelledUs = us(T + 100 + 1024))
            .analyze()
        assertEquals(listOf(2.0, 6.0, 20.0, 20.0, 20.0), quant(r, "waste", "payment", "cancelAfterGiveUpMs"))
    }

    @Test
    fun 취소_지표는_취소를_안_헛일만_본다() {
        // a 는 취소를 알았고(6ms), b 는 몰랐다 — count 1
        val r = rec()
            .inventoryGrpcEnds("a", T, cancelledUs = us(T + 1010))
            .inventoryGrpcEnds("b", T + 100)
            .analyze()
        assertEquals(listOf(1.0, 1.0), listOf(r.double("waste", "payment", "cancelAfterGiveUpMs", "count"), r.double("waste", "payment", "endAfterCancelMs", "count")))
    }

    // ---- 기한 전에 안 취소(7-3: cancelMinusDeadlineMs · cancelBeforeDeadline, 여유 500μs) ----
    // 결제가 받은 기한 = s+1004ms(포기와 같은 때). 결제는 5~1500ms(헛일). cancelledUs = 기한 + [deltaUs].

    private fun cancelAt(deltaUs: Long) = rec().inventoryGrpcEnds("a", T, cancelledUs = us(T + 1004) + deltaUs).analyze()

    @Test
    fun 취소가_기한보다_정확히_500마이크로초_먼저면_기한_전에_안_것이_아니다() {
        val r = cancelAt(-500)
        assertEquals(0L, r.long("waste", "payment", "cancelBeforeDeadline"))
        assertEquals(-0.5, r.double("waste", "payment", "cancelMinusDeadlineMs", "p50")!!, 1e-9)
    }

    @Test
    fun 취소가_기한보다_501마이크로초_먼저면_기한_전에_안_것이다() {
        val r = cancelAt(-501)
        assertEquals(1L, r.long("waste", "payment", "cancelBeforeDeadline"))
        assertEquals(-0.501, r.double("waste", "payment", "cancelMinusDeadlineMs", "p50")!!, 1e-9)
    }

    @Test
    fun 기한의_타이머로_안_취소는_0보다_조금_크고_기한_전이_아니다() {
        val r = cancelAt(400)
        assertEquals(listOf(1.0, 0.4, 0.0), listOf(
            r.double("waste", "payment", "cancelMinusDeadlineMs", "count"),
            r.double("waste", "payment", "cancelMinusDeadlineMs", "p50"),
            r.double("waste", "payment", "cancelBeforeDeadline"),
        ))
    }

    @Test
    fun 취소_시각이_없으면_세지_않는다() {
        val r = rec().inventoryGrpcEnds("a", T).analyze()
        assertEquals(listOf(0.0, 0.0), listOf(r.double("waste", "payment", "cancelBeforeDeadline"), r.double("waste", "payment", "cancelMinusDeadlineMs", "count")))
        assertEquals(null, r.double("waste", "payment", "cancelMinusDeadlineMs", "p50"))
    }

    @Test
    fun 기한_칸이_null이면_취소가_일러도_세지_않는다() {
        val r = rec().inventoryGrpcEnds("a", T, cancelledUs = us(T + 500), paymentDeadlineUs = null).analyze()
        assertEquals(listOf(0.0, 0.0, 1.0), listOf(
            r.double("waste", "payment", "cancelBeforeDeadline"),
            r.double("waste", "payment", "cancelMinusDeadlineMs", "count"),
            r.double("waste", "payment", "cancelSeen"),
        ))
    }

    @Test
    fun 기한_키가_아예_없어도_null과_같아_세지_않는다() {
        val r = rec().inventoryGrpcEnds("a", T, cancelledUs = us(T + 500), paymentAbsentKeys = setOf("deadlineUs")).analyze()
        assertEquals(listOf(0.0, 0.0), listOf(r.double("waste", "payment", "cancelBeforeDeadline"), r.double("waste", "payment", "cancelMinusDeadlineMs", "count")))
    }

    @Test
    fun 헛일이_아닌_기록의_이른_취소는_세지_않는다() {
        // 결제가 포기(1004ms) 전인 1000ms 에 끝났다 — 헛일이 아니다. 취소는 기한보다 4ms 먼저
        val r = rec().inventoryGrpcEnds("a", T, paymentEndUs = us(T + 1000), cancelledUs = us(T + 1000)).analyze()
        assertEquals(listOf(0.0, 0.0, 0.0), listOf(
            r.double("waste", "payment", "wasted"),
            r.double("waste", "payment", "cancelBeforeDeadline"),
            r.double("waste", "payment", "cancelMinusDeadlineMs", "count"),
        ))
    }

    @Test
    fun 기한_전에_안_취소는_헛일마다_센다() {
        // a −501μs(셈), b −500μs(안 셈), c −10ms(셈) → 2
        val r = rec()
            .inventoryGrpcEnds("a", T, cancelledUs = us(T + 1004) - 501)
            .inventoryGrpcEnds("b", T + 100, cancelledUs = us(T + 100 + 1004) - 500)
            .inventoryGrpcEnds("c", T + 200, cancelledUs = us(T + 200 + 994))
            .analyze()
        assertEquals(listOf(2.0, 3.0), listOf(r.double("waste", "payment", "cancelBeforeDeadline"), r.double("waste", "payment", "cancelMinusDeadlineMs", "count")))
    }

    @Test
    fun timingReport_입구도_같은_값을_낸다() {
        val t = rec().inventoryGrpcEnds("a", T, cancelledUs = us(T + 1004) - 501)
        t.write()
        assertEquals(1L, (timingReport(t.dir, null).at("waste", "payment", "cancelBeforeDeadline") as Number).toLong())
    }

    @Test
    fun 기한_전_여유는_500마이크로초다() {
        assertEquals(500L, CANCEL_BEFORE_DEADLINE_MARGIN_US.toLong())
    }

    // ---- 상수(11-5) ----

    @Test
    fun 포기한_결과의_목록은_셋이다() {
        assertEquals(
            setOf("Unknown:REQUEST_TIMEOUT", "Unknown:DEADLINE_EXCEEDED", "Unknown:CANCELLED"),
            GIVE_UP_OUTCOMES.toSet(),
        )
    }

    @Test
    fun 멈춘_자리의_목록은_셋이다() {
        assertEquals(setOf("deadline-before-start", "cancelled-in-delay", "deadline-before-work"), STOP_REASONS.toSet())
    }

    @Test
    fun 전송의_목록은_http와_grpc다() {
        assertEquals(setOf("http", "grpc"), TRANSPORTS.toSet())
    }

    @Test
    fun 기한의_여유는_50ms다() {
        assertEquals(50.0, DEADLINE_SLACK_MS, 0.0)
    }
}
