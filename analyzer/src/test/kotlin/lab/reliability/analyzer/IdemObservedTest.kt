package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// docs/measurement.md 9-4 dropFaults.<서비스>.observedAbove(세 출처 — 주문: status 0 인 보낸 것, 재고·결제: 부르는 층의 재시도가
// 켜져 있으면 시도 기록의 outcome, 꺼져 있으면 시각 기록의 callOutcome 가운데 `Unknown:IO_`), 9-5 ②의 그 대조,
// reconcile.candidates·notResent·resentNotCandidate 와 9-5 ⑥, 설정의 retry.enabled 와 재시도가 켜진 층의 시도 기록(9-2·9-3).
class IdemObservedTest {
    @TempDir
    lateinit var root: Path

    private fun good() = goodIdemRecord(root)

    // ---- observedAbove ----

    @Test
    fun 기준_기록의_위_층이_본_끊김() {
        // 주문: status 0 인 보낸 것 = f 의 정답 1 · 재고: 주문의 callOutcome 에 IO_ 없음 0 · 결제: 재고의 callOutcome IO_ = c 1
        val rep = good().analyze()
        assertEquals(listOf(1L, 0L, 1L), LAYERS.map { rep.long("dropFaults", it, "observedAbove") })
    }

    @Test
    fun 주문의_끊김은_다시_보낸_줄의_status_0도_센다() {
        // f 의 다시 보낸 줄도 status 0 — 위 층이 본 끊김 2, 센 수 2(기록에서 다시 센 수는 min(1, 2) = 1)
        val r = good()
        r.resendLines!!.replaceAll { if (it.contains("\"requestId\":\"f\"")) it.replace("\"status\":201", "\"status\":0") else it }
        r.drops("order", 2)
        val rep = r.analyzeAny()
        assertEquals(2L, rep.long("dropFaults", "order", "observedAbove"))
        assertEquals(listOf("dropFaults.order: 서비스가 센 수 2, 기록에서 다시 센 수 1"), rep.differences())
    }

    @Test
    fun 응답_있는_실패는_주문의_끊김이_아니다() {
        // g: 502 를 받은 요청 — status 0 이 아니므로 세지 않는다
        val r = good()
        val g = T + 600
        r.answer("g", g, 502).timing("order", "g", us(g + 1), us(g + 9), 502, us(g + 2), us(g + 8), "Failed:HTTP_500")
            .row("order", "g", "FAILED").key("order", "g", "FAILED", 502).counts("order", "new", 7)
        assertEquals(1L, r.analyze().long("dropFaults", "order", "observedAbove"))
    }

    /**
     * 재고를 끊는 요청 g 하나(재고 n=1). 주문 0~40(호출 2~39, 마지막 결과 Completed), 재고 첫 기록 3~15 status 0(결제 5~13 처리),
     * 재고 둘째 기록 20~22 201(저장한 응답). 주문의 재시도가 켜져 있으면 시도 기록: 1) 2~16 Unknown:IO_IOException retry
     * 2) 19~23 Completed success(간격 3).
     */
    private fun inventoryDrop(orderRetry: Boolean, orderCallOutcome: String = "Completed"): IdemRecord {
        val r = IdemRecord(root)
        r.retryEnabled["order"] = orderRetry
        r.answer("g", T, 201, dropFault = "inventory:drop-after-commit=1", elapsedMs = 45)
            .timing("order", "g", us(T), us(T + 40), 201, us(T + 2), us(T + 39), orderCallOutcome)
            .timing("inventory", "g", us(T + 3), us(T + 15), 0, us(T + 4), us(T + 14), "Completed")
            .timing("payment", "g", us(T + 5), us(T + 13), 201)
            .timing("inventory", "g", us(T + 20), us(T + 22), 201)
            .processedRows("g").keys("g")
            .counts("order", "new", 1).counts("inventory", "new", 1).counts("inventory", "replayed", 1).counts("payment", "new", 1)
            .drops("inventory", 1)
        if (orderRetry) {
            r.attempt("order", Attempt("g", 1, 1, us(T + 2), us(T + 16), "Unknown:IO_IOException", 0, "retry"))
                .attempt("order", Attempt("g", 1, 2, us(T + 19), us(T + 23), "Completed", 3, "success"))
        }
        return r
    }

    @Test
    fun 주문의_재시도가_켜져_있으면_재고의_끊김은_주문의_시도_기록으로_센다() {
        val r = inventoryDrop(orderRetry = true)
        assertEquals(0, r.runIdem(), r.lastOutput)
        assertEquals(1L, r.report().long("dropFaults", "inventory", "observedAbove"))
    }

    @Test
    fun 주문의_재시도가_꺼져_있으면_재고의_끊김은_주문의_callOutcome으로_센다() {
        // 같은 기록에서 재시도만 끈다 — 시각 기록의 callOutcome(Completed)에는 끊김이 없다
        val d = inventoryDrop(orderRetry = false).mismatch()
        assertEquals(listOf("dropFaults.inventory: 서비스가 센 수 1, 위 층이 본 끊김 0"), d)
    }

    @Test
    fun 재시도가_꺼져_있으면_callOutcome의_IO가_끊김이다() {
        val r = inventoryDrop(orderRetry = false, orderCallOutcome = "Unknown:IO_IOException")
        // 주문이 결과를 몰랐으므로 정답은 504 — 정답 줄을 그것으로 바꿔 쓴다
        r.base.answersText = r.answerLine("answer", "g", T, 504, dropFault = "inventory:drop-after-commit=1", elapsedMs = 45) + "\n"
        val rep = r.analyzeAny()
        assertEquals(1L, rep.long("dropFaults", "inventory", "observedAbove"))
        assertTrue(rep.differences().none { it.startsWith("dropFaults.inventory") }, rep.differences().toString())
    }

    @Test
    fun 재시도가_켜져_있으면_callOutcome이_아니라_시도_기록만_본다() {
        // 시도 기록에는 IO_ 가 없고(첫 시도 성공) 시각 기록의 callOutcome 에만 IO_ 가 있다 — 0
        val r = inventoryDrop(orderRetry = false, orderCallOutcome = "Unknown:IO_IOException")
        r.retryEnabled["order"] = true
        r.attempt("order", Attempt("g", 1, 1, us(T + 2), us(T + 39), "Completed", 0, "success"))
        val rep = r.analyzeAny()
        assertEquals(0L, rep.long("dropFaults", "inventory", "observedAbove"))
    }

    // ---- 설정과 시도 기록의 입력 ----

    @Test
    fun 주문_설정에_retry_enabled가_없으면_입력_오류다() {
        val r = good()
        r.settingsText["order"] = """{"idempotency":{"enabled":true}}"""
        assertTrue(r.assertInputError().contains("settings-order.json"), r.lastOutput)
    }

    @Test
    fun 재고_설정의_retry_enabled가_불이_아니면_입력_오류다() {
        val r = good()
        r.settingsText["inventory"] = """{"idempotency":{"enabled":true},"retry":{"enabled":"false"}}"""
        r.assertInputError()
    }

    @Test
    fun 재시도가_켜진_층의_시도_기록이_없으면_입력_오류다() {
        val r = inventoryDrop(orderRetry = true)
        r.omit += "timing/attempts-order.json"
        r.assertInputError()
    }

    @Test
    fun 재시도가_꺼진_층은_시도_기록이_없어도_된다() {
        // 기준 기록: 주문·재고 모두 꺼짐, 시도 기록 파일 없음
        val r = good()
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 재시도가_켜진_층의_시도_기록이_8_3에_어긋나면_입력_오류다() {
        // 마지막 시도의 decision 이 retry
        val r = inventoryDrop(orderRetry = true)
        r.attempts.getValue("order")[1] = Attempt("g", 1, 2, us(T + 19), us(T + 23), "Unknown:IO_IOException", 3, "retry")
        r.assertInputError()
    }

    // ---- 맞추기 후보(⑥) ----

    @Test
    fun 기준_기록의_후보는_다시_보낸_것과_같다() {
        val rep = good().analyze()
        assertEquals(2L, rep.long("reconcile", "candidates"))
        assertEquals(0L, rep.long("reconcile", "notResent"))
        assertEquals(0L, rep.long("reconcile", "resentNotCandidate"))
    }

    @Test
    fun 후보를_다시_보내지_않았으면_불일치다() {
        val r = good()
        r.resendLines!!.removeIf { it.contains("\"requestId\":\"c\"") }
        // c 의 맞추기 시각 기록은 남는다(보낸 것보다 많이 든 것 — ①은 max(0, …))
        val d = r.mismatch()
        assertEquals(listOf("reconcile: 다시 보낼 후보 2건 가운데 다시 보내지 못한 것 1건, 후보가 아닌데 다시 보낸 것 0건"), d)
        assertEquals(1L, r.report().long("reconcile", "notResent"))
    }

    @Test
    fun 후보가_아닌_것을_다시_보냈으면_불일치다() {
        // a(201)를 다시 보냄 — 주문에도 들었다
        val r = good().resend("a", R + 300, 201, replayed = true).timing("order", "a", us(R + 301), us(R + 302), 201)
            .counts("order", "replayed", 3)
        val d = r.mismatch()
        assertEquals(listOf("reconcile: 다시 보낼 후보 2건 가운데 다시 보내지 못한 것 0건, 후보가 아닌데 다시 보낸 것 1건"), d)
        assertEquals(1L, r.report().long("reconcile", "resentNotCandidate"))
    }

    @Test
    fun 성공이_하나라도_있으면_504가_있어도_후보가_아니다() {
        // g: copy1 504, copy2 201 — 다시 보내지 않아도 불일치가 아니다
        val r = good()
        val g = T + 600
        r.answer("g", g, 504, copy = 1).answer("g", g + 30, 201, copy = 2)
            .chainTimings("g", g, orderStatus = 504, orderOutcome = "Unknown:HTTP_504", invStatus = 504, invOutcome = "Unknown:REQUEST_TIMEOUT")
            .chainTimings("g", g + 30)
            .processedRows("g").keys("g")
            .counts("order", "new", 7).counts("order", "resumed", 2).counts("inventory", "new", 7).counts("inventory", "resumed", 2)
            .counts("payment", "new", 7).counts("payment", "replayed", 2)
        val rep = r.analyze()
        assertEquals(2L, rep.long("reconcile", "candidates"))
    }

    @Test
    fun 실패만_있는_요청은_후보가_아니다() {
        val r = good()
        val g = T + 600
        r.answer("g", g, 502).timing("order", "g", us(g + 1), us(g + 9), 502, us(g + 2), us(g + 8), "Failed:HTTP_500")
            .row("order", "g", "FAILED").key("order", "g", "FAILED", 502).counts("order", "new", 7)
        assertEquals(2L, r.analyze().long("reconcile", "candidates"))
    }

    @Test
    fun resends_파일이_없으면_후보가_있어도_6을_견주지_않는다() {
        val r = good()
        r.resendLines = null
        assertEquals(0, r.runIdem(), r.lastOutput)
    }
}
