package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

// docs/measurement.md 8-4 — clients·layers·edges·attemptFaults·mix 의 식. 기준 기록은 RetryRecord.kt 의 goodRetryRecord
// (a 정상, b 한 번 실패 뒤 성공, c 세 번 실패, d 첫 시도 타임아웃인데 결제가 처리 → 두 번 처리됨, e 404).
class RetryMetricsTest {
    @TempDir
    lateinit var root: Path

    private fun good() = goodRetryRecord(root)

    private val INV = "inventory->payment"
    private val ORD = "order->inventory"

    @Test
    fun 기준_기록은_통과한다() {
        val r = good()
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    // ---- clients ----

    @Test
    fun 판정마다의_정답_수는_4_1절의_판정이다() {
        val r = good().analyze()
        assertEquals(listOf(3L, 2L, 0L), listOf("completed", "failed", "unknown").map { r.long("clients", it) })
    }

    @Test
    fun 성공_비율은_완료_나누기_정답_수다() {
        assertEquals(0.6, good().analyze().double("clients", "successRatio")!!, 1e-9)
    }

    @Test
    fun 모든_정답의_경과_분위수() {
        // [20, 20, 130, 340, 1120]: p50 = 3번째, p90·p99 = 5번째
        val r = good().analyze()
        assertEquals(listOf(5.0, 130.0, 1120.0, 1120.0, 1120.0), listOf("count", "p50", "p90", "p99", "max").map { r.double("clients", "elapsedMs", it) })
    }

    @Test
    fun 완료인_정답의_경과_분위수() {
        // [20, 130, 1120]: p50 = 2번째, p90 = 3번째
        val r = good().analyze()
        assertEquals(listOf(3.0, 130.0, 1120.0, 1120.0), listOf("count", "p50", "p90", "max").map { r.double("clients", "completedElapsedMs", it) })
    }

    @Test
    fun 결과_모름도_판정마다의_수에_센다() {
        val r = good().inventoryRetried("x", T + 500, listOf("Unknown:REQUEST_TIMEOUT")).analyze()
        assertEquals(listOf(3L, 2L, 1L), listOf("completed", "failed", "unknown").map { r.long("clients", it) })
    }

    // ---- layers ----

    @Test
    fun 층의_도착은_그_층의_시각_기록_수다() {
        assertEquals(listOf(5L, 5L, 9L), good().analyze().perLayer("arrivals"))
    }

    @Test
    fun 도착의_배수는_도착_나누기_정답_수다() {
        val r = good().analyze()
        assertEquals(listOf(1.0, 1.0, 1.8), LAYERS.map { r.double("layers", it, "arrivalsPerRequest")!! })
    }

    @Test
    fun 닿은_요청_수는_시각_기록에_나오는_requestId_수다() {
        assertEquals(listOf(5L, 5L, 5L), good().analyze().perLayer("requestsReached"))
    }

    @Test
    fun 다시_든_횟수는_도착_빼기_닿은_요청_수다() {
        assertEquals(listOf(0L, 0L, 4L), good().analyze().perLayer("repeatArrivals"))
    }

    @Test
    fun 층의_행과_처리_행() {
        val r = good().analyze()
        assertEquals(listOf(5L, 5L, 4L), r.perLayer("rows"))
        assertEquals(listOf(3L, 3L, 4L), r.perLayer("processedRows"))
    }

    @Test
    fun 두_번_처리됨은_처리_행이_둘_이상인_requestId_수다() {
        val r = good().analyze()
        assertEquals(listOf(0L, 0L, 1L), r.perLayer("doubled"))
        assertEquals(1L, r.long("doubledTotal"))
    }

    @Test
    fun 넘친_처리_행은_requestId마다_처리_행_빼기_1의_합이다() {
        // d 에 결제 행을 하나 더(셋) → 2
        val r = good().row("payment", "d", "APPROVED").analyze()
        assertEquals(listOf(0L, 0L, 2L), r.perLayer("extraProcessedRows"))
        assertEquals(1L, r.long("layers", "payment", "doubled"))
    }

    @Test
    fun 끝내_성공하지_못한_요청의_처리_행을_센다() {
        // x: 재고→결제 타임아웃(최대 1)으로 504 — 결제는 처리했다(행 APPROVED)
        val r = good().inventoryRetried("x", T + 500, listOf("Unknown:REQUEST_TIMEOUT"))
            .row("payment", "x", "APPROVED").analyze()
        assertEquals(listOf(0L, 0L, 1L), r.perLayer("processedNotCompleted"))
    }

    @Test
    fun 두_번_처리됨_전체는_어느_층이든_둘_이상인_requestId_수다() {
        // a 의 재고 행을 하나 더 — d(결제)와 a(재고) 두 requestId
        val r = good().row("inventory", "a", "RESERVED").analyze()
        assertEquals(2L, r.long("doubledTotal"))
    }

    // ---- edges: 시도 기록 ----

    @Test
    fun 감싼_호출_수는_callId_수다() {
        assertEquals(5L, good().analyze().long("edges", INV, "calls"))
    }

    @Test
    fun 시도_수와_재시도_수() {
        val r = good().analyze()
        assertEquals(9L, r.long("edges", INV, "attempts"))
        assertEquals(4L, r.long("edges", INV, "retries"))
    }

    @Test
    fun 결정마다의_수는_다섯_모두_0을_포함한다() {
        val r = good().analyze()
        assertEquals(
            mapOf("success" to 3L, "not-retryable" to 1L, "retry" to 4L, "max-attempts" to 1L, "budget-denied" to 0L),
            listOf("success", "not-retryable", "retry", "max-attempts", "budget-denied").associateWith { r.long("edges", INV, "decisions", it) },
        )
        assertEquals(0L, r.long("edges", INV, "budgetDenied"))
    }

    @Test
    fun 예산_거절의_수() {
        val r = good().inventoryRetried("x", T + 500, listOf("Failed:HTTP_500"), lastDecision = "budget-denied").analyze()
        assertEquals(1L, r.long("edges", INV, "budgetDenied"))
        assertEquals(1L, r.long("edges", INV, "decisions", "budget-denied"))
    }

    @Test
    fun 시도를_정확히_k번_쓴_호출_수() {
        val r = good().analyze()
        assertEquals(listOf(2L, 2L, 1L), listOf("1", "2", "3").map { r.long("edges", INV, "callsByAttempts", it) })
    }

    @Test
    fun 입구에_들지_않은_시도는_시도_빼기_아래_층_도착이다() {
        assertEquals(0L, good().analyze().long("edges", INV, "attemptsNotArrived"))
    }

    @Test
    fun 연결_거부로_결제에_들지_않은_시도를_센다() {
        // x: 시도 1 연결 거부(결제에 들지 않음), 시도 2 성공 → 시도 2, 도착 1
        val r = good().inventoryRetried("x", T + 500, listOf("Failed:CONNECT_REFUSED", "Completed")).analyze()
        assertEquals(1L, r.long("edges", INV, "attemptsNotArrived"))
    }

    @Test
    fun 재시도하지_않는_층의_시도_기록이_비면_호출과_시도는_0이다() {
        val r = good().analyze()
        assertEquals(listOf(0L, 0L, 0L), listOf("calls", "attempts", "retries").map { r.long("edges", ORD, it) })
        // 식 그대로: 시도 0 − 재고 도착 5
        assertEquals(-5L, r.long("edges", ORD, "attemptsNotArrived"))
    }

    @Test
    fun 시도가_없으면_간격_차의_분위수는_count_0과_null이다() {
        val r = good().analyze()
        assertEquals(0L, r.long("edges", ORD, "gapMinusDelayMs", "count"))
        assertNull(r.at("edges", ORD, "gapMinusDelayMs", "p50"))
        assertNull(r.at("edges", ORD, "gapMinusDelayMs", "max"))
    }

    @Test
    fun 계획한_간격은_시도_번호마다의_분위수다() {
        val r = good().analyze()
        assertEquals(listOf(3.0, 100.0, 100.0), listOf("count", "p50", "max").map { r.double("edges", INV, "delayMs", "attempt2", it) })
        assertEquals(listOf(1.0, 200.0, 200.0), listOf("count", "p50", "max").map { r.double("edges", INV, "delayMs", "attempt3", it) })
    }

    @Test
    fun 실제_간격은_앞_시도의_끝부터_이_시도의_시작까지다() {
        // 시도 2: b 100, c 100, d 100.5 → p50 = 2번째 100, p90 = 3번째 100.5
        val r = good().analyze()
        assertEquals(listOf(3.0, 100.0, 100.5, 100.5), listOf("count", "p50", "p90", "max").map { r.double("edges", INV, "gapMs", "attempt2", it) })
        assertEquals(200.0, r.double("edges", INV, "gapMs", "attempt3", "max"))
    }

    @Test
    fun 간격_차는_모든_재시도의_실제_빼기_계획이다() {
        // b 0, c 0, c 0, d 0.5 → count 4, p50 = 2번째 0, max 0.5
        val r = good().analyze()
        assertEquals(listOf(4.0, 0.0, 0.5, 0.5), listOf("count", "p50", "p90", "max").map { r.double("edges", INV, "gapMinusDelayMs", it) })
    }

    @Test
    fun 재시도가_있으면_재시도_층의_호출과_시도를_센다() {
        // 주문이 재시도: y 는 재고가 처음 한 번 500(fail-first), 주문이 50ms 뒤 다시 불러 성공
        val r = orderRetryRecord().analyze()
        assertEquals(listOf(1L, 2L, 1L), listOf("calls", "attempts", "retries").map { r.long("edges", ORD, it) })
        assertEquals(0L, r.long("edges", ORD, "attemptsNotArrived"))
        assertEquals(listOf(2L, 1L, 1L), r.perLayer("arrivals").let { listOf(it[1], it[2], r.long("layers", "inventory", "repeatArrivals")) })
        assertEquals(50.0, r.double("edges", ORD, "delayMs", "attempt2", "max"))
    }

    @Test
    fun 재고를_가리킨_fail_first도_재고의_시각_기록에서_다시_센다() {
        val r = orderRetryRecord().analyze()
        assertEquals(listOf(1L, 1L), listOf("counted", "expected").map { r.long("attemptFaults", "inventory", "fail-first", it) })
    }

    /** 주문만 재시도하는 기록: y(T) `inventory:fail-first=1` — 재고가 처음 든 것은 500(행 없음), 두 번째는 처리. */
    private fun orderRetryRecord(): RetryRecord {
        val s = T
        return RetryRecord(root)
            .attempt("order", Attempt("y", 1, 1, us(s + 2), us(s + 6), "Failed:HTTP_500", 0, "retry"))
            .attempt("order", Attempt("y", 1, 2, us(s + 56), us(s + 70), "Completed", 50, "success"))
            .timing("inventory", "y", us(s + 3), us(s + 5), 500)
            .timing("inventory", "y", us(s + 57), us(s + 69), 201, us(s + 58), us(s + 68), "Completed")
            .timing("payment", "y", us(s + 59), us(s + 67), 201)
            .timing("order", "y", us(s + 1), us(s + 72), 201, us(s + 56), us(s + 70), "Completed")
            .row("order", "y", "COMPLETED").row("inventory", "y", "RESERVED").row("payment", "y", "APPROVED")
            .answer("y", s, 201, "inventory:fail-first=1", elapsedMs = 75)
            .counted("inventory", "fail-first", 1)
    }

    // ---- attemptFaults ----

    @Test
    fun 서비스가_센_수는_attempt_faults_json의_값이다() {
        val r = good().analyze()
        assertEquals(listOf(4L, 1L, 0L), ATTEMPT_KINDS.map { r.long("attemptFaults", "payment", it, "counted") })
    }

    @Test
    fun 기록에서_다시_센_수는_fail_first가_min_n_시각_기록_수의_합이다() {
        // b: min(1, 2) = 1, c: min(9, 3) = 3
        assertEquals(4L, good().analyze().long("attemptFaults", "payment", "fail-first", "expected"))
    }

    @Test
    fun fail_first의_n이_시각_기록_수보다_작으면_n을_센다() {
        // f: fail-first=2, 결제에 세 번 듦(500, 500, 성공) → 2
        val r = RetryRecord(root)
            .inventoryRetried("f", T, listOf("Failed:HTTP_500", "Failed:HTTP_500", "Completed"), attemptFault = "payment:fail-first=2")
            .counted("payment", "fail-first", 2)
        assertEquals(2L, r.analyze().long("attemptFaults", "payment", "fail-first", "expected"))
    }

    @Test
    fun fail_first의_n이_시각_기록_수보다_크면_시각_기록_수를_센다() {
        // f: fail-first=3, 결제에 두 번 듦(최대 2) → 2
        val r = RetryRecord(root)
            .inventoryRetried("f", T, listOf("Failed:HTTP_500", "Failed:HTTP_500"), attemptFault = "payment:fail-first=3")
            .counted("payment", "fail-first", 2)
        assertEquals(2L, r.analyze().long("attemptFaults", "payment", "fail-first", "expected"))
    }

    @Test
    fun slow_first도_min_n_시각_기록_수다() {
        assertEquals(1L, good().analyze().long("attemptFaults", "payment", "slow-first", "expected"))
    }

    @Test
    fun 가리키지_않은_서비스와_종류는_0이다() {
        val r = good().analyze()
        val cells = listOf("order", "inventory").flatMap { s -> ATTEMPT_KINDS.map { k -> r.long("attemptFaults", s, k, "expected") } } +
            r.long("attemptFaults", "payment", "outage", "expected")
        assertEquals(List(7) { 0L }, cells)
    }

    // ---- mix ----

    @Test
    fun 섞음의_라벨은_attemptFault이고_빈_값은_none이다() {
        val r = good().analyze()
        @Suppress("UNCHECKED_CAST")
        val labels = (r["mix"] as Map<String, Any?>).keys
        assertEquals(
            setOf("none", "payment:fail-first=1", "payment:fail-first=9", "payment:slow-first=1@1500"),
            labels,
        )
    }

    @Test
    fun 섞음의_라벨마다_수_완료_결과_모름() {
        val r = good().inventoryRetried("x", T + 500, listOf("Unknown:REQUEST_TIMEOUT")).analyze()
        // none: a(완료), e(실패), x(결과 모름)
        assertEquals(listOf(3L, 1L, 1L), listOf("count", "completed", "unknown").map { r.long("mix", "none", it) })
    }

    @Test
    fun 섞음의_라벨마다_결제와_재고에_든_수의_합() {
        val r = good().analyze()
        assertEquals(listOf(3L, 1L), listOf("paymentArrivals", "inventoryArrivals").map { r.long("mix", "payment:fail-first=9", it) })
        assertEquals(listOf(2L, 2L), listOf("paymentArrivals", "inventoryArrivals").map { r.long("mix", "none", it) })
    }

    @Test
    fun 섞음의_라벨마다_경과_분위수() {
        val r = good().analyze()
        assertEquals(listOf(1.0, 1120.0), listOf("count", "max").map { r.double("mix", "payment:slow-first=1@1500", "elapsedMs", it) })
    }
}
