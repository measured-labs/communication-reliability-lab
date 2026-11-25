package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// docs/measurement.md 11-3 — 전송 대조(`analyzer transport`)가 늘 견주는 것 ①~⑦과 보고(report-transport.json·metrics-transport.tsv).
// 틀린 이유로 통과하지 않게: 기준 기록(gRPC·HTTP)이 종료 0·차이 줄 없음인 것을 먼저 지키고, 불일치 테스트마다
// 기준에서 한 가지만 바꿔 그 규칙의 차이 줄(문서의 꼴 그대로)을 본다.
// 이 파일은 분석기의 구현을 보지 않고 문서만으로 쓴다.
class TransportCompareTest {
    @TempDir
    lateinit var root: Path

    /** G2: gRPC, 기한 전파 켬, 취소 안 봄. 요청 a(T)·b(T+100) 둘 다 정상. */
    private fun grpc(propagation: Boolean = true, observe: Boolean = false) =
        TransportRecord(root, "grpc", propagation, observe).okChain("a", T).okChain("b", T + 100)

    /** H: HTTP. 요청 a·b 둘 다 정상, 새 칸 넷 가운데 transport 만 값(나머지 null). */
    private fun http() = TransportRecord(root, "http").okChain("a", T).okChain("b", T + 100)

    // ---- 기준 ----

    @Test
    fun gRPC_기준_기록은_종료_0이고_차이_줄이_없다() {
        assertEquals(emptyList(), grpc().expectCode(0))
    }

    @Test
    fun HTTP_기준_기록은_종료_0이고_차이_줄이_없다() {
        assertEquals(emptyList(), http().expectCode(0))
    }

    @Test
    fun 취소를_보는_조건에서_멈춘_기록은_통과한다() {
        // G3: 결제가 지연 중에 멈춤(status 0, stopped=cancelled-in-delay, cancelledUs 있음)
        val r = grpc(observe = true).change("payment", "a") {
            it.copy(status = 0, stopped = "cancelled-in-delay", cancelledUs = us(T + 10))
        }
        assertEquals(emptyList(), r.expectCode(0))
    }

    @Test
    fun 종료_0이면_report_transport_json과_metrics_transport_tsv를_쓴다() {
        val r = grpc()
        assertEquals(0, r.runTransport(), r.lastOutput)
        assertEquals(listOf(true, true), listOf(r.reportFile.exists(), r.tsvFile.exists()))
    }

    // ---- 보고의 칸 ----

    @Test
    fun 보고에는_고른_전송과_설정의_두_칸이_있다() {
        val r = grpc().also { it.runTransport() }.report()
        assertEquals(
            listOf<Any?>("grpc", true, false),
            listOf(r["chosen"], r.at("settings", "inventory", "deadlinePropagation"), r.at("settings", "payment", "observeCancellation")),
        )
    }

    @Test
    fun 보고의_counts는_전송마다_센_수와_처리_코드_전에_취소된_수다() {
        val rec = grpc().also { it.cancelledBeforeHandler["payment"] = 1 }
        assertEquals(0, rec.runTransport(), rec.lastOutput)
        val r = rec.report()
        assertEquals(
            listOf(2L, 0L, 2L, 2L, 3L, 0L, 1L, 0L),
            listOf(
                r.long("counts", "order", "inbound", "http"),
                r.long("counts", "order", "inbound", "grpc"),
                r.long("counts", "order", "outbound", "grpc"),
                r.long("counts", "inventory", "inbound", "grpc"),
                r.long("counts", "payment", "inbound", "grpc"),
                r.long("counts", "payment", "outbound", "grpc"),
                r.long("counts", "payment", "cancelledBeforeHandler", "grpc"),
                r.long("counts", "inventory", "cancelledBeforeHandler", "grpc"),
            ),
        )
    }

    @Test
    fun 보고의_records는_층마다_기록_수와_call_수와_취소를_안_수다() {
        val rec = grpc(observe = true).change("payment", "a") {
            it.copy(status = 0, stopped = "cancelled-in-delay", cancelledUs = us(T + 10))
        }
        assertEquals(0, rec.runTransport(), rec.lastOutput)
        val r = rec.report()
        assertEquals(
            listOf(2L, 2L, 2L, 0L, 0L, 0L, 1L, 1L),
            listOf(
                r.long("records", "order", "count"),
                r.long("records", "inventory", "calls"),
                r.long("records", "payment", "count"),
                r.long("records", "payment", "calls"),
                r.long("records", "payment", "wrongTransport"),
                r.long("records", "payment", "grpcFieldsOnHttp"),
                r.long("records", "payment", "stopped"),
                r.long("records", "payment", "cancelled"),
            ),
        )
    }

    @Test
    fun 결제가_받은_기한_빼기_재고가_받은_기한은_ms의_분위수다() {
        // a: 결제 − 재고 = −998ms(기준). b: 결제의 기한을 재고의 기한 + 30ms 로 → 30ms. p50 = −998, max = 30
        val rec = grpc().change("payment", "b") { it.copy(deadlineUs = us(T + 100 + 2 + 2000 + 30)) }
        assertEquals(0, rec.runTransport(), rec.lastOutput)
        val r = rec.report()
        assertEquals(
            listOf(2.0, -998.0, 30.0, 30.0, 30.0, 0.0, 0.0, 0.0),
            listOf("count", "p50", "p90", "p99", "max").map { r.double("deadline", "paymentAfterInventoryMs", it) } +
                listOf(
                    r.double("deadline", "paymentAfterInventoryOverSlack"),
                    r.double("deadline", "inventoryMissing"),
                    r.double("deadline", "paymentMissing"),
                ),
        )
    }

    @Test
    fun metrics_transport_tsv는_보고의_수를_편_것이고_문자열과_불은_없다() {
        val rec = grpc()
        assertEquals(0, rec.runTransport(), rec.lastOutput)
        val t = rec.tsv()
        assertEquals(
            listOf<String?>("2", "2", "0", "2"),
            listOf(
                t["counts.inventory.inbound.grpc"],
                t["records.payment.count"],
                t["deadline.paymentAfterInventoryOverSlack"],
                t["deadline.paymentAfterInventoryMs.count"],
            ),
        )
        assertEquals(-998.0, t.getValue("deadline.paymentAfterInventoryMs.p50").toDouble(), 1e-9)
        assertEquals(
            listOf(false, false),
            listOf("chosen" in t, "settings.inventory.deadlinePropagation" in t),
            "문자열(chosen)과 불(settings)은 tsv 에 없다(7-4와 같다): $t",
        )
    }

    @Test
    fun HTTP_실행의_기한_분위수는_null이고_tsv에서는_빈_값이다() {
        val rec = http()
        assertEquals(0, rec.runTransport(), rec.lastOutput)
        assertEquals(listOf(0.0, null), listOf("count", "p50").map { rec.report().double("deadline", "paymentAfterInventoryMs", it) })
        assertEquals("", rec.tsv()["deadline.paymentAfterInventoryMs.p50"])
    }

    // ---- ① 세 서비스의 고른 전송 ----

    @Test
    fun 세_서비스의_고른_전송이_다르면_불일치다() {
        val r = grpc().also { it.chosenOf["inventory"] = "http" }
        assertEquals(
            listOf("transport.chosen: 세 서비스가 같아야 한다, 실제 {order=grpc, inventory=http, payment=grpc}"),
            r.expectCode(2),
        )
    }

    // ---- ② 다른 전송(O)으로 센 수 ----

    private fun countOff(rec: TransportRecord, key: String, n: Long = 1): List<String> {
        rec.countDelta[key] = n
        return rec.expectCode(2)
    }

    @Test
    fun gRPC를_골랐는데_주문이_HTTP로_내보낸_호출이_있으면_불일치다() {
        assertEquals(listOf("counts.order.outbound.http: 늘 0(고른 전송 grpc), 실제 1"), countOff(grpc(), "order.outbound.http"))
    }

    @Test
    fun gRPC를_골랐는데_재고가_HTTP로_받은_호출이_있으면_불일치다() {
        assertEquals(listOf("counts.inventory.inbound.http: 늘 0(고른 전송 grpc), 실제 1"), countOff(grpc(), "inventory.inbound.http"))
    }

    @Test
    fun gRPC를_골랐는데_재고가_HTTP로_내보낸_호출이_있으면_불일치다() {
        assertEquals(listOf("counts.inventory.outbound.http: 늘 0(고른 전송 grpc), 실제 1"), countOff(grpc(), "inventory.outbound.http"))
    }

    @Test
    fun gRPC를_골랐는데_결제가_HTTP로_받은_호출이_있으면_불일치다() {
        assertEquals(listOf("counts.payment.inbound.http: 늘 0(고른 전송 grpc), 실제 2"), countOff(grpc(), "payment.inbound.http", 2))
    }

    @Test
    fun HTTP를_골랐는데_재고가_gRPC로_받은_호출이_있으면_불일치다() {
        assertEquals(listOf("counts.inventory.inbound.grpc: 늘 0(고른 전송 http), 실제 1"), countOff(http(), "inventory.inbound.grpc"))
    }

    @Test
    fun HTTP를_골랐는데_주문이_gRPC로_내보낸_호출이_있으면_불일치다() {
        assertEquals(listOf("counts.order.outbound.grpc: 늘 0(고른 전송 http), 실제 1"), countOff(http(), "order.outbound.grpc"))
    }

    @Test
    fun 주문이_gRPC로_받은_호출은_늘_0이다() {
        assertEquals(
            listOf("counts.order.inbound.grpc: 늘 0(부하 도구와 주문 사이는 HTTP), 실제 1"),
            countOff(grpc(), "order.inbound.grpc"),
        )
    }

    @Test
    fun 결제가_내보낸_호출은_늘_0이다() {
        assertEquals(
            listOf("counts.payment.outbound: 늘 0(맨 아래 층), 실제 {http=0, grpc=1}"),
            countOff(grpc(), "payment.outbound.grpc"),
        )
    }

    // ---- ③ 시각 기록의 transport ----

    @Test
    fun 주문의_기록이_http가_아니면_불일치다() {
        val r = grpc().change("order", "a") { it.copy(transport = "grpc") }
        assertEquals(listOf("records.order.wrongTransport: 늘 0, 실제 1"), r.expectCode(2))
    }

    @Test
    fun HTTP를_골랐는데_재고의_기록이_grpc면_불일치다() {
        val r = http().change("inventory", "b") { it.copy(transport = "grpc") }
        assertEquals(listOf("records.inventory.wrongTransport: 늘 0, 실제 1"), r.expectCode(2))
    }

    @Test
    fun gRPC를_골랐는데_결제의_기록이_http면_불일치다() {
        // 기한 칸도 지운다(HTTP로 받은 기록의 꼴) — ⑤의 deadline 줄이 함께 나올 수 있으므로 이 줄이 있는지만 본다
        val r = grpc().change("payment", "a") { it.copy(transport = "http", deadlineUs = null) }
        val d = r.expectCode(2)
        assertTrue("records.payment.wrongTransport: 늘 0, 실제 1" in d, "차이 줄: $d")
    }

    // ---- ④ 센 수와 기록 수 ----

    @Test
    fun 재고가_받은_수가_기록_수보다_많으면_불일치다() {
        assertEquals(
            listOf("counts.inventory.inbound.grpc: 시각 기록 수 2 + 처리 코드에 닿기 전에 취소된 수 0 와 같아야 한다, 실제 3"),
            countOff(grpc(), "inventory.inbound.grpc"),
        )
    }

    @Test
    fun 결제가_받은_수가_기록_수보다_적으면_불일치다() {
        assertEquals(
            listOf("counts.payment.inbound.grpc: 시각 기록 수 2 + 처리 코드에 닿기 전에 취소된 수 0 와 같아야 한다, 실제 1"),
            countOff(grpc(), "payment.inbound.grpc", -1),
        )
    }

    @Test
    fun 받은_수가_기록_수_더하기_처리_코드_전에_취소된_수면_통과한다() {
        val r = grpc().also { it.cancelledBeforeHandler["payment"] = 1 } // 결제가 받은 수 = 2 + 1
        assertEquals(emptyList(), r.expectCode(0))
    }

    @Test
    fun 처리_코드_전에_취소된_수를_받은_수에서_빼지_못하면_불일치다() {
        // 결제 cbh 1, 받은 수는 기록 수 그대로 2 → 2 + 1 이어야 한다
        val r = grpc().also { it.cancelledBeforeHandler["payment"] = 1; it.countDelta["payment.inbound.grpc"] = -1 }
        assertEquals(
            listOf("counts.payment.inbound.grpc: 시각 기록 수 2 + 처리 코드에 닿기 전에 취소된 수 1 와 같아야 한다, 실제 2"),
            r.expectCode(2),
        )
    }

    @Test
    fun 주문의_처리_코드_전에_취소된_수는_늘_0이다() {
        val r = grpc().also { it.cancelledBeforeHandler["order"] = 1 }
        assertEquals(listOf("counts.order.cancelledBeforeHandler.grpc: gRPC로 받지 않는 서비스에서 늘 0, 실제 1"), r.expectCode(2))
    }

    @Test
    fun HTTP를_골랐으면_재고의_처리_코드_전에_취소된_수는_늘_0이다() {
        val r = http().also { it.cancelledBeforeHandler["inventory"] = 2 }
        assertEquals(listOf("counts.inventory.cancelledBeforeHandler.grpc: gRPC로 받지 않는 서비스에서 늘 0, 실제 2"), r.expectCode(2))
    }

    @Test
    fun HTTP를_골랐는데_주문이_받은_수가_기록_수와_다르면_불일치다() {
        val d = countOff(http(), "order.inbound.http")
        assertEquals(1, d.size, "차이 줄: $d")
        assertTrue(d[0].startsWith("counts.order.inbound.http: 시각 기록 수 2") && d[0].endsWith("실제 3"), "차이 줄: $d")
    }

    @Test
    fun 주문이_내보낸_수가_기록의_call_수와_다르면_불일치다() {
        assertEquals(listOf("counts.order.outbound.grpc: 기록의 call 수 2와 같아야 한다, 실제 3"), countOff(grpc(), "order.outbound.grpc"))
    }

    @Test
    fun 재고가_내보낸_수가_기록의_call_수와_다르면_불일치다() {
        assertEquals(
            listOf("counts.inventory.outbound.grpc: 기록의 call 수 2와 같아야 한다, 실제 1"),
            countOff(grpc(), "inventory.outbound.grpc", -1),
        )
    }

    @Test
    fun HTTP_실행에서도_재고가_내보낸_수를_견준다() {
        assertEquals(
            listOf("counts.inventory.outbound.http: 기록의 call 수 2와 같아야 한다, 실제 3"),
            countOff(http(), "inventory.outbound.http"),
        )
    }

    // ---- ⑤ gRPC 칸 ----

    @Test
    fun 주문의_기록에_기한_칸이_있으면_불일치다() {
        val r = grpc().change("order", "a") { it.copy(deadlineUs = us(T + 5000)) }
        assertEquals(listOf("records.order.grpcFieldsOnHttp: 늘 0, 실제 1"), r.expectCode(2))
    }

    @Test
    fun 주문의_기록에_취소_칸이_있으면_불일치다() {
        val r = grpc().change("order", "b") { it.copy(cancelledUs = us(T + 110)) }
        assertEquals(listOf("records.order.grpcFieldsOnHttp: 늘 0, 실제 1"), r.expectCode(2))
    }

    @Test
    fun HTTP를_골랐는데_재고의_기록에_기한_칸이_있으면_불일치다() {
        val r = http().change("inventory", "a") { it.copy(deadlineUs = us(T + 3000)) }
        assertEquals(listOf("records.inventory.grpcFieldsOnHttp: 늘 0, 실제 1"), r.expectCode(2))
    }

    @Test
    fun HTTP를_골랐는데_결제의_기록에_멈춘_자리가_있으면_grpcFieldsOnHttp다() {
        // 취소를 보지 않는 조건이라 ⑦ 줄도 나올 수 있다 — 이 줄이 있는지만 본다
        val r = http().change("payment", "a") { it.copy(status = 0, stopped = "deadline-before-start") }
        val d = r.expectCode(2)
        assertTrue("records.payment.grpcFieldsOnHttp: 늘 0, 실제 1" in d, "차이 줄: $d")
    }

    @Test
    fun gRPC인데_재고의_기록에_기한이_없으면_불일치다() {
        val r = grpc().change("inventory", "a") { it.copy(deadlineUs = null) }
        assertEquals(listOf("deadline.inventory.missing: 늘 0(gRPC), 실제 1"), r.expectCode(2))
    }

    @Test
    fun gRPC인데_결제의_기록에_기한이_없으면_불일치다() {
        val r = grpc().change("payment", "b") { it.copy(deadlineUs = null) }
        assertEquals(listOf("deadline.payment.missing: 늘 0(gRPC), 실제 1"), r.expectCode(2))
    }

    @Test
    fun 기한이_없는_기록_수는_보고의_deadline에_있다() {
        val rec = grpc().change("payment", "b") { it.copy(deadlineUs = null) }
        rec.runTransport()
        assertEquals(listOf(0L, 1L), listOf(rec.report().long("deadline", "inventoryMissing"), rec.report().long("deadline", "paymentMissing")))
    }

    // ---- ⑥ 기한 전파 ----

    /** a 의 결제 기한 = 재고 기한 + [lateUs]μs */
    private fun paymentLate(rec: TransportRecord, lateUs: Long) =
        rec.change("payment", "a") { it.copy(deadlineUs = us(T + 2 + 2000) + lateUs) }

    @Test
    fun 기한_전파를_켰는데_결제의_기한이_재고보다_50ms_넘게_늦으면_불일치다() {
        val r = paymentLate(grpc(), us(51))
        assertEquals(listOf("deadline.paymentAfterInventoryOverSlack: 기한 전파를 켰으면 늘 0, 실제 1"), r.expectCode(2))
    }

    @Test
    fun 결제의_기한이_재고보다_정확히_50ms_늦으면_통과한다() {
        assertEquals(emptyList(), paymentLate(grpc(), us(50)).expectCode(0))
    }

    @Test
    fun 결제의_기한이_재고보다_50ms와_1마이크로초_늦으면_불일치다() {
        val r = paymentLate(grpc(), us(50) + 1)
        assertEquals(listOf("deadline.paymentAfterInventoryOverSlack: 기한 전파를 켰으면 늘 0, 실제 1"), r.expectCode(2))
    }

    @Test
    fun 기한_전파를_껐으면_결제의_기한이_늦어도_불일치가_아니고_수는_보고에_남는다() {
        // G1·P2 꼴: 결제의 기한이 재고보다 1000ms 늦다
        val rec = paymentLate(grpc(propagation = false), us(1000))
        assertEquals(emptyList(), rec.expectCode(0))
        assertEquals(1L, rec.report().long("deadline", "paymentAfterInventoryOverSlack"))
    }

    @Test
    fun 전파_여부는_재고의_설정으로_본다() {
        // 주문·결제는 켬, 재고만 끔 → ⑥은 견주지 않는다
        val rec = paymentLate(grpc(), us(1000)).also { it.propagationOf["inventory"] = false }
        assertEquals(emptyList(), rec.expectCode(0))
    }

    @Test
    fun 재고만_전파를_켰어도_견준다() {
        val rec = paymentLate(grpc(propagation = false), us(1000)).also { it.propagationOf["inventory"] = true }
        assertEquals(listOf("deadline.paymentAfterInventoryOverSlack: 기한 전파를 켰으면 늘 0, 실제 1"), rec.expectCode(2))
    }

    // ---- ⑦ 멈춘 기록 ----

    @Test
    fun 취소를_보지_않는_서비스의_기록에_멈춘_자리가_있으면_불일치다() {
        val r = grpc().change("payment", "a") { it.copy(status = 0, stopped = "cancelled-in-delay", cancelledUs = us(T + 10)) }
        assertEquals(listOf("records.payment.stopped: 취소를 보지 않는 조건에서 늘 0, 실제 1"), r.expectCode(2))
    }

    @Test
    fun 멈춘_자리는_서비스마다_그_서비스의_설정으로_본다() {
        // 결제는 취소를 보고 재고는 보지 않는데, 재고의 기록이 멈췄다
        val r = grpc().also { it.observeOf["payment"] = true }
            .change("inventory", "b") {
                GrpcTiming("inventory", "b", us(T + 103), us(T + 104), 0, transport = "grpc", deadlineUs = it.deadlineUs, stopped = "deadline-before-start")
            }
        // 재고 b 는 결제를 부르지 않았으므로 기록의 call 수가 1이다 — 내보낸 수는 기록에서 세므로(TransportRecord) 1로 맞는다
        assertEquals(listOf("records.inventory.stopped: 취소를 보지 않는 조건에서 늘 0, 실제 1"), r.expectCode(2))
    }

    // ---- 차례 ----

    @Test
    fun 차이_줄은_규칙의_차례로_나온다() {
        // ⑦(결제 멈춤) · ③(주문 기록 grpc) · ②(재고가 HTTP로 받음)를 거꾸로 넣어도 ② → ③ → ⑦
        val r = grpc()
            .change("payment", "a") { it.copy(status = 0, stopped = "cancelled-in-delay") }
            .change("order", "b") { it.copy(transport = "grpc") }
        r.countDelta["inventory.inbound.http"] = 1
        assertEquals(
            listOf(
                "counts.inventory.inbound.http: 늘 0(고른 전송 grpc), 실제 1",
                "records.order.wrongTransport: 늘 0, 실제 1",
                "records.payment.stopped: 취소를 보지 않는 조건에서 늘 0, 실제 1",
            ),
            r.expectCode(2),
        )
    }

    // ---- 기대 파일 ----

    // 11-3은 기대 파일의 차이 줄이 어디에 나오는지 적지 않는다(transportReport 는 늘 견주는 것의 차이 줄) — 종료 코드만 본다.

    @Test
    fun 기대_파일의_범위_밖이면_종료_2() {
        val e = writeExpect(root, """{"records.payment.count":{"min":5,"max":5}}""", "expect.transport.json")
        val r = grpc()
        assertEquals(2, r.runTransport("--expect", e.toString()), r.lastOutput)
    }

    @Test
    fun 기대_파일의_범위_안이면_종료_0() {
        val e = writeExpect(root, """{"records.payment.count":{"min":2,"max":2},"deadline.paymentAfterInventoryOverSlack":{"min":0,"max":0}}""", "expect.transport.json")
        val r = grpc()
        assertEquals(0, r.runTransport("--expect", e.toString()), r.lastOutput)
    }
}
