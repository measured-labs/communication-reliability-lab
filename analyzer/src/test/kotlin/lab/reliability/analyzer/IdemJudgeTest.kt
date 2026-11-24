package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// docs/measurement.md 9-5 — 늘 견주는 것(① 닿지 못한 요청 ② 끊은 수·넣은 실패 ③ DB 행은 있는데 시각 기록이 없음
// ④ 같은 트랜잭션의 확인·센 수 new·꺼진 키), 기대 파일(7-4절과 같은 꼴), 종료 코드.
// 불일치 테스트마다 기준 기록(goodIdemRecord — 종료 0)에서 한 가지만 더하거나 바꾼다.
class IdemJudgeTest {
    @TempDir
    lateinit var root: Path

    private fun good() = goodIdemRecord(root)

    /** 기준 기록에 세 층을 모두 지난 요청 g(T+600)를 더한다 — 키·센 수 new 도 맞춘다. */
    private fun IdemRecord.plusOk(id: String = "g", s: Long = T + 600): IdemRecord =
        answer(id, s).chainTimings(id, s).processedRows(id).keys(id)
            .counts("order", "new", 7).counts("inventory", "new", 7).counts("payment", "new", 7)

    @Test
    fun 기준_기록은_종료_0이고_보고와_tsv를_쓴다() {
        val r = good()
        assertEquals(0, r.runIdem(), r.lastOutput)
        assertTrue(r.reportIdem.exists())
        assertTrue(r.metricsTsv.exists())
    }

    // ---- ① ----

    @Test
    fun 서버에_닿지_못한_요청이_있으면_불일치다() {
        // g: 응답을 받았는데(502) 주문의 시각 기록이 없다 — 9-4: 보낸 것 − 주문의 시각 기록(응답 상태와 무관)
        val d = good().answer("g", T + 600, 502, elapsedMs = 30).mismatch()
        assertEquals(listOf("clients.notReachedServer: 늘 0, 실제 1"), d)
    }

    @Test
    fun 응답_없이_닿지_못한_요청은_위_층이_본_끊김과_맞추기_후보에도_든다() {
        // g: 응답 없음(status 0), 시각 기록·행·키 없음 — ① 닿지 못함, ② 주문 밖에서 본 끊김 2(f, g) ≠ 센 수 1,
        // ⑥ 후보 c·f·g 가운데 g 를 다시 보내지 않았다
        val d = good().answer("g", T + 600, 0, elapsedMs = 60000).mismatch()
        assertEquals(
            listOf(
                "clients.notReachedServer: 늘 0, 실제 1",
                "dropFaults.order: 서비스가 센 수 1, 위 층이 본 끊김 2",
                "reconcile: 다시 보낼 후보 3건 가운데 다시 보내지 못한 것 1건, 후보가 아닌데 다시 보낸 것 0건",
            ),
            d,
        )
    }

    @Test
    fun 두_번_보냈는데_주문에_한_번만_들었으면_닿지_못한_것이다() {
        // g: 잇달아 두 번(둘째는 저장한 응답이라고 받았다) — 주문의 시각 기록은 하나뿐
        val r = good()
        r.answer("g", T + 600, 201, copy = 1).answer("g", T + 630, 201, copy = 2, replayed = true)
            .chainTimings("g", T + 600).processedRows("g").keys("g")
            .counts("order", "new", 7).counts("inventory", "new", 7).counts("payment", "new", 7).counts("order", "replayed", 3)
        assertEquals(listOf("clients.notReachedServer: 늘 0, 실제 1"), r.mismatch())
    }

    @Test
    fun 다시_보낸_것이_주문에_들지_않았으면_닿지_못한_것이다() {
        // g: 정답 504 + 다시 보낸 것 201 — 주문의 시각 기록은 처음 것 하나
        val r = good()
        r.answer("g", T + 600, 504)
            .chainTimings("g", T + 600, orderStatus = 504, orderOutcome = "Unknown:HTTP_504", invStatus = 504, invOutcome = "Unknown:REQUEST_TIMEOUT")
            .resend("g", R + 200, 201)
            .processedRows("g").keys("g")
            .counts("order", "new", 7).counts("inventory", "new", 7).counts("payment", "new", 7)
        assertEquals(listOf("clients.notReachedServer: 늘 0, 실제 1"), r.mismatch())
    }

    @Test
    fun 주문에_보낸_것보다_많이_들었어도_닿지_못한_수는_음수가_되지_않는다() {
        // g: 한 번 보냈는데 주문에 두 번 든 기록(max(0, 1 − 2) = 0)
        val r = good().plusOk().timing("order", "g", us(T + 650), us(T + 652), 201)
        assertEquals(0L, r.analyze().long("clients", "notReachedServer"))
    }

    // ---- ② ----

    @Test
    fun 끊은_수가_기록에서_다시_센_수와_다르면_불일치다() {
        // 9-5 ②: 기록에서 다시 센 수와, 위 층이 본 끊김(주문 = status 0 인 보낸 것 — f 하나)과 각각 견준다
        val d = good().drops("order", 2).mismatch()
        assertEquals(
            listOf("dropFaults.order: 서비스가 센 수 2, 기록에서 다시 센 수 1", "dropFaults.order: 서비스가 센 수 2, 위 층이 본 끊김 1"),
            d,
        )
    }

    @Test
    fun 가리키지_않은_서비스가_끊었다고_세도_불일치다() {
        val d = good().drops("inventory", 1).mismatch()
        assertEquals(
            listOf("dropFaults.inventory: 서비스가 센 수 1, 기록에서 다시 센 수 0", "dropFaults.inventory: 서비스가 센 수 1, 위 층이 본 끊김 0"),
            d,
        )
    }

    @Test
    fun 넣은_실패의_수가_다르면_8_5와_같은_꼴의_불일치다() {
        val d = good().attemptCounted("payment", "fail-first", 1).mismatch()
        assertEquals(listOf("attemptFaults.payment.fail-first: 서비스가 센 수 1, 기록에서 다시 센 수 0"), d)
    }

    @Test
    fun 끊은_수는_서비스_차례이고_넣은_실패보다_앞이다() {
        val d = good().drops("payment", 3).drops("order", 0).attemptCounted("order", "slow-first", 1).mismatch()
        assertEquals(
            listOf(
                "dropFaults.order: 서비스가 센 수 0, 기록에서 다시 센 수 1",
                "dropFaults.order: 서비스가 센 수 0, 위 층이 본 끊김 1",
                "dropFaults.payment: 서비스가 센 수 3, 기록에서 다시 센 수 1",
                "dropFaults.payment: 서비스가 센 수 3, 위 층이 본 끊김 1",
                "attemptFaults.order.slow-first: 서비스가 센 수 1, 기록에서 다시 센 수 0",
            ),
            d,
        )
    }

    // ---- ③ ----

    @Test
    fun DB_행은_있는데_그_층의_시각_기록이_없으면_불일치다() {
        // g: 세 층에 행, 재고의 시각 기록만 없다
        val r = good()
        val g = T + 600
        r.answer("g", g)
            .timing("order", "g", us(g + 1), us(g + 19), 201, us(g + 2), us(g + 18), "Completed")
            .timing("payment", "g", us(g + 5), us(g + 15), 201)
            .processedRows("g").keys("g")
            .counts("order", "new", 7).counts("inventory", "new", 7).counts("payment", "new", 7)
        assertEquals(listOf("layers.inventory: DB 행은 있는데 시각 기록이 없는 requestId 1건([g])"), r.mismatch())
    }

    // ---- ④ 켜짐 ----

    @Test
    fun 키가_켜졌는데_업무_행에_키_행이_없으면_불일치다() {
        val r = good()
        val g = T + 600
        r.answer("g", g).chainTimings("g", g).processedRows("g")
            .key("order", "g", "COMPLETED", 201).key("payment", "g", "COMPLETED", 201)
            .counts("order", "new", 7).counts("payment", "new", 7)
        // 업무 행 7·키 행 6 — 짝의 대조(keyRows, keyLinks)도 같은 것을 잡는다
        assertEquals(
            listOf(
                "layers.inventory.rowsWithoutKey: 늘 0, 실제 1",
                "layers.inventory.keyRows: 업무 행 7과 다르다(6)",
                "layers.inventory.keyLinks.rowsNotLinked: 늘 0, 실제 1",
            ),
            r.mismatch(),
        )
    }

    @Test
    fun 키가_켜졌는데_키_행에_업무_행이_없으면_불일치다() {
        val r = good()
        val g = T + 600
        r.answer("g", g).chainTimings("g", g).row("order", "g", "COMPLETED").row("inventory", "g", "RESERVED").keys("g")
            .counts("order", "new", 7).counts("inventory", "new", 7).counts("payment", "new", 7)
        assertEquals(
            listOf(
                "layers.payment.keysWithoutRow: 늘 0, 실제 1",
                "layers.payment.keyRows: 업무 행 6과 다르다(7)",
                "layers.payment.keyLinks.keysToMissingRow: 늘 0, 실제 1",
            ),
            r.mismatch(),
        )
    }

    @Test
    fun 센_new가_키_행_수와_다르면_불일치다() {
        val d = good().counts("order", "new", 5).mismatch()
        assertEquals(listOf("layers.order.counts.new: 키 행 6와 다르다(5)"), d)
    }

    @Test
    fun 같은_층의_셋은_rowsWithoutKey_keysWithoutRow_new_차례다() {
        // 재고: g 는 업무 행만(키 없음), h 는 키만(업무 행 없음), 센 new 는 9(키 행 7)
        val r = good()
        r.answer("g", T + 600).chainTimings("g", T + 600).processedRows("g")
            .key("order", "g", "COMPLETED", 201).key("payment", "g", "COMPLETED", 201)
            .answer("h", T + 700).chainTimings("h", T + 700).row("order", "h", "COMPLETED").row("payment", "h", "APPROVED").keys("h")
            .counts("order", "new", 8).counts("payment", "new", 8).counts("inventory", "new", 9)
        assertEquals(
            listOf(
                "layers.inventory.rowsWithoutKey: 늘 0, 실제 1",
                "layers.inventory.keysWithoutRow: 늘 0, 실제 1",
                "layers.inventory.counts.new: 키 행 7와 다르다(9)",
                "layers.inventory.keyLinks.rowsNotLinked: 늘 0, 실제 1",
                "layers.inventory.keyLinks.keysToMissingRow: 늘 0, 실제 1",
            ),
            r.mismatch(),
        )
    }

    // ---- ④ 꺼짐 ----

    @Test
    fun 키가_꺼진_기록은_키_행이_없고_센_수가_0이면_통과한다() {
        val r = offIdemRecord(root)
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 키가_꺼졌는데_키_행이_있으면_불일치다() {
        val d = offIdemRecord(root).key("order", "a", "COMPLETED", 201).mismatch()
        assertEquals(1, d.size, d.toString())
        assertTrue(d[0].startsWith("layers.order: 멱등 키가 꺼져 있는데(settings-order.json)"), d[0])
    }

    @Test
    fun 키가_꺼졌는데_센_수가_0이_아니면_불일치다() {
        val d = offIdemRecord(root).counts("payment", "missing-key", 1).mismatch()
        assertEquals(1, d.size, d.toString())
        assertTrue(d[0].startsWith("layers.payment: 멱등 키가 꺼져 있는데(settings-payment.json)"), d[0])
    }

    @Test
    fun 키가_꺼졌으면_업무_행에_키_행이_없어도_불일치가_아니다() {
        // 꺼진 층은 rowsWithoutKey·counts.new 를 견주지 않는다(offIdemRecord 는 업무 행 6, 키 행 0)
        val r = offIdemRecord(root)
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    // ---- 차례 ----

    @Test
    fun 늘_견주는_것의_차례는_1_2_3_4_5_6이다() {
        val r = good()
        // ① g: 닿지 못함(응답 없음 — 주문 밖에서 본 끊김은 f·g 2, ⑥ 후보 g 를 다시 보내지 않음)
        r.answer("g", T + 600, 0, elapsedMs = 60000)
        // ③ h: 재고의 시각 기록 없음
        val h = T + 700
        r.answer("h", h)
            .timing("order", "h", us(h + 1), us(h + 19), 201, us(h + 2), us(h + 18), "Completed")
            .timing("payment", "h", us(h + 5), us(h + 15), 201)
            .processedRows("h").keys("h")
            .counts("order", "new", 7).counts("payment", "new", 7)
        // ② 주문이 끊은 수 2(위 층이 본 것도 2 — 그 줄은 없다), ④ 재고 new 5(키 행 7), ⑤ 주문·재고 in-progress +1(409 없음)
        r.drops("order", 2).counts("inventory", "new", 5).counts("order", "in-progress", 2).counts("inventory", "in-progress", 1)
        assertEquals(
            listOf(
                "clients.notReachedServer: 늘 0, 실제 1",
                "dropFaults.order: 서비스가 센 수 2, 기록에서 다시 센 수 1",
                "layers.inventory: DB 행은 있는데 시각 기록이 없는 requestId 1건([h])",
                "layers.order.counts: in-progress 2·mismatch 1가 시각 기록의 409 1·422 1와 맞지 않는다(409·422였을 수 있는 끊긴 응답 0)",
                "layers.inventory.counts.new: 키 행 7와 다르다(5)",
                "layers.inventory.counts: in-progress 1·mismatch 0가 시각 기록의 409 0·422 0와 맞지 않는다(409·422였을 수 있는 끊긴 응답 0)",
                "reconcile: 다시 보낼 후보 3건 가운데 다시 보내지 못한 것 1건, 후보가 아닌데 다시 보낸 것 0건",
            ),
            r.mismatch(),
        )
    }

    // ---- 기대 파일 ----

    @Test
    fun 기대_범위_안이면_통과한다() {
        val r = good()
        val e = writeExpect(
            root,
            """{"clients.completed":{"min":5,"max":5},"duplicates.pairs.201+409":{"min":1,"max":1},
               "layers.order.counts.in-progress":{"min":0,"max":1},"reconcile.finalOnce":{"min":2,"max":2},
               "clients.status.0":{"min":1,"max":1},"enabled.payment":{"min":1,"max":1}}""",
            "expect.idem.json",
        )
        assertEquals(0, r.runIdem("--expect", e.toString()), r.lastOutput)
    }

    @Test
    fun 기대_범위_밖이면_불일치다() {
        val r = good()
        val e = writeExpect(root, """{"clients.completed":{"min":0,"max":4}}""", "expect.idem.json")
        assertEquals(listOf("clients.completed: 기대 0~4, 실제 5"), r.mismatch("--expect", e.toString()))
    }

    @Test
    fun 기대의_이름이_없으면_실제_없음이다() {
        val r = good()
        val e = writeExpect(root, """{"layers.order.counts.nope":{"min":0,"max":0}}""", "expect.idem.json")
        assertEquals(listOf("layers.order.counts.nope: 기대 0~0, 실제 없음"), r.mismatch("--expect", e.toString()))
    }

    @Test
    fun 기대의_차이는_적은_차례이고_늘_견주는_것_뒤다() {
        val r = good().drops("payment", 0)
        val e = writeExpect(
            root,
            """{"reconcile.resent":{"min":3,"max":3},"clients.failed":{"min":0,"max":0}}""",
            "expect.idem.json",
        )
        assertEquals(
            listOf(
                "dropFaults.payment: 서비스가 센 수 0, 기록에서 다시 센 수 1",
                "dropFaults.payment: 서비스가 센 수 0, 위 층이 본 끊김 1",
                "reconcile.resent: 기대 3~3, 실제 2",
                "clients.failed: 기대 0~0, 실제 2",
            ),
            r.mismatch("--expect", e.toString()),
        )
    }

    @Test
    fun 기대의_수는_정수면_소수점_없이_소수면_그대로_적는다() {
        // 7-4: clients.elapsedMs.p50 = 20(보고에 20.0 이어도 20), min·max 는 0.0 → 0, 0.5 는 그대로
        val r = good()
        val e = writeExpect(
            root,
            """{"clients.elapsedMs.p50":{"min":0.5,"max":19.5},"clients.completed":{"min":0.0,"max":4.0}}""",
            "expect.idem.json",
        )
        assertEquals(
            listOf("clients.elapsedMs.p50: 기대 0.5~19.5, 실제 20", "clients.completed: 기대 0~4, 실제 5"),
            r.mismatch("--expect", e.toString()),
        )
    }

    @Test
    fun 불일치여도_보고와_tsv를_쓴다() {
        val r = good().drops("order", 2)
        assertEquals(2, r.runIdem(), r.lastOutput)
        assertTrue(r.reportIdem.exists())
        assertTrue(r.metricsTsv.exists())
    }
}
