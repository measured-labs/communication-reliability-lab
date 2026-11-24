package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// docs/measurement.md 8-5 — 늘 견주는 것(① 서버에 닿지 못한 요청 ② 넣은 실패의 수), 기대 파일(7-4절과 같은 꼴),
// 종료 코드, metrics-retry.tsv(수와 null 만 — series 는 없다).
class RetryJudgeTest {
    @TempDir
    lateinit var root: Path

    private fun good() = goodRetryRecord(root)

    // ---- ① 서버에 닿지 못한 요청 ----

    @Test
    fun 서버에_닿지_못한_요청이_있으면_불일치다() {
        // g: 응답 없음(status 0), 시각 기록·행 없음
        val r = good().answer("g", T + 500, 0, elapsedMs = 30)
        assertEquals(2, r.runRetry(), r.lastOutput)
        assertEquals(listOf("clients.notReachedServer: 늘 0, 실제 1"), r.report().differences())
    }

    @Test
    fun 응답이_없어도_주문의_시각_기록이_있으면_닿은_것이다() {
        // h: k6 는 응답을 받지 못했지만(status 0) 주문·재고·결제에 들어 처리됐다
        val r = good()
        r.timing("order", "h", us(T + 501), us(T + 519), 201, us(T + 502), us(T + 518), "Completed")
            .timing("inventory", "h", us(T + 503), us(T + 517), 201, us(T + 504), us(T + 514), "Completed")
            .timing("payment", "h", us(T + 505), us(T + 513), 201)
            .attempt("inventory", Attempt("h", r.nextCallId("inventory"), 1, us(T + 504), us(T + 514), "Completed", 0, "success"))
            .row("order", "h", "COMPLETED").row("inventory", "h", "RESERVED").row("payment", "h", "APPROVED")
            .answer("h", T + 500, 0, elapsedMs = 60000)
        val rep = r.analyze()
        assertEquals(1L, rep.long("clients", "noResponse"))
        assertEquals(0L, rep.long("clients", "notReachedServer"))
    }

    @Test
    fun 응답을_받았으면_시각_기록이_없어도_닿지_못한_요청이_아니다() {
        // g: 502 를 받았는데 시각 기록이 없다(예: 앞단에서 끊김) — 응답 없음이 아니므로 세지 않는다
        val rep = good().answer("g", T + 500, 502, elapsedMs = 30).analyze()
        assertEquals(0L, rep.long("clients", "noResponse"))
        assertEquals(0L, rep.long("clients", "notReachedServer"))
    }

    @Test
    fun 응답_없음의_수와_닿지_못한_수() {
        val rep = good().answer("g1", T + 500, 0, elapsedMs = 30).answer("g2", T + 600, 0, elapsedMs = 30).analyzeAny()
        assertEquals(2L, rep.long("clients", "noResponse"))
        assertEquals(2L, rep.long("clients", "notReachedServer"))
    }

    // ---- ② 넣은 실패의 수 ----

    @Test
    fun 서비스가_센_수와_기록에서_다시_센_수가_다르면_불일치다() {
        val r = good().counted("payment", "fail-first", 3)
        assertEquals(2, r.runRetry(), r.lastOutput)
        assertEquals(listOf("attemptFaults.payment.fail-first: 서비스가 센 수 3, 기록에서 다시 센 수 4"), r.report().differences())
    }

    @Test
    fun 가리키지_않은_서비스가_실패를_셌어도_불일치다() {
        val r = good().counted("order", "outage", 1)
        assertEquals(2, r.runRetry(), r.lastOutput)
        assertEquals(listOf("attemptFaults.order.outage: 서비스가 센 수 1, 기록에서 다시 센 수 0"), r.report().differences())
    }

    @Test
    fun 넣은_실패의_불일치는_서비스_차례_다음_종류_차례다() {
        val r = good()
            .counted("payment", "slow-first", 0)
            .counted("payment", "fail-first", 5)
            .counted("inventory", "outage", 2)
        assertEquals(2, r.runRetry(), r.lastOutput)
        assertEquals(
            listOf(
                "attemptFaults.inventory.outage: 서비스가 센 수 2, 기록에서 다시 센 수 0",
                "attemptFaults.payment.fail-first: 서비스가 센 수 5, 기록에서 다시 센 수 4",
                "attemptFaults.payment.slow-first: 서비스가 센 수 0, 기록에서 다시 센 수 1",
            ),
            r.report().differences(),
        )
    }

    @Test
    fun 닿지_못한_요청이_넣은_실패보다_먼저_나온다() {
        val r = good().counted("payment", "fail-first", 3).answer("g", T + 500, 0, elapsedMs = 30)
        assertEquals(2, r.runRetry(), r.lastOutput)
        assertEquals(
            listOf(
                "clients.notReachedServer: 늘 0, 실제 1",
                "attemptFaults.payment.fail-first: 서비스가 센 수 3, 기록에서 다시 센 수 4",
            ),
            r.report().differences(),
        )
    }

    // ---- ③ DB 행은 있는데 그 층의 시각 기록이 없는 requestId ----

    @Test
    fun DB_행은_있는데_그_층의_시각_기록이_없으면_불일치다() {
        // n: 502 를 받았고 주문 행(FAILED)은 있는데 어느 층에도 시각 기록이 없다
        val r = good().answer("n", T + 500, 502, elapsedMs = 30).row("order", "n", "FAILED")
        assertEquals(2, r.runRetry(), r.lastOutput)
        assertEquals(listOf("layers.order: DB 행은 있는데 시각 기록이 없는 requestId 1건([n])"), r.report().differences())
    }

    @Test
    fun 다른_층의_시각_기록이_있어도_그_층의_것이_없으면_불일치다() {
        // a 의 결제 시각 기록을 빼지 않고, 결제 행만 있는 새 요청 m(주문·재고 기록은 있다)
        val r = good()
        r.timing("order", "m", us(T + 501), us(T + 519), 201, us(T + 502), us(T + 518), "Completed")
            .timing("inventory", "m", us(T + 503), us(T + 517), 201, us(T + 504), us(T + 514), "Completed")
            .attempt("inventory", Attempt("m", r.nextCallId("inventory"), 1, us(T + 504), us(T + 514), "Completed", 0, "success"))
            .row("order", "m", "COMPLETED").row("inventory", "m", "RESERVED").row("payment", "m", "APPROVED")
            .answer("m", T + 500, 201)
        // 결제에 든 기록이 없으므로 재고→결제의 attemptsNotArrived 도 1 — 두 줄, ③이 ④보다 앞
        assertEquals(2, r.runRetry(), r.lastOutput)
        assertEquals(
            listOf(
                "layers.payment: DB 행은 있는데 시각 기록이 없는 requestId 1건([m])",
                "edges.inventory->payment.attemptsNotArrived: 늘 0, 실제 1",
            ),
            r.report().differences(),
        )
    }

    @Test
    fun 시각_기록이_없는_requestId가_여럿이면_건수를_적는다() {
        val r = good()
        listOf("n1", "n2", "n3", "n4").forEachIndexed { i, id ->
            r.answer(id, T + 500 + i, 502, elapsedMs = 30).row("order", id, "FAILED")
        }
        assertEquals(2, r.runRetry(), r.lastOutput)
        val d = r.report().differences()
        assertEquals(1, d.size, "$d")
        assertTrue(d[0].startsWith("layers.order: DB 행은 있는데 시각 기록이 없는 requestId 4건(["), d[0])
    }

    @Test
    fun 시각_기록이_빠진_층은_order_inventory_payment_차례로_적는다() {
        val r = good()
            .answer("p1", T + 500, 502, elapsedMs = 30).row("payment", "p1", "APPROVED")
            .answer("o1", T + 510, 502, elapsedMs = 30).row("order", "o1", "FAILED")
        assertEquals(2, r.runRetry(), r.lastOutput)
        val d = r.report().differences()
        assertEquals(
            listOf(
                "layers.order: DB 행은 있는데 시각 기록이 없는 requestId 1건([o1])",
                "layers.payment: DB 행은 있는데 시각 기록이 없는 requestId 1건([p1])",
            ),
            d,
        )
    }

    @Test
    fun 시각_기록이_통째로_비면_불일치다() {
        // R03-4: 결제의 시각 기록을 [] 로 바꾼 기록이 통과하면 안 된다
        val r = good().apply { timingsText["payment"] = "[]" }
        assertEquals(2, r.runRetry(), r.lastOutput)
        assertTrue(r.report().differences().any { it.startsWith("layers.payment: DB 행은 있는데 시각 기록이 없는 requestId 3건(") }, r.report().differences().toString())
    }

    // ---- ④ 재시도 설정과 시도 기록 ----

    @Test
    fun 재시도가_켜져_있는데_시도_기록이_비면_불일치다() {
        val r = good().apply { retryEnabled["order"] = true }
        assertEquals(2, r.runRetry(), r.lastOutput)
        assertEquals(
            listOf("edges.order->inventory: 재시도가 켜져 있는데(settings-order.json) 시도 기록이 없다"),
            r.report().differences(),
        )
    }

    @Test
    fun 재시도가_꺼져_있는데_시도_기록이_있으면_불일치다() {
        // 기준 기록의 재고 시도 기록 9건
        val r = good().apply { retryEnabled["inventory"] = false }
        assertEquals(2, r.runRetry(), r.lastOutput)
        assertEquals(
            listOf("edges.inventory->payment: 재시도가 꺼져 있는데(settings-inventory.json) 시도 기록이 9건"),
            r.report().differences(),
        )
    }

    @Test
    fun 재시도가_꺼져_있고_시도_기록이_비면_받는다() {
        // 기준 기록의 주문이 그렇다 — 둘 다 꺼짐, 재고 기록도 비움
        val r = goodRetryRecord(root).apply {
            retryEnabled["inventory"] = false
            attempts.getValue("inventory").clear()
        }
        assertEquals(0, r.runRetry(), r.lastOutput)
    }

    @Test
    fun 재시도_설정의_불일치는_부르는_층의_차례다() {
        val r = good().apply { retryEnabled["order"] = true }
            .inventoryRetried("x", T + 500, listOf("Failed:CONNECT_REFUSED", "Completed"))
        assertEquals(2, r.runRetry(), r.lastOutput)
        assertEquals(
            listOf(
                "edges.order->inventory: 재시도가 켜져 있는데(settings-order.json) 시도 기록이 없다",
                "edges.inventory->payment.attemptsNotArrived: 늘 0, 실제 1",
            ),
            r.report().differences(),
        )
    }

    // ---- 늘 견주는 것의 차례 ①~④, 그 뒤 기대 ----

    @Test
    fun 늘_견주는_것은_1_2_3_4_차례_그_뒤에_기대_파일이다() {
        val e = writeExpect(root, """{"clients.completed": {"min": 0, "max": 0}}""")
        val r = good()
            .counted("payment", "fail-first", 3) // ②
            .answer("n", T + 500, 502, elapsedMs = 30).row("order", "n", "FAILED") // ③
            .inventoryRetried("x", T + 600, listOf("Failed:CONNECT_REFUSED", "Completed")) // ④
            .answer("g", T + 700, 0, elapsedMs = 30) // ①
        assertEquals(2, r.runRetry("--expect", e.toString()), r.lastOutput)
        assertEquals(
            listOf(
                "clients.notReachedServer: 늘 0, 실제 1",
                "attemptFaults.payment.fail-first: 서비스가 센 수 3, 기록에서 다시 센 수 4",
                "layers.order: DB 행은 있는데 시각 기록이 없는 requestId 1건([n])",
                "edges.inventory->payment.attemptsNotArrived: 늘 0, 실제 1",
                "clients.completed: 기대 0~0, 실제 4",
            ),
            r.report().differences(),
        )
    }

    // ---- 구간 지시가 없는 실행 ----

    @Test
    fun 구간_장애의_지시가_없으면_outage_키가_없다() {
        val g = good()
        val r = g.analyze()
        assertFalse("outage" in r, "outage 키가 있다")
        assertFalse(g.metricsTsv.readLines().any { it.startsWith("outage.") })
    }

    @Test
    fun 구간_장애의_지시가_없는데_outage를_기대하면_실제는_없음이다() {
        val e = writeExpect(root, """{"outage.retryTailMs": {"min": 0, "max": 100}}""")
        val r = good()
        assertEquals(2, r.runRetry("--expect", e.toString()), r.lastOutput)
        assertEquals(listOf("outage.retryTailMs: 기대 0~100, 실제 없음"), r.report().differences())
    }

    // ---- 기대 파일 ----

    @Test
    fun 기대_범위_안이면_통과한다() {
        val e = writeExpect(root, """{"layers.payment.arrivals": {"min": 9, "max": 9}, "edges.inventory->payment.retries": {"min": 0, "max": 10}}""")
        val r = good()
        assertEquals(0, r.runRetry("--expect", e.toString()), r.lastOutput)
        assertEquals(emptyList<String>(), r.report().differences())
    }

    @Test
    fun 기대_범위_밖이면_불일치이고_7_4절의_꼴로_적는다() {
        val e = writeExpect(root, """{"layers.payment.arrivals": {"min": 10, "max": 12}}""")
        val r = good()
        assertEquals(2, r.runRetry("--expect", e.toString()), r.lastOutput)
        assertEquals(listOf("layers.payment.arrivals: 기대 10~12, 실제 9"), r.report().differences())
    }

    @Test
    fun 기대의_정수는_소수점_없이_실제의_소수는_그대로_적는다() {
        val e = writeExpect(root, """{"layers.payment.arrivalsPerRequest": {"min": 2.0, "max": 3.0}}""")
        val r = good()
        assertEquals(2, r.runRetry("--expect", e.toString()), r.lastOutput)
        assertEquals(listOf("layers.payment.arrivalsPerRequest: 기대 2~3, 실제 1.8"), r.report().differences())
    }

    @Test
    fun 없는_지표를_기대하면_실제는_없음이다() {
        val e = writeExpect(root, """{"edges.inventory->payment.callsByAttempts.7": {"min": 0, "max": 1}}""")
        val r = good()
        assertEquals(2, r.runRetry("--expect", e.toString()), r.lastOutput)
        assertEquals(listOf("edges.inventory->payment.callsByAttempts.7: 기대 0~1, 실제 없음"), r.report().differences())
    }

    @Test
    fun 기대의_차이는_늘_견주는_것_뒤에_기대_파일의_차례로_나온다() {
        val e = writeExpect(
            root,
            """{"layers.payment.rows": {"min": 0, "max": 0}, "clients.completed": {"min": 0, "max": 0}}""",
        )
        val r = good().counted("payment", "fail-first", 3)
        assertEquals(2, r.runRetry("--expect", e.toString()), r.lastOutput)
        assertEquals(
            listOf(
                "attemptFaults.payment.fail-first: 서비스가 센 수 3, 기록에서 다시 센 수 4",
                "layers.payment.rows: 기대 0~0, 실제 4",
                "clients.completed: 기대 0~0, 실제 3",
            ),
            r.report().differences(),
        )
    }

    // ---- 출력 ----

    @Test
    fun metrics_tsv는_이름_탭_값이다() {
        val r = good()
        r.analyze()
        val lines = r.metricsTsv.readLines()
        assertTrue("layers.payment.arrivals\t9" in lines, "줄이 없다: ${lines.take(20)}")
        assertTrue("edges.inventory->payment.decisions.budget-denied\t0" in lines)
    }

    @Test
    fun metrics_tsv의_null은_빈_값이다() {
        val r = good()
        r.analyze()
        assertTrue("edges.order->inventory.gapMinusDelayMs.p50\t" in r.metricsTsv.readLines())
    }

    @Test
    fun metrics_tsv에는_series가_없다() {
        val r = good()
        r.analyze()
        assertFalse(r.metricsTsv.readLines().any { it.startsWith("series") })
    }

    @Test
    fun 보고의_run은_실행_폴더의_이름이다() {
        assertEquals("retry-20261124T000000-r1", good().analyze()["run"])
    }
}
