package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// docs/measurement.md 12-1·12-2·12-5 — 테스트 입구 `readClockOffset(run)`과 `ClockOffset`(옮기는 양·반올림·보고의 clock 칸).
// 이 파일은 분석기의 구현을 보지 않고 문서만으로 쓴다.
class ClockOffsetReadTest {
    @TempDir
    lateinit var root: Path

    private var n = 0

    /** 새 실행 폴더에 config.env 를 쓰고 그 폴더를 돌려준다. */
    private fun run(lines: List<String>?): Path {
        val dir = root.resolve("run-${n++}").also { it.createDirectories() }
        lines?.let { writeConfig(dir, it) }
        return dir
    }

    private fun bd(s: String) = BigDecimal(s)

    private fun assertSameNumber(expected: String, actual: BigDecimal?, what: String) {
        assertNotNull(actual, what)
        assertEquals(0, bd(expected).compareTo(actual), "$what: 기대 $expected, 실제 $actual")
    }

    // ---- 상수 ----

    @Test
    fun 칸의_이름과_문턱의_기본값은_문서의_것이다() {
        assertEquals(
            listOf(CLOCK_START, CLOCK_OLD, CLOCK_END, CLOCK_DRIFT),
            listOf(CLOCK_OFFSET_START_KEY, CLOCK_OFFSET_OLD_KEY, CLOCK_OFFSET_END_KEY, CLOCK_DRIFT_MAX_KEY),
        )
        assertSameNumber("5", CLOCK_DRIFT_MAX_MS_DEFAULT, "문턱의 기본값")
    }

    // ---- 기록 없음 ----

    @Test
    fun config_env가_없으면_기록_없음() {
        assertNull(readClockOffset(run(null)))
    }

    @Test
    fun 시계_칸이_없는_config_env는_기록_없음() {
        assertNull(readClockOffset(run(OTHER_CONFIG_LINES)))
    }

    @Test
    fun 앞의_값이_없으면_문턱_칸만_있어도_기록_없음() {
        assertNull(readClockOffset(run(OTHER_CONFIG_LINES + "$CLOCK_DRIFT=3")))
    }

    @Test
    fun 다른_줄과_비슷한_이름의_칸은_보지_않는다() {
        // 12-1: 칸은 `<이름>=<값>` 한 줄, 다른 줄은 보지 않는다. 이름이 꼭 같아야 칸이다.
        val lines = OTHER_CONFIG_LINES + listOf(
            "# 주석 줄",
            "등호가 없는 줄",
            "x$CLOCK_START=abc",
            "${CLOCK_START}x=abc",
            "${CLOCK_END}_extra=abc",
            "clock_offset_error_ms=abc", // 오차 칸은 분석기가 읽지 않는다
        )
        assertNull(readClockOffset(run(lines)))
    }

    // ---- 기록 있음 ----

    @Test
    fun 앞의_값만_있으면_그_값으로_옮긴다() {
        val c = assertNotNull(readClockOffset(run(OTHER_CONFIG_LINES + "$CLOCK_START=-16.397")))
        assertSameNumber("-16.397", c.startMs, "startMs")
        assertNull(c.endMs, "endMs")
        assertSameNumber("-16.397", c.offsetMs, "offsetMs")
        assertEquals(-16L, c.shiftMs)
    }

    @Test
    fun 옛_이름만_있으면_앞의_값으로_읽고_문턱은_보지_않는다() {
        // 12-5: 06-a의 27개 — 앞의 값(옛 이름)만. 문턱 칸이 작아도 뒤의 값이 없으니 견줄 것이 없다.
        val c = assertNotNull(readClockOffset(run(OTHER_CONFIG_LINES + listOf("$CLOCK_OLD=-16.6", "$CLOCK_DRIFT=0"))))
        assertSameNumber("-16.6", c.startMs, "startMs")
        assertNull(c.endMs)
        assertEquals(-17L, c.shiftMs)
    }

    @Test
    fun 앞과_뒤가_있으면_평균으로_옮긴다() {
        val c = assertNotNull(readClockOffset(run(OTHER_CONFIG_LINES + listOf("$CLOCK_START=-15", "$CLOCK_END=-16"))))
        assertSameNumber("-15", c.startMs, "startMs")
        assertSameNumber("-16", c.endMs, "endMs")
        assertSameNumber("-15.5", c.offsetMs, "offsetMs")
        assertEquals(-16L, c.shiftMs, "−15.5 는 0에서 먼 쪽 −16")
    }

    @Test
    fun 평균은_반올림하지_않고_옮기는_양만_ms_정수로_반올림한다() {
        // (−16.499 + −16.500) / 2 = −16.4995 → −16. 평균을 셋째 자리로 먼저 반올림하면 −16.500 → −17 이 된다.
        val c = assertNotNull(readClockOffset(run(OTHER_CONFIG_LINES + listOf("$CLOCK_START=-16.499", "$CLOCK_END=-16.500"))))
        assertSameNumber("-16.4995", c.offsetMs, "offsetMs")
        assertEquals(-16L, c.shiftMs)
    }

    @Test
    fun 옮기는_양의_반올림_5는_0에서_먼_쪽() {
        val cases = listOf(
            "-16.5" to -17L, "16.5" to 17L, "-0.5" to -1L, "0.5" to 1L,
            "-16.4" to -16L, "16.4" to 16L, "-16.6" to -17L, "16.6" to 17L,
            "0.4" to 0L, "-0.4" to 0L, "0" to 0L, "-16.397" to -16L, "-16.500001" to -17L,
        )
        val wrong = cases.mapNotNull { (v, want) ->
            val got = ClockOffset(bd(v), null).shiftMs
            if (got == want) null else "$v → 기대 $want, 실제 $got"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun 평균의_5도_0에서_먼_쪽() {
        assertEquals(17L, ClockOffset(bd("16"), bd("17")).shiftMs)
        assertEquals(-17L, ClockOffset(bd("-17"), bd("-16")).shiftMs)
        assertEquals(1L, ClockOffset(bd("0"), bd("1")).shiftMs)
        assertEquals(-1L, ClockOffset(bd("0"), bd("-1")).shiftMs)
    }

    @Test
    fun 보고의_clock_칸_넷() {
        val c = ClockOffset(bd("-15"), bd("-16"))
        val rep = c.report()
        assertEquals(setOf("offsetStartMs", "offsetEndMs", "offsetMs", "shiftMs"), rep.keys)
        assertEquals(
            listOf(-15.0, -16.0, -15.5, -16.0),
            listOf("offsetStartMs", "offsetEndMs", "offsetMs").map { (rep[it] as Number).toDouble() } + (rep["shiftMs"] as Number).toDouble(),
        )
        val shift = rep["shiftMs"]
        assertTrue(shift is Int || shift is Long, "shiftMs 는 정수: ${shift?.javaClass}")
    }

    @Test
    fun 뒤가_없으면_보고의_offsetEndMs는_null() {
        val rep = ClockOffset(bd("-16.397"), null).report()
        assertTrue("offsetEndMs" in rep && rep["offsetEndMs"] == null, "offsetEndMs: $rep")
        assertEquals(-16L, (rep["shiftMs"] as Number).toLong())
    }

    // ---- 입력 오류 ----

    private fun base(): MutableList<String> =
        (OTHER_CONFIG_LINES + listOf("$CLOCK_START=-0.300", "$CLOCK_END=0.300", "$CLOCK_DRIFT=5")).toMutableList()

    private fun replace(key: String, value: String?): List<String> {
        val lines = base()
        val i = lines.indexOfFirst { it.startsWith("$key=") }
        if (value == null) lines.removeAt(i) else lines[i] = "$key=$value"
        return lines
    }

    @Test
    fun 기준_기록은_오류가_아니다() {
        val c = assertNotNull(readClockOffset(run(base())))
        assertEquals(0L, c.shiftMs)
    }

    private fun assertBad(lines: List<String>, what: String) {
        assertFailsWith<InputException>(what) { readClockOffset(run(lines)) }
    }

    @Test
    fun 칸이_두_줄이면_입력_오류() {
        for (key in listOf(CLOCK_START, CLOCK_END, CLOCK_DRIFT)) {
            val line = base().first { it.startsWith("$key=") }
            assertBad(base() + line, "$key 가 두 줄(같은 값)")
        }
        assertBad(OTHER_CONFIG_LINES + listOf("$CLOCK_OLD=-1", "$CLOCK_OLD=-1"), "옛 이름이 두 줄")
    }

    @Test
    fun 옛_이름과_새_이름이_함께_있으면_입력_오류() {
        assertBad(base() + "$CLOCK_OLD=-0.300", "_start 와 옛 이름")
    }

    @Test
    fun 뒤의_값만_있으면_입력_오류() {
        assertBad(replace(CLOCK_START, null), "뒤의 값만")
    }

    @Test
    fun 값의_꼴이_틀리면_입력_오류() {
        val bad = listOf("", "abc", "1e3", "+5", ".5", "5.", "-", "1,5", " 1", "1 ", "--1", "0x10", "1=2")
        val notThrown = mutableListOf<String>()
        for (key in listOf(CLOCK_START, CLOCK_END)) {
            for (v in bad) {
                val ok = runCatching { readClockOffset(run(replace(key, v))) }.exceptionOrNull() is InputException
                if (!ok) notThrown += "$key='$v'"
            }
        }
        for (v in bad + listOf("-1", "-0.5")) {
            val ok = runCatching { readClockOffset(run(replace(CLOCK_DRIFT, v))) }.exceptionOrNull() is InputException
            if (!ok) notThrown += "$CLOCK_DRIFT='$v'"
        }
        val ok = runCatching { readClockOffset(run(OTHER_CONFIG_LINES + "$CLOCK_OLD=abc")) }.exceptionOrNull() is InputException
        if (!ok) notThrown += "$CLOCK_OLD='abc'"
        assertEquals(emptyList(), notThrown, "입력 오류가 아니었던 값")
    }

    @Test
    fun 꼴에_맞는_값은_받는다() {
        // 12-1의 꼴: -?[0-9]+(\.[0-9]+)?, 문턱은 [0-9]+(\.[0-9]+)?. 앞의 0과 -0도 꼴에 맞는다.
        val ok = listOf("0", "-0", "007", "-16.397", "16.39700", "-0.001")
        for (v in ok) assertNotNull(readClockOffset(run(OTHER_CONFIG_LINES + "$CLOCK_START=$v")), "앞 '$v'")
        assertNotNull(readClockOffset(run(replace(CLOCK_DRIFT, "0.6"))), "문턱 0.6 (차 0.6)")
    }

    @Test
    fun 앞_뒤의_차가_기본_문턱_5를_넘으면_입력_오류이고_같으면_받는다() {
        // 문턱 칸이 없으면 5. 앞 −0.300.
        val noDrift = replace(CLOCK_DRIFT, null)
        fun withEnd(v: String) = noDrift.map { if (it.startsWith("$CLOCK_END=")) "$CLOCK_END=$v" else it }
        assertNotNull(readClockOffset(run(withEnd("4.700"))), "차 정확히 5 는 받는다")
        assertNotNull(readClockOffset(run(withEnd("-5.300"))), "차 정확히 5(뒤가 작음)는 받는다")
        assertBad(withEnd("4.701"), "차 5.001")
        assertBad(withEnd("-5.301"), "차 5.001(뒤가 작음)")
    }

    @Test
    fun 문턱_칸으로_바꾼_값으로_견준다() {
        fun lines(end: String, drift: String) = replace(CLOCK_DRIFT, drift).map { if (it.startsWith("$CLOCK_END=")) "$CLOCK_END=$end" else it }
        assertNotNull(readClockOffset(run(lines("1.700", "2"))), "차 2 = 문턱 2")
        assertBad(lines("1.701", "2"), "차 2.001 > 문턱 2")
        assertNotNull(readClockOffset(run(lines("9.700", "10"))), "차 10 = 문턱 10 (기본 5보다 크다)")
        assertBad(lines("9.701", "10"), "차 10.001 > 문턱 10")
        assertNotNull(readClockOffset(run(lines("-0.300", "0"))), "차 0 = 문턱 0")
        assertBad(lines("-0.299", "0"), "차 0.001 > 문턱 0")
    }
}
