package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// docs/measurement.md 12-1 — 시계 칸의 입력 오류를 명령줄로 본다: 대조 분석·`timing`·`retry`·`overload`는 종료 1에 그 명령의
// 보고를 쓰지 않고 있던 것은 지운다. `idem`·`transport`는 시계 칸을 읽지 않는다(틀린 칸이 있어도 그대로).
// 틀린 이유로 통과하지 않게: 기준 기록(+ 기준 시계 칸, BASE_CLOCK_LINES)이 명령마다 종료 0인 것을 먼저 보고,
// 오류 테스트마다 그 기준의 config.env 에서 칸 하나만 바꾼다.
// 이 파일은 분석기의 구현을 보지 않고 문서만으로 쓴다.
class ClockInputErrorTest {
    @TempDir
    lateinit var root: Path

    /** 명령 하나: 기준 기록을 만들고 돌리는 법과 그 명령의 출력 파일 둘. */
    private class Subject(val dir: Path, val outputs: List<String>, val run: () -> Pair<Int, String>)

    private var n = 0
    private fun fresh(): Path = root.resolve("r${n++}")

    private fun timing(): Subject {
        val r = TimingRecord(fresh()).okChain("a", T).okChain("b", T + 100)
        return Subject(r.dir, listOf("report-timing.json", "metrics.tsv")) { r.runTiming() to r.lastOutput }
    }

    private fun retry(): Subject {
        val r = goodRetryRecord(fresh())
        return Subject(r.dir, listOf("report-retry.json", "metrics-retry.tsv")) { r.runRetry() to r.lastOutput }
    }

    private fun overload(): Subject {
        val r = goodOverloadRecord(fresh())
        return Subject(r.dir, listOf("report-overload.json", "metrics-overload.tsv")) { r.runOverload() to r.lastOutput }
    }

    private fun reconciliation(): Subject {
        val r = RunRecord(fresh()).completedChain("a").completedChain("b")
        return Subject(r.dir, listOf("report.json", "report.md")) {
            r.write()
            analyzerCli("--run", r.dir.toString())
        }
    }

    private val subjects: List<Pair<String, () -> Subject>> =
        listOf("대조 분석" to ::reconciliation, "timing" to ::timing, "retry" to ::retry, "overload" to ::overload)

    /** 기준 시계 칸에서 [key]의 줄을 [value]로(null 이면 지운다). */
    private fun replaced(key: String, value: String?): List<String> {
        val lines = BASE_CLOCK_LINES.toMutableList()
        val i = lines.indexOfFirst { it.startsWith("$key=") }
        check(i >= 0) { "기준에 $key 가 없다" }
        if (value == null) lines.removeAt(i) else lines[i] = "$key=$value"
        return lines
    }

    private fun baseLine(key: String) = BASE_CLOCK_LINES.first { it.startsWith("$key=") }

    /** 기준(칸 하나만 바꾼 것)에서 입력 오류여야 하는 것들. 앞 −0.300, 뒤 0.300, 문턱 5. */
    private val errorCases: List<Pair<String, List<String>>> = listOf(
        "앞의 칸이 두 줄" to BASE_CLOCK_LINES + baseLine(CLOCK_START),
        "뒤의 칸이 두 줄" to BASE_CLOCK_LINES + baseLine(CLOCK_END),
        "문턱 칸이 두 줄" to BASE_CLOCK_LINES + baseLine(CLOCK_DRIFT),
        "옛 이름과 _start 가 함께" to BASE_CLOCK_LINES + "$CLOCK_OLD=-0.300",
        "뒤의 값만(앞 없음)" to replaced(CLOCK_START, null),
        "앞이 숫자가 아님" to replaced(CLOCK_START, "abc"),
        "앞이 빈 값" to replaced(CLOCK_START, ""),
        "앞이 지수 표기" to replaced(CLOCK_START, "1e3"),
        "뒤가 +부호" to replaced(CLOCK_END, "+0.300"),
        "뒤가 .5" to replaced(CLOCK_END, ".5"),
        "문턱이 음수" to replaced(CLOCK_DRIFT, "-1"),
        "문턱이 숫자가 아님" to replaced(CLOCK_DRIFT, "abc"),
        "차 5.001 > 문턱 5" to replaced(CLOCK_END, "4.701"),
        "차 2.001 > 문턱 칸 2" to replaced(CLOCK_END, "1.701").map { if (it.startsWith("$CLOCK_DRIFT=")) "$CLOCK_DRIFT=2" else it },
    )

    /** 기준에서 받아야 하는 것들(경계). */
    private val okCases: List<Pair<String, List<String>>> = listOf(
        // 앞·뒤를 0에 대해 맞세워 평균 0(옮기는 양 0)을 지킨다 — 평균이 0이 아니면 기준 기록의 지표가 옮겨져
        // (timing 의 표본 덮기, overload 의 구간 앞 거절) 시계 칸의 검사와 다른 까닭으로 종료가 달라진다.
        "기준" to BASE_CLOCK_LINES,
        "차 정확히 문턱 5" to clockOf("-2.500", "2.500", "5"),
        "문턱 칸이 없고(기본 5) 차 정확히 5" to clockOf("2.500", "-2.500", null),
        "차 정확히 문턱 칸 2" to clockOf("-1.000", "1.000", "2"),
        "차 10 = 문턱 칸 10(기본보다 크다)" to clockOf("-5", "5", "10"),
    )

    private fun clockOf(start: String, end: String, drift: String?): List<String> =
        OTHER_CONFIG_LINES + listOfNotNull("$CLOCK_START=$start", "$CLOCK_END=$end", drift?.let { "$CLOCK_DRIFT=$it" })

    @Test
    fun 기준_기록은_시계_칸이_없어도_있어도_명령마다_종료_0() {
        val wrong = mutableListOf<String>()
        for ((name, make) in subjects) {
            val s = make()
            val (code0, out0) = s.run()
            if (code0 != 0) wrong += "$name 시계 칸 없음: 종료 $code0 — $out0"
            val t = make()
            writeConfig(t.dir, BASE_CLOCK_LINES)
            val (code1, out1) = t.run()
            if (code1 != 0) wrong += "$name 기준 시계 칸: 종료 $code1 — $out1"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun 경계의_값은_받는다() {
        val wrong = mutableListOf<String>()
        for ((name, make) in subjects) {
            for ((what, lines) in okCases) {
                val s = make()
                writeConfig(s.dir, lines)
                val (code, out) = s.run()
                if (code != 0) wrong += "$name / $what: 종료 $code — $out"
            }
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun 입력_오류는_종료_1이고_그_명령의_옛_보고를_지운다() {
        val wrong = mutableListOf<String>()
        for ((name, make) in subjects) {
            for ((what, lines) in errorCases) {
                val s = make()
                writeConfig(s.dir, lines)
                for (f in s.outputs) s.dir.resolve(f).writeText("옛 보고\n")
                val (code, out) = s.run()
                if (code != 1) wrong += "$name / $what: 종료 $code(기대 1) — $out"
                for (f in s.outputs) if (s.dir.resolve(f).exists()) wrong += "$name / $what: $f 가 남았다"
            }
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun 문턱을_넘은_오류_줄은_앞_뒤_차_문턱을_적는다() {
        // 12-1: `<경로>: 실행 앞·뒤의 시계 어긋남이 <앞> → <뒤>ms, 차 <차>ms가 문턱 <문턱>ms보다 크다 — …`
        // 정수 값으로 써서 수의 표기가 갈리지 않게 한다: 앞 1, 뒤 7, 차 6, 문턱 5.
        val lines = OTHER_CONFIG_LINES + listOf("$CLOCK_START=1", "$CLOCK_END=7", "$CLOCK_DRIFT=5")
        val wrong = mutableListOf<String>()
        for ((name, make) in subjects) {
            val s = make()
            writeConfig(s.dir, lines)
            val (code, out) = s.run()
            if (code != 1) wrong += "$name: 종료 $code"
            val want = "실행 앞·뒤의 시계 어긋남이 1 → 7ms, 차 6ms가 문턱 5ms보다 크다"
            if (want !in out) wrong += "$name: 오류 줄에 '$want' 가 없다 — $out"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun 문턱을_넘은_오류_줄에는_실행의_config_env_경로가_든다() {
        val s = retry()
        writeConfig(s.dir, OTHER_CONFIG_LINES + listOf("$CLOCK_START=1", "$CLOCK_END=7"))
        val (code, out) = s.run()
        assertEquals(1, code, out)
        assertTrue(s.dir.toString() in out, "오류 줄의 <경로>에 실행 폴더가 들어야 한다: $out")
    }

    // ---- idem·transport 는 읽지 않는다 ----

    @Test
    fun idem은_시계_칸을_읽지_않는다_틀린_칸이_있어도_보고가_같다() {
        val plain = goodIdemRecord(root.resolve("i0"))
        assertEquals(0, plain.runIdem(), plain.lastOutput)
        val want = plain.reportIdem.readText()
        val wantTsv = plain.metricsTsv.readText()
        for ((what, lines) in errorCases.take(1) + errorCases.last() + ("큰 어긋남" to OTHER_CONFIG_LINES + "$CLOCK_START=-1000")) {
            val r = goodIdemRecord(root.resolve("i${n++}"))
            writeConfig(r.dir, lines)
            assertEquals(0, r.runIdem(), "$what: ${r.lastOutput}")
            assertEquals(want, r.reportIdem.readText(), "$what: report-idem.json")
            assertEquals(wantTsv, r.metricsTsv.readText(), "$what: metrics-idem.tsv")
        }
    }

    @Test
    fun transport는_시계_칸을_읽지_않는다_틀린_칸이_있어도_보고가_같다() {
        val plain = TransportRecord(root.resolve("t0")).okChain("a", T).okChain("b", T + 100)
        assertEquals(0, plain.runTransport(), plain.lastOutput)
        val want = plain.reportFile.readText()
        val wantTsv = plain.tsvFile.readText()
        for ((what, lines) in errorCases.take(1) + errorCases.last() + ("큰 어긋남" to OTHER_CONFIG_LINES + "$CLOCK_START=-1000")) {
            val r = TransportRecord(root.resolve("t${n++}")).okChain("a", T).okChain("b", T + 100)
            writeConfig(r.dir, lines)
            assertEquals(0, r.runTransport(), "$what: ${r.lastOutput}")
            assertEquals(want, r.reportFile.readText(), "$what: report-transport.json")
            assertEquals(wantTsv, r.tsvFile.readText(), "$what: metrics-transport.tsv")
        }
    }
}
