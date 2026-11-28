package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// docs/measurement.md 12-1·12-3·12-4(와 8-4의 outage.*·attemptFaults.*) — 구간 장애의 from·to는 k6가 헤더에 더한 양
// (`outageShiftMs` = 앞의 값의 반올림)만큼 되옮기고, 서비스의 시각은 앞·뒤 평균(`shiftMs`)만큼 옮긴다.
// - k6의 시각과 견주는 outage.*(startMs·endMs·requestsSentDuring·completedSentDuring·clientRecoveryMs)는 k6가 뜻한 구간 그대로.
// - 서비스의 판정을 다시 세는 것(attemptFaults.payment.outage.expected·calleeArrivalsDuring·retryTailMs)은 기록 그대로의
//   서비스 시각과 헤더의 from·to를 견준 것과 같다.
// 두 반올림이 다른 기록(앞 −15, 뒤 −17 → 헤더에 쓴 양 −15, 평균 −16 / 반대로 15, 17)에서, 평균으로 구간을 되옮기는 틀린 규칙이면
// 값이 달라지도록 경계에 정확히 / 1ms(1μs) 앞에 놓인 기록을 골랐다. 주석의 "틀린 규칙" 값은 같은 기록에서 손으로 셈한 것.
// 이 파일은 분석기의 구현을 보지 않고 문서만으로 쓴다.
class OutageShiftTest {
    @TempDir
    lateinit var root: Path

    private var n = 0
    private fun fresh(): Path = root.resolve("o${n++}").also { it.createDirectories() }

    private fun bd(s: String) = BigDecimal(s)

    // ================= ClockOffset.outageShiftMs =================

    @Test
    fun outageShiftMs는_앞의_값의_반올림이고_5는_0에서_먼_쪽이다() {
        // (앞, 뒤, 기대 outageShiftMs, 그때의 shiftMs). 뒤를 골라 평균의 반올림(shiftMs)이 앞의 반올림과 다르게 했다 —
        // outageShiftMs 가 shiftMs 를 쓰면 넷째 값이 나온다. .5 를 짝수로(HALF_EVEN) 하면 −0.5·0.5 → 0, 16.5 → 16,
        // Math.round(+∞ 쪽)면 −15.5 → −15, −0.5 → 0, −16.5 → −16.
        val cases = listOf(
            listOf("-15", "-17", -15L, -16L),
            listOf("15", "17", 15L, 16L),
            listOf("-15.5", "-15.4", -16L, -15L),
            listOf("15.5", "15.4", 16L, 15L),
            listOf("-16.5", "-16.4", -17L, -16L),
            listOf("16.5", "16.4", 17L, 16L),
            listOf("-0.5", "0.3", -1L, 0L),
            listOf("0.5", "-0.3", 1L, 0L),
            listOf("-16.4", "-16.8", -16L, -17L),
            listOf("-16.397", "-17", -16L, -17L),
            listOf("0", "1", 0L, 1L),
            listOf("-0.4", "-1.6", 0L, -1L),
        )
        val wrong = cases.mapNotNull { (s, e, wantOut, wantShift) ->
            val c = ClockOffset(bd(s as String), bd(e as String))
            val got = c.outageShiftMs to c.shiftMs
            if (got == (wantOut to wantShift)) null else "앞 $s 뒤 $e → 기대 (outage $wantOut, shift $wantShift), 실제 $got"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun 뒤가_없으면_outageShiftMs는_shiftMs와_같다() {
        for ((s, want) in listOf("-16.5" to -17L, "16.5" to 17L, "-16.397" to -16L, "0.4" to 0L)) {
            val c = ClockOffset(bd(s), null)
            assertEquals(want, c.outageShiftMs, "앞 $s")
            assertEquals(c.shiftMs, c.outageShiftMs, "앞 $s")
        }
    }

    @Test
    fun headerShifted가_기본값_참이다() {
        // 12-1: headerShifted = true 가 기본. 앞 −15, 뒤 −17 → 앞의 반올림 −15 (shiftMs −16 이면 기본값이 거짓인 것).
        assertEquals(-15L, ClockOffset(bd("-15"), bd("-17")).outageShiftMs)
    }

    @Test
    fun headerShifted가_거짓이면_outageShiftMs는_shiftMs다() {
        // 앞의 반올림이었다면 −15 · 15 · −16 이 나온다.
        assertEquals(-16L, ClockOffset(bd("-15"), bd("-17"), headerShifted = false).outageShiftMs)
        assertEquals(16L, ClockOffset(bd("15"), bd("17"), headerShifted = false).outageShiftMs)
        assertEquals(-15L, ClockOffset(bd("-15.5"), bd("-15.4"), headerShifted = false).outageShiftMs)
        assertEquals(-17L, ClockOffset(bd("-16.5"), null, headerShifted = false).outageShiftMs)
    }

    @Test
    fun 보고의_clock_칸에는_outageShiftMs가_없다() {
        val rep = ClockOffset(bd("-15"), bd("-17")).report()
        assertEquals(setOf("offsetStartMs", "offsetEndMs", "offsetMs", "shiftMs"), rep.keys)
        assertEquals(-16L, (rep["shiftMs"] as Number).toLong(), "보고의 shiftMs 는 평균의 반올림(−15 가 아니다)")
    }

    // ---- readClockOffset 이 만드는 것 ----

    private fun read(vararg clock: String): ClockOffset {
        val dir = fresh()
        writeConfig(dir, OTHER_CONFIG_LINES + clock.toList())
        return assertNotNull(readClockOffset(dir))
    }

    @Test
    fun start_칸이면_headerShifted이고_outageShiftMs는_앞의_반올림() {
        val c = read("$CLOCK_START=-15", "$CLOCK_END=-17")
        assertTrue(c.headerShifted)
        assertEquals(-15L to -16L, c.outageShiftMs to c.shiftMs) // 평균을 쓰면 (−16, −16)

        val d = read("$CLOCK_START=15.500", "$CLOCK_END=14.400") // 평균 14.95 → 15, 앞 15.5 → 16
        assertTrue(d.headerShifted)
        assertEquals(16L to 15L, d.outageShiftMs to d.shiftMs)

        val e = read("$CLOCK_START=-16.397") // 뒤 없음 — 둘 다 −16
        assertTrue(e.headerShifted)
        assertEquals(-16L to -16L, e.outageShiftMs to e.shiftMs)
    }

    @Test
    fun 옛_이름_칸이면_headerShifted가_거짓이고_outageShiftMs는_shiftMs() {
        val c = read("$CLOCK_OLD=-15.5")
        assertFalse(c.headerShifted)
        assertEquals(-16L to -16L, c.outageShiftMs to c.shiftMs)
    }

    @Test
    fun 옛_이름과_뒤의_값이_함께면_outageShiftMs는_평균의_반올림() {
        // 문서의 빈 곳에 기댄 테스트: 12-1은 "옛 이름 + `_end`"를 입력 오류로 들지 않으므로 받는다고 읽었다.
        // 그때 headerShifted 는 거짓(`_start` 칸이 없다) → outageShiftMs = shiftMs = round(−16) = −16 (앞의 반올림이면 −15).
        val c = read("$CLOCK_OLD=-15", "$CLOCK_END=-17")
        assertFalse(c.headerShifted)
        assertEquals(-16L to -16L, c.outageShiftMs to c.shiftMs)
    }

    // ================= retry — k6의 시각과 견주는 outage.* =================

    /**
     * 헤더 `payment:outage=<hFrom>-<hTo>`(서비스의 시계)는 a(T — t0)에만 싣는다. a 의 결제는 T+5 로 구간 밖이다(expected 0 = counted 0).
     * 지시가 없는 요청의 보낸 시각(k6의 시계)과 판정:
     *   u T+90 완료 · w T+99 실패(404) · x1 T+100 완료 · x2 T+100 실패 · v T+299 완료 · y1 T+300 실패 · y2 T+301 완료.
     * k6가 뜻한 구간이 [T+100, T+300)이면: startMs 100, endMs 300, requestsSentDuring 3(x1, x2, v), completedSentDuring 2(x1, v),
     *   clientRecoveryMs = 완료가 아닌 것의 sentAtMs 최대(y1 T+300) − to = 0.
     */
    private fun k6Record(hFrom: Long, hTo: Long): RetryRecord {
        val r = RetryRecord(fresh()).okChain("a", T, attemptFault = "payment:outage=$hFrom-$hTo")
            .okChain("u", T + 90)
        r.inventoryRetried("w", T + 99, listOf("Failed:HTTP_404"), lastDecision = "not-retryable")
        r.okChain("x1", T + 100)
        r.inventoryRetried("x2", T + 100, listOf("Failed:HTTP_404"), lastDecision = "not-retryable")
        r.okChain("v", T + 299)
        r.inventoryRetried("y1", T + 300, listOf("Failed:HTTP_404"), lastDecision = "not-retryable")
        r.okChain("y2", T + 301)
        return r
    }

    private val k6Keys = listOf("startMs", "endMs", "requestsSentDuring", "completedSentDuring", "clientRecoveryMs")
    private val k6Intended = listOf(100L, 300L, 3L, 2L, 0L)

    private fun Map<String, Any?>.k6Side(): List<Long> = k6Keys.map { long("outage", it) }

    @Test
    fun 헤더를_옮긴_실행의_k6_쪽_구간은_k6가_뜻한_구간_그대로다_서비스가_늦은_쪽() {
        // 앞 −15 → k6가 헤더에 더한 양 −15: 헤더 = [T+100−15, T+300−15) = [T+85, T+285). 되옮김 = 헤더 + 15.
        // 틀린 규칙(평균 −16으로 되옮김): [T+101, T+301) → startMs 101, endMs 301, 보냄 2(v, y1), 완료 1(v), 회복 300−301 = −1.
        // 옮기지 않음: [T+85, T+285) → 85, 285, 4(u, w, x1, x2), 2, 15.
        val r = k6Record(T + 85, T + 285)
        writeClock(r.dir, start = "-15", end = "-17")
        val rep = r.analyzeAny()
        assertEquals(listOf(-15.0, -17.0, -16.0, -16.0), rep.clockNumbers(), "두 반올림이 다른 기록인지")
        val got = rep.k6Side()
        assertNotEquals(listOf(101L, 301L, 2L, 1L, -1L), got, "평균으로 되옮긴 값")
        assertEquals(k6Intended, got)
        assertEquals(emptyList(), rep.differences())
    }

    @Test
    fun 헤더를_옮긴_실행의_k6_쪽_구간은_k6가_뜻한_구간_그대로다_서비스가_이른_쪽() {
        // 앞 15 → 헤더 = [T+115, T+315). 되옮김 = 헤더 − 15 = [T+100, T+300).
        // 틀린 규칙(평균 16): [T+99, T+299) → 99, 299, 보냄 3(w, x1, x2), 완료 1(x1), 회복 300−299 = 1.
        // 옮기지 않음: [T+115, T+315) → 115, 315, 3(v, y1, y2), 2, −15.
        val r = k6Record(T + 115, T + 315)
        writeClock(r.dir, start = "15", end = "17")
        val rep = r.analyzeAny()
        assertEquals(listOf(15.0, 17.0, 16.0, 16.0), rep.clockNumbers())
        val got = rep.k6Side()
        assertNotEquals(listOf(99L, 299L, 3L, 1L, 1L), got, "평균으로 되옮긴 값")
        assertEquals(k6Intended, got)
        assertEquals(emptyList(), rep.differences())
    }

    @Test
    fun 뒤의_값을_문턱_안에서_바꿔도_k6_쪽_구간은_움직이지_않는다() {
        // 앞 −15(헤더 [T+85, T+285)) 고정, 뒤만 바꾼다(차 ≤ 4.9 < 문턱 5). 평균의 반올림과 평균으로 되옮겼을 때의 구간:
        //   뒤 없음 → −15 [100,300) · −15 → −15 · −17 → −16 [101,301) · −19.9 → −17.45 → −17 [102,302)(보냄 3 v·y1·y2, 완료 2, 회복 −2)
        //   · −10.1 → −12.55 → −13 [98,298)(보냄 3 w·x1·x2, 완료 1, 회복 2).
        val wrong = mutableListOf<String>()
        for (end in listOf(null, "-15", "-17", "-19.9", "-10.1")) {
            val r = k6Record(T + 85, T + 285)
            writeClock(r.dir, start = "-15", end = end)
            val got = r.analyzeAny().k6Side()
            if (got != k6Intended) wrong += "뒤 $end → $got"
        }
        // 반대 방향: 앞 15(헤더 [T+115, T+315)).
        for (end in listOf(null, "17", "19.9", "10.1")) {
            val r = k6Record(T + 115, T + 315)
            writeClock(r.dir, start = "15", end = end)
            val got = r.analyzeAny().k6Side()
            if (got != k6Intended) wrong += "앞 15, 뒤 $end → $got"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun 옛_이름의_실행은_구간도_shiftMs로_되옮긴다() {
        // 옛 이름 −15.5 → shiftMs −16 = outageShiftMs. 헤더 [T+84, T+284) → 되옮김 [T+100, T+300).
        // 옛 실행을 0으로 두면(헤더 그대로) [T+84, T+284) → 84, 284, 보냄 4(u, w, x1, x2), 완료 2, 회복 16.
        // (옛 이름에는 뒤의 값이 없어 앞의 반올림 = 평균의 반올림이다 — 둘을 가를 수는 없다.)
        val r = k6Record(T + 84, T + 284)
        writeClock(r.dir, start = null, old = "-15.5")
        val got = r.analyzeAny().k6Side()
        assertNotEquals(listOf(84L, 284L, 4L, 2L, 16L), got)
        assertEquals(k6Intended, got)
    }

    @Test
    fun 어긋남_기록이_없으면_헤더_그대로다() {
        val r = k6Record(T + 100, T + 300)
        val rep = r.analyzeAny()
        assertFalse("clock" in rep)
        assertEquals(k6Intended, rep.k6Side())
    }

    // ================= retry — 서비스의 판정을 다시 세는 것 =================

    /** okChain 과 같되 결제의 든 시각(startUs)만 [dUs] μs 옮긴 요청(결제 s+5ms + dUs). */
    private fun RetryRecord.okChainPayAt(id: String, s: Long, dUs: Long, attemptFault: String): RetryRecord {
        val callId = nextCallId("inventory")
        attempt("inventory", Attempt(id, callId, 1, us(s + 4), us(s + 14), "Completed", 0, "success"))
        timing("payment", id, us(s + 5) + dUs, us(s + 13), 201)
        row("payment", id, "APPROVED")
        timing("inventory", id, us(s + 3), us(s + 16), 201, us(s + 4), us(s + 14), "Completed")
        timing("order", id, us(s + 1), us(s + 18), 201, us(s + 2), us(s + 17), "Completed")
        row("inventory", id, "RESERVED").row("order", id, "COMPLETED")
        answer(id, s, 201, attemptFault, elapsedMs = 20)
        return this
    }

    /**
     * 헤더 [T+85, T+285)(서비스의 시계 — 결제가 기록 그대로의 startUs 와 견준다). 모든 요청이 지시를 싣는다.
     * 결제의 든 시각(기록 그대로):
     *   p1 T+84(1ms 앞, 밖, 완료) · q T+85 − 1μs(밖, 완료) · p2, p3 T+85(from 정확히 — 안, 500 → 간격 300 → 시도 2 시작 T+394, 결제 T+395 완료)
     *   · p4, p5, p6 T+284(안, 500 → 시도 2 시작 T+593, 결제 T+594 완료) · p7~p10 T+285(to 정확히 — 밖, 완료).
     * 기록 그대로 견주면: expected 5(p2~p6 의 첫 시도), calleeArrivalsDuring 5, retryTailMs = 재시도 시작 최대 T+593 − to T+285 = 308.
     * 서비스가 센 outage = 5.
     */
    private fun serviceRecord(): RetryRecord {
        val dir = "payment:outage=${T + 85}-${T + 285}"
        val r = RetryRecord(fresh())
        r.okChain("p1", T + 79, attemptFault = dir)
        r.okChainPayAt("q", T + 80, -1, dir)
        for (id in listOf("p2", "p3")) r.inventoryRetried(id, T + 80, listOf("Failed:HTTP_500", "Completed"), listOf(300L), attemptFault = dir)
        for (id in listOf("p4", "p5", "p6")) r.inventoryRetried(id, T + 279, listOf("Failed:HTTP_500", "Completed"), listOf(300L), attemptFault = dir)
        for (id in listOf("p7", "p8", "p9", "p10")) r.okChain(id, T + 280, attemptFault = dir)
        return r.counted("payment", "outage", 5)
    }

    private fun Map<String, Any?>.serviceSide(): List<Long> = listOf(
        long("attemptFaults", "payment", "outage", "expected"),
        long("outage", "calleeArrivalsDuring"),
        long("outage", "retryTailMs"),
    )

    private val serviceRaw = listOf(5L, 5L, 308L)

    @Test
    fun 서비스의_판정을_다시_세는_것은_기록_그대로_견준_것과_같다_서비스가_늦은_쪽() {
        // 앞 −15, 뒤 −17: 서비스의 시각 +16, 구간을 헤더 + 15 로 되옮긴 값과 그대로 견주는 틀린 규칙이면
        //   기록 그대로 t ∈ [T+84, T+285 − 1) — p1, q, p2, p3 → expected 4, callee 4, 꼬리 (T+593+16) − (T+285+15) = 309.
        val r = serviceRecord()
        writeClock(r.dir, start = "-15", end = "-17")
        val rep = r.analyzeAny()
        val got = rep.serviceSide()
        assertNotEquals(listOf(4L, 4L, 309L), got, "평균과 헤더의 양을 섞어 견준 값")
        assertEquals(serviceRaw, got)
        assertEquals(5L, rep.long("attemptFaults", "payment", "outage", "counted"))
        assertEquals(emptyList(), rep.differences(), "서비스가 센 5 = 다시 센 5 — 불일치가 없다")
    }

    @Test
    fun 서비스의_판정을_다시_세는_것은_기록_그대로_견준_것과_같다_서비스가_이른_쪽() {
        // 앞 15, 뒤 17: 틀린 규칙이면 기록 그대로 t ∈ [T+86, T+286) — p4~p6, p7~p10 → 7, 7, 꼬리 (T+593−16) − (T+285−15) = 307.
        val r = serviceRecord()
        writeClock(r.dir, start = "15", end = "17")
        val rep = r.analyzeAny()
        val got = rep.serviceSide()
        assertNotEquals(listOf(7L, 7L, 307L), got, "평균과 헤더의 양을 섞어 견준 값")
        assertEquals(serviceRaw, got)
        assertEquals(emptyList(), rep.differences())
    }

    @Test
    fun 서비스의_판정을_다시_세는_것은_뒤의_값과_옛_이름과_기록_없음에도_같다() {
        val wrong = mutableListOf<String>()
        fun check(name: String, start: String?, end: String?, old: String? = null) {
            val r = serviceRecord()
            if (start != null || old != null) writeClock(r.dir, start = start, end = end, old = old)
            val rep = r.analyzeAny()
            val got = rep.serviceSide()
            if (got != serviceRaw) wrong += "$name → $got"
            if (rep.differences().isNotEmpty()) wrong += "$name: 불일치 ${rep.differences()}"
        }
        for (end in listOf(null, "-15", "-19.9", "-10.1")) check("앞 −15 뒤 $end", "-15", end)
        for (end in listOf(null, "19.9", "10.1")) check("앞 15 뒤 $end", "15", end)
        check("옛 이름 −15.5", null, null, old = "-15.5")
        check("기록 없음", null, null)
        assertEquals(emptyList(), wrong)
    }
}
