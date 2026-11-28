package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// docs/measurement.md 12-2·12-3 — 어긋남이 기록된 실행에서 서비스의 시각을 `t − shiftMs`로 옮겨 두 시계를 견주는 지표를 낸다.
// 기록마다 옮긴 뒤 경계와 같은 값 / 1 단위 차의 값을 넣어, 옮기는 양·부호·반올림이 틀리면 값이 달라지게 했다.
// 옮기지 않는 지표(서비스끼리만)는 어긋남의 유무로 달라지지 않는다. 기록이 없는 실행은 보고가 한 글자도 바뀌지 않는다.
// 이 파일은 분석기의 구현을 보지 않고 문서만으로 쓴다.
class ClockShiftTest {
    @TempDir
    lateinit var root: Path

    private var n = 0
    private fun fresh(): Path = root.resolve("r${n++}")

    // ================= retry — bins.payment.peakPer1s =================

    /**
     * 결제의 든 시각(기록 그대로) = 보낸 ms + 5(okChain). t0 = T.
     * - a: 보냄 T → 결제 T+5
     * - b, c: 보냄 T+978 → 결제 T+983
     * - d, e, f, g: 보냄 T+979 → 결제 T+984
     * 어긋남 −15.5(앞 −15, 뒤 −16) → shiftMs −16 → 옮긴 결제 = 기록 + 16: a T+21, b·c T+999(칸 0의 끝), d~g T+1000(칸 1의 시작).
     * 1초 칸: 칸 0 = 3, 칸 1 = 4 → peakPer1s 4.
     * 옮기지 않으면 7 · −15로 옮기면(.5를 위로) d~g가 T+999 → 7 · −17이면 b·c도 칸 1 → 6 · 부호를 뒤집으면(−16을 뺀 반대) 모두 칸 0 쪽 → 4가 아니다.
     */
    private fun binsRecord(): RetryRecord {
        val r = RetryRecord(fresh()).okChain("a", T)
        for (id in listOf("b", "c")) r.okChain(id, T + 978)
        for (id in listOf("d", "e", "f", "g")) r.okChain(id, T + 979)
        return r
    }

    @Test
    fun retry_결제의_1초_칸은_옮긴_든_시각으로_나눈다() {
        val plain = binsRecord().analyze()
        assertEquals(7L, plain.long("bins", "payment", "peakPer1s"), "옮기지 않은 기준")

        val r = binsRecord()
        writeClock(r.dir, start = "-15", end = "-16")
        assertEquals(4L, r.analyze().long("bins", "payment", "peakPer1s"))
    }

    @Test
    fun retry_부호를_뒤집으면_값이_달라진다() {
        // 앞 15, 뒤 16 → shiftMs +16 → 결제 = 기록 − 16 (위와 반대). d~g 는 T+968 — 칸 0.
        val r = binsRecord()
        writeClock(r.dir, start = "15", end = "16")
        assertNotEquals(4L, r.analyze().long("bins", "payment", "peakPer1s"))
    }

    @Test
    fun retry_옛_이름의_앞의_값만으로도_옮긴다() {
        val r = binsRecord()
        writeClock(r.dir, start = null, old = "-15.5")
        val rep = r.analyze()
        assertEquals(4L, rep.long("bins", "payment", "peakPer1s"))
        assertEquals(listOf(-15.5, null, -15.5, -16.0), rep.clockNumbers())
    }

    @Test
    fun retry_start만_있어도_그_값으로_옮긴다() {
        val r = binsRecord()
        writeClock(r.dir, start = "-16.4")
        val rep = r.analyze()
        assertEquals(4L, rep.long("bins", "payment", "peakPer1s"))
        assertEquals(listOf(-16.4, null, -16.4, -16.0), rep.clockNumbers())
    }

    // ================= retry — 구간 장애 =================

    /**
     * 구간 장애 from = T+100, to = T+300(서비스의 시각 — 헤더 그대로). 어긋남 −16 → 옮긴 구간 [T+116, T+316).
     * - a(T, 지시) 결제 T+5 — 구간 밖.
     * - b(T+294, 지시) 시도 1 결제 T+299(구간 안 — to 바로 앞), 500 → 간격 300 → 시도 2 결제 T+609 성공. 서비스가 센 outage 1.
     *   from·to를 옮기지 않고 시각 기록만 옮기면 T+315 가 [T+100, T+300) 밖 → expected 0 ≠ counted 1.
     * - 지시 없는 요청(결제 = 보냄 + 5): c(T+115) d(T+300) e(T+316) f(T+310) g(T+90).
     * requestsSentDuring(보낸 시각 ∈ 구간): 옮김 [T+116, T+316) → b, d, f = 3 · 옮기지 않음 [T+100, T+300) → b, c = 2 ·
     *   부호를 뒤집음 [T+84, T+284) → c, g = 2.
     * calleeArrivalsDuring(결제의 시각 ∈ 구간 — 서비스끼리): b 시도 1(T+299), c(T+120) = 2, 옮겨도 같다.
     */
    private fun outageRecord(): RetryRecord {
        val dir = "payment:outage=${T + 100}-${T + 300}"
        val r = RetryRecord(fresh())
            .okChain("a", T, attemptFault = dir)
            .inventoryRetried("b", T + 294, listOf("Failed:HTTP_500", "Completed"), listOf(300L), attemptFault = dir)
        for ((id, s) in listOf("c" to 115L, "d" to 300L, "e" to 316L, "f" to 310L, "g" to 90L)) r.okChain(id, T + s)
        return r.counted("payment", "outage", 1)
    }

    @Test
    fun retry_구간_장애의_from_to와_보낸_수는_옮긴_구간으로_센다() {
        val plain = outageRecord().analyze()
        assertEquals(
            listOf(100L, 300L, 2L, 2L),
            listOf("startMs", "endMs", "requestsSentDuring", "completedSentDuring").map { plain.long("outage", it) },
            "옮기지 않은 기준",
        )
        val r = outageRecord()
        writeClock(r.dir, start = "-16", end = "-16")
        val rep = r.analyze() // 종료 0 — attemptFaults.payment.outage 의 counted 1 = expected 1
        assertEquals(
            listOf(116L, 316L, 3L, 3L),
            listOf("startMs", "endMs", "requestsSentDuring", "completedSentDuring").map { rep.long("outage", it) },
        )
    }

    @Test
    fun retry_구간_장애의_기대_수와_구간_안의_도착_수는_옮겨도_같다() {
        val plain = outageRecord().analyze()
        val r = outageRecord()
        writeClock(r.dir, start = "-16", end = "-16")
        val rep = r.analyze()
        for (rp in listOf(plain, rep)) {
            assertEquals(1L, rp.long("attemptFaults", "payment", "outage", "expected"))
            assertEquals(2L, rp.long("outage", "calleeArrivalsDuring"))
        }
        assertEquals(emptyList(), rep.differences())
    }

    @Test
    fun retry_서비스끼리_견주는_지표는_어긋남이_있어도_같다() {
        val plain = outageRecord().analyze()
        val r = outageRecord()
        writeClock(r.dir, start = "-16", end = "-16")
        val rep = r.analyze()
        for (k in listOf("layers", "clients", "mix")) assertEquals(plain[k], rep[k], "$k 가 바뀌었다")
        // edges.* 가운데 retriesPeakPer50ms 만 두 시계를 견준다(12-3) — 나머지(시도 수·결정·delayMs·gapMs·gapMinusDelayMs)는 같다.
        @Suppress("UNCHECKED_CAST")
        fun edgesWithoutPeak(r: Map<String, Any?>) = (r["edges"] as Map<String, Map<String, Any?>>).mapValues { it.value - "retriesPeakPer50ms" }
        assertEquals(edgesWithoutPeak(plain), edgesWithoutPeak(rep))
        assertEquals(plain.at("outage", "retryTailMs"), rep.at("outage", "retryTailMs"))
    }

    // ================= timing — 헛일 =================

    /**
     * 세 요청 모두 k6가 응답을 받지 못함(status 0, 경과 100) → giveUp = 보냄 + 100(k6의 시계). 위 층은 모두 Completed.
     * 서비스의 시각(기록 그대로, 보냄 s 기준 ms): 주문 s+1~s+89(call s+2~s+88), 재고 s+3~s+87(call s+4~s+86).
     * - x(T): 결제 s+5~s+85 → 옮김(+16) s+21~s+101 — 헛일 1ms, giveUp 앞에 시작.
     * - y(T+200): 결제 s+84~s+86 → 옮김 s+100~s+102 — 시작 = giveUp(≥ 이므로 startedAfterGiveUp), 헛일 2ms.
     * - z(T+400): 결제 s+5~s+84 → 옮김 끝 s+100 = giveUp — 헛일 아님(end > giveUp 이 아니다).
     * 어긋남 −16: 결제 wasted 2, startedAfterGiveUp 1, wastedMsSum 3.0, startedAfterGiveUpMsSum 2.0.
     * 옮기지 않으면 결제 헛일 0. −15로 옮기면 x 헛일 아님·y 는 시작 전 → wasted 1. −17이면 z 도 → 3.
     */
    private fun wasteRecord(): TimingRecord {
        val r = TimingRecord(fresh())
        for ((id, s, pay) in listOf(Triple("x", T, 5L to 85L), Triple("y", T + 200, 84L to 86L), Triple("z", T + 400, 5L to 84L))) {
            r.answer(id, s, status = 0, elapsedMs = 100)
            r.timing("order", id, us(s + 1), us(s + 89), 201, us(s + 2), us(s + 88), "Completed")
            r.timing("inventory", id, us(s + 3), us(s + 87), 201, us(s + 4), us(s + 86), "Completed")
            r.timing("payment", id, us(s + pay.first), us(s + pay.second), 201)
            r.row("order", id, "COMPLETED").row("inventory", id, "RESERVED").row("payment", id, "APPROVED")
        }
        return r
    }

    private fun Map<String, Any?>.paymentWaste(): List<Any?> = listOf(
        long("waste", "payment", "wasted"),
        long("waste", "payment", "startedAfterGiveUp"),
        double("waste", "payment", "wastedMsSum"),
        double("waste", "payment", "startedAfterGiveUpMsSum"),
    )

    @Test
    fun timing_헛일은_k6의_포기_시각과_옮긴_서비스의_끝으로_잰다() {
        val plain = wasteRecord().analyze()
        assertEquals(0L, plain.long("waste", "payment", "wasted"), "옮기지 않은 기준")

        val r = wasteRecord()
        writeClock(r.dir, start = "-16", end = "-16")
        assertEquals(listOf<Any?>(2L, 1L, 3.0, 2.0), r.analyze().paymentWaste())
    }

    @Test
    fun timing_헛일_주문과_재고도_옮긴_끝으로_잰다() {
        // 옮긴 끝: 주문 s+105(헛일 5ms), 재고 s+103(3ms) — 세 요청 모두.
        val r = wasteRecord()
        writeClock(r.dir, start = "-16", end = "-16")
        val rep = r.analyze()
        assertEquals(listOf(3L, 3L), listOf("order", "inventory").map { rep.long("waste", it, "wasted") })
        assertEquals(listOf(15.0, 9.0), listOf("order", "inventory").map { rep.double("waste", it, "wastedMsSum") })
    }

    @Test
    fun timing_서비스끼리_견주는_지표는_어긋남이_있어도_같다() {
        val plain = wasteRecord().analyze()
        val r = wasteRecord()
        writeClock(r.dir, start = "-16", end = "-16")
        val rep = r.analyze()
        for (k in listOf("edges", "layers", "clients", "delays")) assertEquals(plain[k], rep[k], "$k 가 바뀌었다")
        for (s in LAYERS) {
            for (k in listOf("maxGapMs", "maxInflight", "saturatedMs", "count")) {
                assertEquals(plain.at("samples", s, k), rep.at("samples", s, k), "samples.$s.$k")
            }
        }
    }

    /** 표본이 부하 구간을 덮는지(7-2)는 옮긴 표본의 atMs 로 본다. a(T), b(T+100) 경과 20 → 마지막 끝 T+120. */
    private fun coverRecord(lastSampleMs: Long): TimingRecord {
        val r = TimingRecord(fresh()).okChain("a", T).okChain("b", T + 100)
        for (s in LAYERS) r.samples[s] = grid(T, lastSampleMs).map { TestSample(it) }
        return r
    }

    @Test
    fun timing_표본이_부하_구간을_덮는지는_옮긴_atMs로_본다() {
        // 어긋남 +16 → 표본 = 기록 − 16. 마지막 표본 T+136 → T+120 = 마지막 끝(덮는다), T+135 → T+119(덮지 않는다 — 입력 오류).
        val noClock = coverRecord(T + 120)
        assertEquals(0, noClock.runTiming(), noClock.lastOutput)

        val shortNow = coverRecord(T + 120)
        writeClock(shortNow.dir, start = "16", end = "16")
        shortNow.assertInputError()

        val edge = coverRecord(T + 136)
        writeClock(edge.dir, start = "16", end = "16")
        assertEquals(0, edge.runTiming(), edge.lastOutput)

        val oneShort = coverRecord(T + 135)
        writeClock(oneShort.dir, start = "16", end = "16")
        oneShort.assertInputError()
    }

    // ================= overload — 구간 앞·안·뒤의 거절, 멈춘 시각 =================

    /**
     * 한도 3 / 100ms. 결제의 든 시각(기록 그대로, ms — startUs = ms·1000 + 600)을 손으로 준다. 구간 = spike 정답 s1(T+300)~s2(T+305).
     * - b1(T) 결제 T+20 받음
     * - s1(T+300, spike) T+312 받음, s2(T+305, spike) T+313 받음, c1(T+303) T+314 받음
     * - c2~c5(T+304) T+315, T+316, T+321, T+322 거절(창의 수 4·5·6·7)
     * - d1(T+600) T+610, d2(T+700) T+710 받음. lastSent T+700, Q 200.
     * 어긋남 +16 → 결제 = 기록 − 16: 거절 T+299(앞 — spikeStart 1 앞), T+300(= spikeStart, 안), T+305(= spikeEnd, 안), T+306(뒤).
     * rejectedBefore/During/After = 1/2/1. 옮기지 않으면 0/0/4. rejectionsStopMs = 306 − 305 = 1(옮기지 않으면 17).
     * 구간 뒤에 든 것: 옮김 — c5·d1·d2 = 3(받음 2) / 옮기지 않음 — 9(받음 5).
     */
    private fun spikeRecord(): OverloadRecord {
        val r = OverloadRecord(fresh())
        fun pay(ms: Long, status: Int) = listOf(Pay(us(T + ms) + 600, status))
        r.request("b1", T, "base", pay(20, 201))
        r.request("s1", T + 300, "spike", pay(312, 201))
        r.request("s2", T + 305, "spike", pay(313, 201))
        r.request("c1", T + 303, "base", pay(314, 201))
        for ((id, ms) in listOf("c2" to 315L, "c3" to 316L, "c4" to 321L, "c5" to 322L)) r.request(id, T + 304, "base", pay(ms, 503))
        r.request("d1", T + 600, "base", pay(610, 201))
        r.request("d2", T + 700, "base", pay(710, 201))
        return r
    }

    /** 불일치(⑥ 구간 앞의 거절, ⑦ 구간 안의 거절 0)가 있어도 보고는 쓴다 — 종료 0 또는 2. */
    private fun OverloadRecord.reportAny(): Map<String, Any?> {
        val code = runOverload()
        check(code == 0 || code == 2) { "보고를 기대했는데 종료 코드 $code: $lastOutput" }
        return report()
    }

    private fun Map<String, Any?>.rejectedSplit(): List<Long> =
        listOf("rejectedBeforeSpike", "rejectedDuringSpike", "rejectedAfterSpike").map { long("payment", it) }

    @Test
    fun overload_거절의_구간_앞_안_뒤는_옮긴_든_시각으로_가른다() {
        val plain = spikeRecord().reportAny()
        assertEquals(listOf(0L, 0L, 4L), plain.rejectedSplit(), "옮기지 않은 기준")

        val r = spikeRecord()
        writeClock(r.dir, start = "16", end = "16")
        assertEquals(listOf(1L, 2L, 1L), r.reportAny().rejectedSplit())
    }

    @Test
    fun overload_거절이_멈춘_시각과_구간_뒤의_도착은_옮긴_시각으로_잰다() {
        val plain = spikeRecord().reportAny()
        assertEquals(listOf<Any?>(1L, 17L, 9L, 5L), plain.stopAndAfter(), "옮기지 않은 기준")

        val r = spikeRecord()
        writeClock(r.dir, start = "16", end = "16")
        assertEquals(listOf<Any?>(1L, 1L, 3L, 2L), r.reportAny().stopAndAfter())
    }

    private fun Map<String, Any?>.stopAndAfter(): List<Any?> = listOf(
        long("payment", "recovered"),
        long("payment", "rejectionsStopMs"),
        long("payment", "arrivalsAfterSpike"),
        long("payment", "acceptedAfterSpike"),
    )

    @Test
    fun overload_부호를_뒤집으면_값이_달라진다() {
        // 앞 −16, 뒤 −16 → 결제 = 기록 + 16: 거절 모두 구간 뒤.
        val r = spikeRecord()
        writeClock(r.dir, start = "-16", end = "-16")
        assertEquals(listOf(0L, 0L, 4L), r.reportAny().rejectedSplit())
    }

    @Test
    fun overload_결제끼리_견주는_지표는_어긋남이_있어도_같다() {
        val plain = spikeRecord().reportAny()
        val r = spikeRecord()
        writeClock(r.dir, start = "16", end = "16")
        val rep = r.reportAny()
        for (k in listOf("rejectedReplayed", "rejectedObserved", "tieGroups", "tieGroupsMismatched", "maxWindowCount", "arrivals", "otherStatus")) {
            assertEquals(plain.at("payment", k), rep.at("payment", k), "payment.$k")
        }
        assertEquals(4L, rep.long("payment", "rejectedReplayed"))
        for (k in listOf("clients", "spike", "inventory", "limit")) assertEquals(plain[k], rep[k], "$k 가 바뀌었다")
    }

    // ================= 보고의 clock 칸 =================

    @Test
    fun 기록이_있으면_세_보고의_끝_series_앞에_clock_칸이_있고_tsv에_네_줄이_붙는다() {
        val wrong = mutableListOf<String>()
        fun check(name: String, rep: Map<String, Any?>, tsvPath: Path, hasSeries: Boolean) {
            val nums = runCatching { rep.clockNumbers() }.getOrElse { wrong += "$name: ${it.message}"; return }
            if (nums != listOf(-15.0, -16.0, -15.5, -16.0)) wrong += "$name: clock $nums"
            val keys = rep.keys.toList()
            val wantTail = if (hasSeries) listOf("clock", "series") else listOf("clock")
            if (keys.takeLast(wantTail.size) != wantTail) wrong += "$name: 보고의 끝 키 ${keys.takeLast(3)} (기대 $wantTail)"
            val tsv = readTsv(tsvPath)
            for ((k, v) in listOf("offsetStartMs" to -15.0, "offsetEndMs" to -16.0, "offsetMs" to -15.5, "shiftMs" to -16.0)) {
                val got = tsv["clock.$k"]?.toDoubleOrNull()
                if (got != v) wrong += "$name: tsv clock.$k = ${tsv["clock.$k"]}"
            }
            if (tsv["clock.shiftMs"] != "-16") wrong += "$name: tsv clock.shiftMs 는 정수 표기여야 한다: ${tsv["clock.shiftMs"]}"
        }
        val t = wasteRecord()
        writeClock(t.dir, start = "-15", end = "-16")
        check("timing", t.analyze(), t.metricsTsv, hasSeries = false)
        val rr = binsRecord()
        writeClock(rr.dir, start = "-15", end = "-16")
        check("retry", rr.analyze(), rr.metricsTsv, hasSeries = true)
        val o = goodOverloadRecord(fresh())
        writeClock(o.dir, start = "-15", end = "-16")
        check("overload", o.reportAny(), o.metricsTsv, hasSeries = true)
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun 뒤의_값이_없으면_tsv의_offsetEndMs는_빈_값() {
        val r = binsRecord()
        writeClock(r.dir, start = null, old = "-16")
        r.analyze()
        val tsv = readTsv(r.metricsTsv)
        assertTrue("clock.offsetEndMs" in tsv, "clock.offsetEndMs 줄이 없다")
        assertEquals("", tsv["clock.offsetEndMs"])
    }

    @Test
    fun 대조_분석의_보고에는_clock_칸이_없고_어긋남이_있어도_같다() {
        val a = RunRecord(fresh()).completedChain("a").completedChain("b")
        assertEquals(0, a.run())
        val b = RunRecord(fresh()).completedChain("a").completedChain("b")
        writeClock(b.dir, start = "-15", end = "-16")
        assertEquals(0, b.run())
        assertEquals(a.reportJson.readText(), b.reportJson.readText())
        assertEquals(a.reportMd.readText(), b.reportMd.readText())
        assertFalse("clock" in b.report())
    }

    // ================= 기록 없음 — 보고가 한 글자도 바뀌지 않는다 =================

    @Test
    fun 시계_칸이_없는_config_env는_config_env가_없는_것과_보고가_같고_clock이_없다() {
        val wrong = mutableListOf<String>()
        fun compare(name: String, a: Pair<Path, Path>, b: Pair<Path, Path>, aRep: Map<String, Any?>) {
            if (a.first.readText() != b.first.readText()) wrong += "$name: 보고가 다르다"
            if (a.second.readText() != b.second.readText()) wrong += "$name: tsv가 다르다"
            if ("clock" in aRep) wrong += "$name: 보고에 clock 칸이 있다"
            if (readTsv(b.second).keys.any { it.startsWith("clock.") }) wrong += "$name: tsv에 clock. 줄이 있다"
        }
        // timing
        val t0 = wasteRecord(); val t0rep = t0.analyze()
        val t1 = wasteRecord(); writeConfig(t1.dir, OTHER_CONFIG_LINES); t1.analyze()
        compare("timing", t0.reportTiming to t0.metricsTsv, t1.reportTiming to t1.metricsTsv, t0rep)
        // retry
        val r0 = outageRecord(); val r0rep = r0.analyze()
        val r1 = outageRecord(); writeConfig(r1.dir, OTHER_CONFIG_LINES + "$CLOCK_DRIFT=5"); r1.analyze()
        compare("retry", r0.reportRetry to r0.metricsTsv, r1.reportRetry to r1.metricsTsv, r0rep)
        // overload
        val o0 = spikeRecord(); val o0rep = o0.reportAny()
        val o1 = spikeRecord(); writeConfig(o1.dir, OTHER_CONFIG_LINES); o1.reportAny()
        compare("overload", o0.reportFile to o0.metricsTsv, o1.reportFile to o1.metricsTsv, o0rep)
        assertEquals(emptyList(), wrong)
    }
}
