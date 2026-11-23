package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// docs/measurement.md 7-2(인자, 기대 파일의 입력 오류)와 7-4(출력, 기대 파일 비교, 종료 코드).
class TimingExpectTest {
    @TempDir
    lateinit var root: Path

    /** 정상 하나와 재고→결제 거짓 타임아웃 하나: edges.inventory->payment.falseTimeouts = 1 */
    private fun rec() = TimingRecord(root).okChain("a", T).inventoryTimesOut("b", T + 100)

    private fun expect(json: String) = writeExpect(root, json, "expect.timing.json").toString()

    private val FT = "edges.inventory->payment.falseTimeouts"

    // ---- 출력 ----

    @Test
    fun 기준_기록은_종료_코드_0() {
        assertEquals(0, rec().runTiming())
    }

    @Test
    fun 보고의_run은_실행_폴더의_이름이다() {
        val r = rec()
        assertEquals(r.base.name, r.analyze()["run"])
    }

    @Test
    fun 기대_파일이_없으면_보고의_expect는_null() {
        assertEquals(null, rec().analyze()["expect"])
    }

    @Test
    fun metrics_tsv는_이름_탭_값으로_적는다() {
        val r = rec()
        r.analyze()
        assertTrue("$FT\t1" in r.metricsTsv.readLines())
    }

    @Test
    fun metrics_tsv의_null은_빈_값이다() {
        // 주문에 넣은 지연이 없다 → delays.order.p50 = null
        val r = rec()
        r.analyze()
        assertTrue("delays.order.p50\t" in r.metricsTsv.readLines())
    }

    // ---- 기대 파일 비교 ----

    @Test
    fun 기대_범위_안이면_종료_코드_0() {
        assertEquals(0, rec().runTiming("--expect", expect("""{"$FT":{"min":1,"max":1}}""")))
    }

    @Test
    fun 기대_범위_안이면_차이_목록이_비었다() {
        val r = rec()
        r.runTiming("--expect", expect("""{"$FT":{"min":1,"max":1}}"""))
        assertEquals(emptyList(), r.report().differences())
    }

    @Test
    fun 기대를_주면_보고의_expect는_그_값이다() {
        val e = expect("""{"$FT":{"min":1,"max":1}}""")
        val r = rec()
        r.runTiming("--expect", e)
        assertEquals(e, r.report()["expect"])
    }

    @Test
    fun 값이_min과_같으면_범위_안이다() {
        assertEquals(0, rec().runTiming("--expect", expect("""{"$FT":{"min":1,"max":5}}""")))
    }

    @Test
    fun 값이_max와_같으면_범위_안이다() {
        assertEquals(0, rec().runTiming("--expect", expect("""{"$FT":{"min":0,"max":1}}""")))
    }

    @Test
    fun 기대_범위_밖이면_종료_코드_2() {
        assertEquals(2, rec().runTiming("--expect", expect("""{"$FT":{"min":0,"max":0}}""")))
    }

    @Test
    fun 기대_범위_밖이면_문서의_꼴로_차이_한_줄을_적는다() {
        val r = rec()
        r.runTiming("--expect", expect("""{"$FT":{"min":0,"max":0}}"""))
        assertEquals(listOf("$FT: 기대 0~0, 실제 1"), r.report().differences())
    }

    @Test
    fun 기대_범위_밖이면_보고_파일을_쓴다() {
        val r = rec()
        r.runTiming("--expect", expect("""{"$FT":{"min":0,"max":0}}"""))
        assertTrue(r.reportTiming.exists() && r.metricsTsv.exists())
    }

    @Test
    fun 값이_null이면_어떤_범위와도_다르다() {
        assertEquals(2, rec().runTiming("--expect", expect("""{"delays.order.p50":{"min":0,"max":1000000}}""")))
    }

    @Test
    fun 없는_이름은_입력_오류가_아니라_불일치다() {
        assertEquals(2, rec().runTiming("--expect", expect("""{"no.such.metric":{"min":0,"max":1}}""")))
    }

    // ---- 기대 파일의 입력 오류 ----

    @Test
    fun 기대_파일이_없으면_입력_오류() {
        rec().assertInputError("--expect", root.resolve("nope.timing.json").toString())
    }

    @Test
    fun 기대_파일이_JSON이_아니면_입력_오류() {
        rec().assertInputError("--expect", expect("not json"))
    }

    @Test
    fun 기대_파일이_객체가_아니면_입력_오류() {
        rec().assertInputError("--expect", expect("""[{"$FT":{"min":1,"max":1}}]"""))
    }

    @Test
    fun 기대_파일이_빈_객체면_입력_오류() {
        rec().assertInputError("--expect", expect("{}"))
    }

    @Test
    fun 기대_값이_객체가_아니면_입력_오류() {
        rec().assertInputError("--expect", expect("""{"$FT":1}"""))
    }

    @Test
    fun 기대의_min이_max보다_크면_입력_오류() {
        rec().assertInputError("--expect", expect("""{"$FT":{"min":2,"max":1}}"""))
    }

    @Test
    fun 기대의_min이_수가_아니면_입력_오류() {
        rec().assertInputError("--expect", expect("""{"$FT":{"min":"1","max":1}}"""))
    }

    @Test
    fun 기대에_max가_없으면_입력_오류() {
        rec().assertInputError("--expect", expect("""{"$FT":{"min":1}}"""))
    }

    // ---- 인자 ----

    @Test
    fun run이_없으면_입력_오류() {
        assertEquals(1, analyzerCli("timing").first)
    }

    @Test
    fun 테스트_입구_runAnalyzer도_timing_하위_명령을_받는다() {
        // 5절: main 은 runAnalyzer 를 부르고 exitProcess — 그래서 `analyzer timing …` 의 인자도 runAnalyzer 가 받는다
        val r = rec()
        r.write()
        assertEquals(0, runAnalyzer(arrayOf("timing", "--run", r.dir.toString())))
    }

    @Test
    fun 모르는_인자는_입력_오류() {
        // --planned 는 대조 분석(5절)의 인자 — timing 에는 없다
        rec().assertInputError("--planned", "2")
    }

    @Test
    fun 값이_없는_인자는_입력_오류() {
        rec().assertInputError("--slow-label")
    }

    @Test
    fun 같은_인자가_두_번이면_입력_오류() {
        val r = rec()
        r.assertInputError("--run", r.dir.toString())
    }

    @Test
    fun 입력_오류면_그_폴더에_있던_보고를_지운다() {
        val r = rec().apply { omit += "settings-order.json" }
        r.write()
        r.reportTiming.writeText("""{"run":"old","differences":[]}""")
        r.metricsTsv.writeText("answers\t2\n")
        assertEquals(1, r.runTiming())
        assertFalse(r.reportTiming.exists() || r.metricsTsv.exists())
    }
}
