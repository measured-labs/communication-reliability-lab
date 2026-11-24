package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// docs/measurement.md 10-3(지표의 식)·10-4(출력) — 기준 기록(goodOverloadRecord, 종료 0)의 손계산과 회복 시간의 정의.
class OverloadMetricsTest {
    @TempDir
    lateinit var root: Path

    private fun good(after: List<Long> = listOf(480, 600, 720, 840)) = goodOverloadRecord(root, after)

    // ---- 기준 기록 ----

    @Test
    fun 기준_기록은_종료_코드_0() {
        val r = good()
        assertEquals(0, r.runOverload(), r.lastOutput)
        assertEquals(emptyList(), r.report().differences())
    }

    @Test
    fun 보고의_run은_실행_폴더의_이름이고_기대가_없으면_expect는_null() {
        val rep = good().analyze()
        assertEquals("overload-20261125T000000-r1", rep["run"])
        assertNull(rep["expect"])
    }

    @Test
    fun 한도의_설정과_조용함() {
        val rep = good().analyze()
        assertEquals(listOf(3L, 100L, 200L), listOf(rep.long("limit", "limit"), rep.long("limit", "windowMs"), rep.long("limit", "quietMs")))
    }

    @Test
    fun 구간은_spike_정답의_보낸_시각의_처음과_끝이다() {
        val rep = good().analyze()
        assertEquals(300L, rep.long("spike", "startMs"))
        assertEquals(305L, rep.long("spike", "endMs"))
        assertEquals(6L, rep.long("spike", "sent"))
        assertEquals(8L, rep.long("spike", "baseSent"))
        assertEquals(535L, rep.long("spike", "lastSentAfterEndMs"), "lastSent 840 − spikeEnd 305")
    }

    @Test
    fun 클라이언트의_판정과_구간마다의_실패() {
        val rep = good().analyze()
        assertEquals(listOf(9L, 5L, 0L), listOf("completed", "failed", "unknown").map { rep.long("clients", it) })
        assertEquals(9.0 / 14, rep.double("clients", "successRatio")!!, 1e-9)
        assertEquals(listOf(0L, 4L, 1L), listOf("badBeforeSpike", "badDuringSpike", "badAfterSpike").map { rep.long("clients", it) })
        assertEquals(5L, rep.long("clients", "sentAfterSpike"), "360, 480, 600, 720, 840")
        assertEquals(14L, rep.long("clients", "elapsedMs", "count"))
        assertEquals(9L, rep.long("clients", "completedElapsedMs", "count"))
    }

    @Test
    fun 결제의_세_갈래가_같다() {
        val rep = good().analyze()
        assertEquals(14L, rep.long("payment", "arrivals"))
        assertEquals(14L, rep.long("payment", "arrivalsCounted"))
        assertEquals(
            listOf(5L, 5L, 5L),
            listOf("rejectedCounted", "rejectedObserved", "rejectedReplayed").map { rep.long("payment", it) },
        )
        assertEquals(0L, rep.long("payment", "tieGroups"))
        assertEquals(0L, rep.long("payment", "tieGroupsMismatched"))
        assertEquals(0L, rep.long("payment", "otherStatus"))
        assertEquals(7L, rep.long("payment", "maxWindowCount"), "305 와 360 에서 창 안 7")
    }

    @Test
    fun 결제의_거절을_든_시각으로_구간마다_나눈다() {
        // 305.6ms 에 든 거절은 든 시각(버림) 305 = 구간 끝 → 구간 안
        val rep = good().analyze()
        assertEquals(
            listOf(0L, 4L, 1L),
            listOf("rejectedBeforeSpike", "rejectedDuringSpike", "rejectedAfterSpike").map { rep.long("payment", it) },
        )
        assertEquals(5L, rep.long("payment", "arrivalsAfterSpike"))
        assertEquals(4L, rep.long("payment", "acceptedAfterSpike"))
    }

    @Test
    fun 회복_시간은_마지막_거절과_마지막_실패에서_구간_끝을_뺀_것이다() {
        val rep = good().analyze()
        assertEquals(1L, rep.long("payment", "recovered"))
        assertEquals(55L, rep.long("payment", "rejectionsStopMs"), "든 시각 360(360.6 의 버림) − 305")
        assertEquals(1L, rep.long("clients", "recovered"))
        assertEquals(55L, rep.long("clients", "recoveryMs"), "lastBad 360 − 305")
    }

    @Test
    fun 재고의_호출_결과마다의_수() {
        val rep = good().analyze()
        assertEquals(9L, rep.long("inventory", "callOutcomes", "Completed"))
        assertEquals(5L, rep.long("inventory", "callOutcomes", "Failed:HTTP_503"))
    }

    // ---- 회복의 경계: lastSent − Q 이하이면 회복 ----

    @Test
    fun 마지막_거절이_정확히_lastSent_빼기_Q이면_회복이다() {
        // 뒤의 평소를 480, 560 으로: lastSent 560, 560 − 200 = 360 = 마지막 거절·마지막 실패
        val rep = good(listOf(480, 560)).analyze()
        assertEquals(1L, rep.long("payment", "recovered"))
        assertEquals(55L, rep.long("payment", "rejectionsStopMs"))
        assertEquals(1L, rep.long("clients", "recovered"))
        assertEquals(55L, rep.long("clients", "recoveryMs"))
    }

    @Test
    fun 마지막_거절이_lastSent_빼기_Q보다_1ms_늦으면_회복하지_못함이고_값은_null이다() {
        // lastSent 559, 559 − 200 = 359 < 360
        val r = good(listOf(480, 559))
        val rep = r.analyze()
        assertEquals(0L, rep.long("payment", "recovered"))
        assertNull(rep.at("payment", "rejectionsStopMs"))
        assertEquals(0L, rep.long("clients", "recovered"))
        assertNull(rep.at("clients", "recoveryMs"))
        val tsv = r.metricsTsv.readLines()
        assertTrue("payment.rejectionsStopMs\t" in tsv, "회복하지 못함은 tsv 에서 빈 값: $tsv")
        assertTrue("clients.recoveryMs\t" in tsv, "회복하지 못함은 tsv 에서 빈 값: $tsv")
    }

    @Test
    fun 측정이_끝날_때까지_거절이_이어지면_회복하지_못함이다() {
        // 구간 뒤에도 평소가 창을 넘게(5ms 간격) 이어진다 — 창에 거절이 쌓여 끝까지 모두 거절
        val r = OverloadRecord(root)
        r.request("b1", T).request("b2", T + 120).request("b3", T + 240)
        r.request("s1", T + 300, "spike").request("s2", T + 301, "spike")
        for (i in 3..6) r.rejected("s$i", T + 299 + i, "spike")
        for (k in 1..80) r.rejected("a$k", T + 305 + 5L * k)
        val rep = r.analyze()
        assertEquals(0L, rep.long("payment", "recovered"))
        assertNull(rep.at("payment", "rejectionsStopMs"))
        assertEquals(0L, rep.long("clients", "recovered"))
        assertNull(rep.at("clients", "recoveryMs"))
        assertEquals(84L, rep.long("payment", "rejectedReplayed"))
    }

    @Test
    fun 구간이_끝나기_전에_실패가_멈추면_회복_시간은_음수다() {
        // a1(360)을 빼고 구간 요청 s7 을 470 에 더한다(창 (370, 470] = 자기 → 받음): spikeEnd 470, 마지막 거절·실패 305 → −165
        val r = OverloadRecord(root)
        r.request("b1", T).request("b2", T + 120).request("b3", T + 240)
        r.request("s1", T + 300, "spike").request("s2", T + 301, "spike")
        for (i in 3..6) r.rejected("s$i", T + 299 + i, "spike")
        r.request("s7", T + 470, "spike")
        for (t in listOf(600L, 720, 840)) r.request("a$t", T + t)
        val rep = r.analyze()
        assertEquals(470L, rep.long("spike", "endMs"))
        assertEquals(1L, rep.long("payment", "recovered"))
        assertEquals(-165L, rep.long("payment", "rejectionsStopMs"))
        assertEquals(1L, rep.long("clients", "recovered"))
        assertEquals(-165L, rep.long("clients", "recoveryMs"))
    }

    @Test
    fun 재시도로_성공한_요청은_클라이언트_회복에_들지_않고_결제의_거절에는_든다() {
        // a1(360)이 결제에서 거절된 뒤 420.6ms 에 다시 들어 받는다(창 (320.6, 420.6] = 360.6, 420.6 = 2 ≤ 3) → 클라이언트 201
        val r = OverloadRecord(root)
        r.request("b1", T).request("b2", T + 120).request("b3", T + 240)
        r.request("s1", T + 300, "spike").request("s2", T + 301, "spike")
        for (i in 3..6) r.rejected("s$i", T + 299 + i, "spike")
        r.request("a1", T + 360, pays = listOf(Pay(payAt(T + 360), 503), Pay(payAt(T + 420), 201)))
        for (t in listOf(480L, 600, 720, 840)) r.request("a$t", T + t)
        val rep = r.analyze()
        assertEquals(15L, rep.long("payment", "arrivals"))
        assertEquals(5L, rep.long("payment", "rejectedReplayed"))
        assertEquals(55L, rep.long("payment", "rejectionsStopMs"), "결제의 마지막 거절 360")
        assertEquals(0L, rep.long("clients", "recoveryMs"), "클라이언트의 마지막 실패는 구간의 s6(305) — 305 − 305")
        assertEquals(0L, rep.long("clients", "badAfterSpike"))
        assertEquals(10L, rep.long("inventory", "callOutcomes", "Completed"), "재시도는 마지막 시도의 결과")
        assertEquals(4L, rep.long("inventory", "callOutcomes", "Failed:HTTP_503"))
    }

    @Test
    fun 실패가_없으면_클라이언트_회복은_0이고_거절이_없으면_결제의_멈춤은_null이다() {
        // 한도를 넉넉히(50) — 거절이 없다 → ⑦ 불일치(종료 2)이지만 보고는 나온다
        val r = good()
        r.limit = 50
        val rep = r.analyzeMismatch()
        // 기준 기록의 503 다섯은 그대로 두었으므로 클라이언트에는 실패가 있다 — 실패 없는 기록은 아래에서 따로 만든다
        assertEquals(0L, rep.long("payment", "rejectedReplayed"))

        val clean = OverloadRecord(root, "clean-20261125T000000-r1")
        clean.limit = 50
        clean.request("b1", T).request("s1", T + 300, "spike").request("s2", T + 301, "spike").request("a1", T + 600)
        val rep2 = clean.analyzeMismatch()
        assertEquals(1L, rep2.long("clients", "recovered"))
        assertEquals(0L, rep2.long("clients", "recoveryMs"))
        assertEquals(1L, rep2.long("payment", "recovered"))
        assertNull(rep2.at("payment", "rejectionsStopMs"), "503 이 없으면 null")
        assertEquals(
            listOf("payment.rejectedDuringSpike: 0보다 커야 한다(구간이 한도를 넘지 않았다)"),
            rep2.differences(),
        )
    }

    @Test
    fun 구간의_처음에_보낸_실패는_구간_안이다() {
        // s1(300 = spikeStart)의 응답을 받지 못함(status 0) — 결제는 받았다. 구간 안의 실패 4 + 1
        val r = OverloadRecord(root)
        r.request("b1", T).request("b2", T + 120).request("b3", T + 240)
        r.request("s1", T + 300, "spike", status = 0).request("s2", T + 301, "spike")
        for (i in 3..6) r.rejected("s$i", T + 299 + i, "spike")
        r.rejected("a1", T + 360)
        for (t in listOf(480L, 600, 720, 840)) r.request("a$t", T + t)
        val rep = r.analyze()
        assertEquals(listOf(0L, 5L, 1L), listOf("badBeforeSpike", "badDuringSpike", "badAfterSpike").map { rep.long("clients", it) })
        assertEquals(1L, rep.long("clients", "unknown"))
    }

    // ---- 같은 μs 묶음 ----

    @Test
    fun 같은_us_묶음_안에서는_어느_호출이_거절인지가_아니라_수를_견준다() {
        // 구간 여섯이 모두 300.6ms 에 든다: 창 = 240, 그리고 묶음의 j번째 → 2, 3, 4, 5, 6, 7 → 거절 넷.
        // 파일에는 거절 넷을 먼저, 받은 둘을 나중에 적는다(차례가 규칙과 반대) — 수가 같으므로 통과.
        val r = OverloadRecord(root)
        r.request("b1", T).request("b2", T + 120).request("b3", T + 240)
        for (i in 1..4) r.request("s$i", T + 300, "spike", listOf(Pay(payAt(T + 300), 503)))
        for (i in 5..6) r.request("s$i", T + 300, "spike")
        r.rejected("a1", T + 360)
        for (t in listOf(480L, 600, 720, 840)) r.request("a$t", T + t)
        val rep = r.analyze()
        assertEquals(1L, rep.long("payment", "tieGroups"))
        assertEquals(0L, rep.long("payment", "tieGroupsMismatched"))
        assertEquals(5L, rep.long("payment", "rejectedReplayed"))
    }

    // ---- series ----

    @Test
    fun series는_1초_칸마다_결제와_클라이언트의_수다() {
        // 기준 기록 + 평소 1500, 2000: last = max(든 시각 2000, 2000 + 경과 1) = 2001 → 칸 3. 2000 은 셋째 칸 [2000, 3000)
        val r = good(listOf(480, 600, 720, 840, 1500, 2000))
        val s = r.analyze().at("series")
        assertEquals(1000L, s.long("binMs"))
        assertEquals(T, s.long("originSentAtMs"))
        assertEquals(listOf(14L, 1L, 1L), (s.at("paymentArrivals") as List<*>).map { (it as Number).toLong() })
        assertEquals(listOf(5L, 0L, 0L), (s.at("paymentRejected") as List<*>).map { (it as Number).toLong() })
        assertEquals(listOf(14L, 1L, 1L), (s.at("clientSent") as List<*>).map { (it as Number).toLong() })
        assertEquals(listOf(5L, 0L, 0L), (s.at("clientBad") as List<*>).map { (it as Number).toLong() })
    }

    @Test
    fun series의_마지막은_정답의_보낸_시각_더하기_경과의_버림까지다() {
        // 1989 + 경과 10.9 = 1999.9 → 버림 1999 → 칸 2. 경과 11 이면 2000 → 칸 3(셋째 칸은 빈 칸).
        val a = good(listOf(480, 600, 720, 840))
        a.request("z", T + 1989, elapsedMs = 10.9)
        assertEquals(2, (a.analyze().at("series", "clientSent") as List<*>).size)

        val b = goodOverloadRecord(root.resolve("b"), listOf(480, 600, 720, 840))
        b.request("z", T + 1989, elapsedMs = 11)
        val s = b.analyze().at("series")
        assertEquals(listOf(14L, 1L, 0L), (s.at("clientSent") as List<*>).map { (it as Number).toLong() })
        assertEquals(3, (s.at("paymentArrivals") as List<*>).size)
    }

    // ---- 출력 ----

    @Test
    fun metrics_tsv는_이름_탭_값이고_series와_run은_없다() {
        val r = good()
        r.analyze()
        val tsv = r.metricsTsv.readLines()
        for (line in listOf(
            "payment.rejectedReplayed\t5", "payment.rejectedCounted\t5", "payment.rejectedObserved\t5",
            "payment.rejectionsStopMs\t55", "clients.recoveryMs\t55", "limit.quietMs\t200", "spike.endMs\t305",
            "inventory.callOutcomes.Failed:HTTP_503\t5",
        )) assertTrue(line in tsv, "tsv 에 '$line' 가 없다: $tsv")
        assertTrue(tsv.none { it.startsWith("series") || it.startsWith("run\t") || it.startsWith("differences") }, "$tsv")
    }

    @Test
    fun 기대_파일과_맞으면_종료_0이고_expect에_경로를_적는다() {
        val r = good()
        val e = writeExpect(root, """{"payment.rejectedReplayed":{"min":5,"max":5},"payment.rejectionsStopMs":{"min":50,"max":60}}""", "expect.overload.json")
        val rep = r.analyze("--expect", e.toString())
        assertEquals(e.toString(), rep["expect"])
    }

    @Test
    fun 기대_파일과_다르면_종료_2이고_회복하지_못함은_없음으로_적는다() {
        val r = good(listOf(480, 559))
        val e = writeExpect(root, """{"payment.rejectionsStopMs":{"min":0,"max":100},"payment.rejectedReplayed":{"min":6,"max":9}}""", "expect.overload.json")
        val rep = r.analyzeMismatch("--expect", e.toString())
        assertEquals(
            listOf("payment.rejectionsStopMs: 기대 0~100, 실제 없음", "payment.rejectedReplayed: 기대 6~9, 실제 5"),
            rep.differences(),
        )
    }
}
