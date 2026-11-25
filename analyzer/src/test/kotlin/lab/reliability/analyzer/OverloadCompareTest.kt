package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// docs/measurement.md 10-4 — 늘 견주는 것 ①~⑦(기대 파일이 없어도, 이 차례로). 어긋나면 종료 2 와 문서의 줄.
// 어긋남마다 받아야 하는 짝(같은 꼴인데 맞는 기록 — 종료 0)을 둔다. 바탕은 기준 기록(goodOverloadRecord — 종료 0).
class OverloadCompareTest {
    @TempDir
    lateinit var root: Path

    private fun good() = goodOverloadRecord(root)

    /** 기준 기록과 같되 [change]로 요청 하나를 바꿔 만든다(바꾸지 않은 것은 기준 기록의 손계산 그대로). */
    private fun build(
        name: String = "overload-20261125T000000-r1",
        change: (OverloadRecord, String, Long, String) -> Boolean = { _, _, _, _ -> false },
    ): OverloadRecord {
        val r = OverloadRecord(root, name)
        val plan = listOf(
            Triple("b1", 0L, "ok"), Triple("b2", 120L, "ok"), Triple("b3", 240L, "ok"),
            Triple("s1", 300L, "ok"), Triple("s2", 301L, "ok"),
            Triple("s3", 302L, "rej"), Triple("s4", 303L, "rej"), Triple("s5", 304L, "rej"), Triple("s6", 305L, "rej"),
            Triple("a1", 360L, "rej"),
            Triple("a2", 480L, "ok"), Triple("a3", 600L, "ok"), Triple("a4", 720L, "ok"), Triple("a5", 840L, "ok"),
        )
        for ((id, t, kind) in plan) {
            val phase = if (id.startsWith("s")) "spike" else "base"
            if (change(r, id, T + t, phase)) continue
            if (kind == "ok") r.request(id, T + t, phase) else r.rejected(id, T + t, phase)
        }
        return r
    }

    @Test
    fun 바꾸지_않은_build는_기준_기록과_같고_통과한다() {
        val r = build()
        assertEquals(0, r.runOverload(), r.lastOutput)
        assertEquals(5L, r.report().long("payment", "rejectedReplayed"))
    }

    // ---- ① 한도가 꺼진 서비스의 센 수 ----

    @Test
    fun 꺼진_주문이_센_수가_0이_아니면_불일치() {
        val r = good()
        r.counts["order" to "arrivals"] = 1
        assertEquals(
            listOf("overload.order: 한도가 꺼져 있는데(settings-order.json) 센 수가 0이 아니다(arrivals 1, rejected 0)"),
            r.analyzeMismatch().differences(),
        )
    }

    @Test
    fun 꺼진_재고가_거절을_세었으면_불일치() {
        val r = good()
        r.counts["inventory" to "rejected"] = 2
        assertEquals(
            listOf("overload.inventory: 한도가 꺼져 있는데(settings-inventory.json) 센 수가 0이 아니다(arrivals 0, rejected 2)"),
            r.analyzeMismatch().differences(),
        )
    }

    @Test
    fun 꺼진_서비스의_줄은_주문_다음_재고_차례다() {
        val r = good()
        r.counts["inventory" to "arrivals"] = 3
        r.counts["order" to "arrivals"] = 4
        val d = r.analyzeMismatch().differences()
        assertEquals(listOf("overload.order", "overload.inventory"), d.map { it.substringBefore(":") })
    }

    // ---- ② 서비스가 센 든 수 = 시각 기록 수 ----

    @Test
    fun 서비스가_센_든_수가_시각_기록과_다르면_불일치() {
        val r = good()
        r.counts["payment" to "arrivals"] = 13
        assertEquals(listOf("payment.arrivalsCounted: 서비스가 센 든 수 13, 시각 기록 14"), r.analyzeMismatch().differences())
    }

    @Test
    fun 시각_기록이_하나_빠지면_든_수가_어긋난다() {
        // 받은 a5(840)의 결제 기록을 뺀다 — 서비스가 센 수 14 는 그대로(빠진 것이 결제가 센 뒤 기록에서 사라진 것)
        val r = build { rr, id, t, phase ->
            if (id != "a5") false else {
                rr.rawTiming(
                    "inventory",
                    """{"requestId":"a5","startUs":${payAt(t) - 200},"endUs":${payAt(t) + 300},"status":201,"callStartUs":${payAt(t) - 100},"callEndUs":${payAt(t) + 200},"callOutcome":"Completed"}""",
                )
                rr.answer("a5", t, phase, 201, 1)
                true
            }
        }
        r.counts["payment" to "arrivals"] = 14
        assertEquals(listOf("payment.arrivalsCounted: 서비스가 센 든 수 14, 시각 기록 13"), r.analyzeMismatch().differences())
    }

    // ---- ③ 거절 수의 세 갈래 ----

    @Test
    fun 서비스가_센_거절_수만_다르면_불일치() {
        val r = good()
        r.counts["payment" to "rejected"] = 4
        assertEquals(
            listOf("payment.rejected: 서비스가 센 수 4, 시각 기록의 503 5, 규칙을 다시 적용한 수 5"),
            r.analyzeMismatch().differences(),
        )
    }

    @Test
    fun 시각_기록의_503만_다르면_불일치() {
        // s4(303)의 결제 기록을 201 로 — 503 4, 센 수 5, 다시 적용 5. 같은 μs 묶음(303.6 하나짜리)도 어긋난다(④).
        val r = build { rr, id, t, phase ->
            if (id != "s4") false else { rr.request(id, t, phase, status = 502); true }
        }
        r.counts["payment" to "rejected"] = 5
        val d = r.analyzeMismatch().differences()
        assertEquals("payment.rejected: 서비스가 센 수 5, 시각 기록의 503 4, 규칙을 다시 적용한 수 5", d[0])
        assertTrue(d[1].startsWith("payment.rejectedByMicros: 같은 μs 묶음 1개에서"), "$d")
        assertTrue("${payAt(T + 303)}" in d[1], "$d")
        assertEquals(2, d.size, "$d")
    }

    @Test
    fun 규칙을_다시_적용한_수만_다르면_불일치() {
        // a1 의 결제가 360.6 대신 420.6 에 든 것으로 적혔다(창 (320.6, 420.6] = 자기 → 규칙으로는 받음) — 503 은 그대로
        val r = build { rr, id, t, phase ->
            if (id != "a1") false else { rr.request(id, t, phase, listOf(Pay(payAt(T + 420), 503))); true }
        }
        val d = r.analyzeMismatch().differences()
        assertEquals("payment.rejected: 서비스가 센 수 5, 시각 기록의 503 5, 규칙을 다시 적용한 수 4", d[0])
        assertTrue(d[1].startsWith("payment.rejectedByMicros: 같은 μs 묶음 1개에서 시각 기록의 503과 규칙을 다시 적용한 거절 수가 다르다(["), "$d")
        assertEquals(2, d.size, "$d")
    }

    @Test
    fun 세_수가_같아도_되는_까닭은_옮겨_적지_않아서다_503을_모두_201로_적으면_다시_적용한_수는_그대로다() {
        // 거절 다섯의 결제 기록을 모두 201 로, 센 거절 0 — 503 0, 센 수 0, 규칙 5
        val r = build { rr, id, t, phase ->
            if (id !in setOf("s3", "s4", "s5", "s6", "a1")) false else { rr.request(id, t, phase, status = 502); true }
        }
        val d = r.analyzeMismatch().differences()
        assertTrue("payment.rejected: 서비스가 센 수 0, 시각 기록의 503 0, 규칙을 다시 적용한 수 5" in d, "$d")
        assertEquals(5L, r.report().long("payment", "rejectedReplayed"))
    }

    // ---- ④ 같은 μs 묶음마다 ----

    @Test
    fun 거절_수의_합이_같아도_자리가_다르면_묶음_불일치만_난다() {
        // s3(302)는 201 로, a2(480)는 503 으로 적혔다 — 셋의 합은 5 로 같고 묶음 302.6·480.6 에서 어긋난다
        val r = build { rr, id, t, phase ->
            when (id) {
                "s3" -> { rr.request(id, t, phase, status = 502); true }
                "a2" -> { rr.rejected(id, t, phase); true }
                else -> false
            }
        }
        val d = r.analyzeMismatch().differences()
        assertEquals(1, d.size, "$d")
        assertTrue(d[0].startsWith("payment.rejectedByMicros: 같은 μs 묶음 2개에서 시각 기록의 503과 규칙을 다시 적용한 거절 수가 다르다(["), "$d")
        assertTrue("${payAt(T + 302)}" in d[0] && "${payAt(T + 480)}" in d[0], "$d")
        assertEquals(2L, r.report().long("payment", "tieGroupsMismatched"))
    }

    @Test
    fun 묶음_안에서_수가_다르면_불일치_받아야_하는_짝은_OverloadMetricsTest의_차례가_반대인_묶음() {
        // 구간 여섯이 모두 300.6 에 든다(규칙으로 거절 넷). 503 을 셋만 적었다 — 센 수도 3 으로 맞춰 ③은 503 과 센 수가 같다
        val r = OverloadRecord(root)
        r.request("b1", T).request("b2", T + 120).request("b3", T + 240)
        for (i in 1..3) r.request("s$i", T + 300, "spike", listOf(Pay(payAt(T + 300), 503)))
        for (i in 4..6) r.request("s$i", T + 300, "spike")
        r.rejected("a1", T + 360)
        for (t in listOf(480L, 600, 720, 840)) r.request("a$t", T + t)
        val d = r.analyzeMismatch().differences()
        assertEquals("payment.rejected: 서비스가 센 수 4, 시각 기록의 503 4, 규칙을 다시 적용한 수 5", d[0])
        assertTrue(d[1].startsWith("payment.rejectedByMicros: 같은 μs 묶음 1개에서"), "$d")
        assertTrue("${payAt(T + 300)}" in d[1], "$d")
        assertEquals(1L, r.report().long("payment", "tieGroups"))
    }

    // ---- ⑤ 201 도 503 도 아닌 결제 기록 ----

    @Test
    fun 결제_기록이_201도_503도_아니면_불일치() {
        val r = build { rr, id, t, phase ->
            if (id != "a3") false else { rr.request(id, t, phase, listOf(Pay(payAt(t), 500)), status = 502); true }
        }
        assertEquals(listOf("payment.otherStatus: 늘 0, 실제 1"), r.analyzeMismatch().differences())
    }

    // ---- ⑥ 구간 앞은 평소 ----

    @Test
    fun 구간_앞의_결제_거절이_있으면_불일치() {
        // 한도 1: b1(0) 받음, b2(50.6)는 거절(창 (−49.4, 50.6] = 2)되고 160.6 에 다시 들어 받는다 → 클라이언트 201.
        // 구간 s1(300) 받음, s2(301) 거절. 뒤 a1(600) 받음.
        val r = OverloadRecord(root)
        r.limit = 1
        r.request("b1", T)
        r.request("b2", T + 50, pays = listOf(Pay(payAt(T + 50), 503), Pay(payAt(T + 160), 201)))
        r.request("s1", T + 300, "spike").rejected("s2", T + 301, "spike").request("a1", T + 600)
        assertEquals(listOf("payment.rejectedBeforeSpike: 늘 0, 실제 1"), r.analyzeMismatch().differences())
    }

    @Test
    fun 구간_앞의_클라이언트_실패가_있으면_불일치() {
        // b2(120)의 응답을 받지 못함(status 0) — 결제는 받았다
        val r = build { rr, id, t, phase ->
            if (id != "b2") false else { rr.request(id, t, phase, status = 0); true }
        }
        assertEquals(listOf("clients.badBeforeSpike: 늘 0, 실제 1"), r.analyzeMismatch().differences())
    }

    @Test
    fun 구간_앞의_거절이_실패로_이어지면_두_줄이다() {
        val r = OverloadRecord(root)
        r.limit = 1
        r.request("b1", T).rejected("b2", T + 50)
        r.request("s1", T + 300, "spike").rejected("s2", T + 301, "spike").request("a1", T + 600)
        assertEquals(
            listOf("payment.rejectedBeforeSpike: 늘 0, 실제 1", "clients.badBeforeSpike: 늘 0, 실제 1"),
            r.analyzeMismatch().differences(),
        )
    }

    @Test
    fun 구간_앞의_재시도가_받아진_기록의_짝_구간_앞이_평소면_통과() {
        // 위와 같되 b2 를 구간 앞 거절 없이(160 에 한 번) — 통과
        val r = OverloadRecord(root)
        r.limit = 1
        r.request("b1", T).request("b2", T + 160)
        r.request("s1", T + 300, "spike").rejected("s2", T + 301, "spike").request("a1", T + 600)
        assertEquals(0, r.runOverload(), r.lastOutput)
    }

    // ---- ⑦ 구간 동안의 거절 ----

    @Test
    fun 구간_동안의_거절이_0이면_불일치() {
        val r = OverloadRecord(root)
        r.limit = 5
        r.request("b1", T).request("s1", T + 300, "spike").request("s2", T + 301, "spike").request("a1", T + 600)
        assertEquals(
            listOf("payment.rejectedDuringSpike: 0보다 커야 한다(구간이 한도를 넘지 않았다)"),
            r.analyzeMismatch().differences(),
        )
    }

    @Test
    fun 구간_동안의_거절이_하나면_통과한다() {
        val r = OverloadRecord(root)
        r.limit = 1
        r.request("b1", T).request("s1", T + 300, "spike").rejected("s2", T + 301, "spike").request("a1", T + 600)
        assertEquals(0, r.runOverload(), r.lastOutput)
    }

    // ---- 차례: ①~⑦ 다음에 기대 파일 ----

    @Test
    fun 늘_견주는_것은_차례대로이고_기대_파일의_차이는_그_뒤다() {
        val r = build { rr, id, t, phase ->
            if (id != "a3") false else { rr.request(id, t, phase, listOf(Pay(payAt(t), 500)), status = 502); true }
        }
        r.counts["payment" to "arrivals"] = 99
        r.counts["order" to "rejected"] = 1
        val e = writeExpect(root, """{"payment.rejectedReplayed":{"min":0,"max":0}}""", "expect.overload.json")
        val d = r.analyzeMismatch("--expect", e.toString()).differences()
        assertEquals(
            listOf(
                "overload.order: 한도가 꺼져 있는데(settings-order.json) 센 수가 0이 아니다(arrivals 0, rejected 1)",
                "payment.arrivalsCounted: 서비스가 센 든 수 99, 시각 기록 14",
                "payment.otherStatus: 늘 0, 실제 1",
                "payment.rejectedReplayed: 기대 0~0, 실제 5",
            ),
            d,
        )
    }
}
