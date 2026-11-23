package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// docs/measurement.md 5절 — 명령줄, 종료 코드, --expect(5-2), --planned, report.json 의 머리 필드(5-1)
class ExpectAndPlannedTest {
    @TempDir
    lateinit var root: Path

    /** 정상 둘과 payment 를 잃은 하나: lostTotal 1, layerMismatch 1 */
    private fun oneLost() = RunRecord(root).completedChain("a").completedChain("b")
        .answer("c").rows("c", "COMPLETED", "RESERVED", null)

    // ---- 9. 기대 파일 ----

    @Test
    fun 기대_파일과_같으면_종료_코드_0() {
        val e = writeExpect(
            root,
            """{"answers":3,"lostTotal":0,"doubledTotal":0,"layerMismatch":0,"orphanRows":0,"pendingRows":0,"faultCountsMatch":true,
               "labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":3}}}""",
        )
        val r = RunRecord(root).completedChain("a").completedChain("b").completedChain("c")
        assertEquals(0, r.run("--expect", e.toString()))
    }

    @Test
    fun 기대_파일과_같으면_차이_목록이_비었다() {
        val e = writeExpect(root, """{"answers":3,"lostTotal":0}""")
        val r = RunRecord(root).completedChain("a").completedChain("b").completedChain("c")
        r.run("--expect", e.toString())
        assertEquals(emptyList(), r.report().differences())
    }

    @Test
    fun 잃음_건수가_하나_다르면_종료_코드_2() {
        val e = writeExpect(root, """{"lostTotal":0}""")
        assertEquals(2, oneLost().run("--expect", e.toString()))
    }

    @Test
    fun 잃음_건수가_하나_다르면_차이_한_줄을_문서의_꼴로_적는다() {
        val e = writeExpect(root, """{"lostTotal":0}""")
        val r = oneLost()
        r.run("--expect", e.toString())
        assertEquals(listOf("lostTotal: 기대 0, 실제 1"), r.report().differences())
    }

    @Test
    fun 기대_파일에_적은_키만_견준다() {
        // 잃음이 있어도 answers 만 적었으면 통과
        val e = writeExpect(root, """{"answers":3}""")
        assertEquals(0, oneLost().run("--expect", e.toString()))
    }

    @Test
    fun 정답_줄_수가_다르면_종료_코드_2() {
        val e = writeExpect(root, """{"answers":4}""")
        assertEquals(2, oneLost().run("--expect", e.toString()))
    }

    @Test
    fun 두_번_처리됨이_다르면_종료_코드_2() {
        val e = writeExpect(root, """{"doubledTotal":0}""")
        val r = RunRecord(root).completedChain("a").completedChain("a")
        assertEquals(2, r.run("--expect", e.toString()))
    }

    @Test
    fun 층_불일치가_다르면_종료_코드_2() {
        val e = writeExpect(root, """{"layerMismatch":0}""")
        assertEquals(2, oneLost().run("--expect", e.toString()))
    }

    @Test
    fun 기대의_orphanRows는_세_층의_합과_견준다() {
        val e = writeExpect(root, """{"orphanRows":3}""")
        val r = RunRecord(root).completedChain("a")
            .row("order", "x", "COMPLETED").row("inventory", "y", "RESERVED").row("payment", "z", "APPROVED")
        assertEquals(0, r.run("--expect", e.toString()))
    }

    @Test
    fun 기대의_pendingRows는_세_층의_합과_견준다() {
        val e = writeExpect(root, """{"pendingRows":2}""")
        val r = RunRecord(root).answer("a", 0).rows("a", "PENDING", "PENDING", null)
        assertEquals(0, r.run("--expect", e.toString()))
    }

    @Test
    fun 장애_수_일치가_기대와_다르면_종료_코드_2이고_차이_한_줄() {
        val e = writeExpect(root, """{"faultCountsMatch":true}""")
        val r = RunRecord(root).answer("a", 500, "order:error") // 센 수 0 → 불일치
        assertEquals(2, r.run("--expect", e.toString()))
        val d = r.report().differences()
        assertEquals(1, d.size)
        assertTrue(d.single().startsWith("faultCountsMatch:"), "실제: $d")
    }

    @Test
    fun 기대에_없는_서명이_실제에_있으면_기대_0으로_차이를_적는다() {
        val e = writeExpect(root, """{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":3}}}""")
        val r = oneLost()
        assertEquals(2, r.run("--expect", e.toString()))
        assertTrue(
            "labels['']['completed | COMPLETED | RESERVED | -']: 기대 0, 실제 1" in r.report().differences(),
            "실제: ${r.report().differences()}",
        )
    }

    @Test
    fun 기대의_서명_건수가_다르면_차이를_적는다() {
        val e = writeExpect(root, """{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":3}}}""")
        val r = oneLost()
        r.run("--expect", e.toString())
        assertTrue(
            "labels['']['completed | COMPLETED | RESERVED | APPROVED']: 기대 3, 실제 2" in r.report().differences(),
            "실제: ${r.report().differences()}",
        )
    }

    @Test
    fun 기대에_있는_라벨이_실제에_없으면_불일치다() {
        val e = writeExpect(
            root,
            """{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":1},"order:error":{"failed | - | - | -":1}}}""",
        )
        val r = RunRecord(root).completedChain("a")
        assertEquals(2, r.run("--expect", e.toString()))
    }

    @Test
    fun 기대에_없는_라벨이_실제에_있으면_불일치다() {
        val e = writeExpect(root, """{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":1}}}""")
        val r = RunRecord(root).completedChain("a").answer("b", 500, "order:error").counted("order", "error", 1)
        assertEquals(2, r.run("--expect", e.toString()))
    }

    @Test
    fun 보고의_expect는_넘긴_값이다() {
        val e = writeExpect(root, """{"answers":1}""")
        val r = RunRecord(root).completedChain("a")
        r.run("--expect", e.toString())
        assertEquals(e.toString(), r.report()["expect"])
    }

    @Test
    fun 기대_파일에_모르는_키가_있으면_입력_오류() {
        val e = writeExpect(root, """{"answers":1,"lost":0}""")
        RunRecord(root).completedChain("a").assertInputError("--expect", e.toString())
    }

    @Test
    fun 기대_파일이_빈_객체면_입력_오류() {
        val e = writeExpect(root, "{}")
        RunRecord(root).completedChain("a").assertInputError("--expect", e.toString())
    }

    @Test
    fun 기대_파일이_없으면_입력_오류() {
        RunRecord(root).completedChain("a").assertInputError("--expect", root.resolve("no-such.json").toString())
    }

    @Test
    fun 기대_파일이_JSON이_아니면_입력_오류() {
        val e = writeExpect(root, "answers: 1")
        RunRecord(root).completedChain("a").assertInputError("--expect", e.toString())
    }

    @Test
    fun 기대의_labels가_라벨에서_서명_건수로의_객체가_아니면_입력_오류() {
        val e = writeExpect(root, """{"labels":{"":3}}""")
        RunRecord(root).completedChain("a").assertInputError("--expect", e.toString())
    }

    // ---- 10. --planned ----

    @Test
    fun planned가_정답_줄_수와_같으면_종료_코드_0() {
        assertEquals(0, RunRecord(root).completedChain("a").completedChain("b").run("--planned", "2"))
    }

    @Test
    fun planned를_보고에_적는다() {
        val r = RunRecord(root).completedChain("a").completedChain("b")
        r.run("--planned", "2")
        assertEquals(2L, r.report().long("planned"))
    }

    @Test
    fun planned가_정답_줄_수와_다르면_종료_코드_2() {
        assertEquals(2, RunRecord(root).completedChain("a").completedChain("b").run("--planned", "3"))
    }

    @Test
    fun planned가_정답_줄_수와_다르면_보고를_쓰고_차이_한_줄을_적는다() {
        val r = RunRecord(root).completedChain("a").completedChain("b")
        r.run("--planned", "1")
        assertEquals(1, r.report().differences().size)
    }

    @Test
    fun planned와_expect가_모두_없으면_잃음이_있어도_종료_코드_0() {
        assertEquals(0, oneLost().run())
    }

    @Test
    fun planned와_expect가_없으면_보고에_null로_적는다() {
        val r = RunRecord(root).completedChain("a")
        r.run()
        assertNull(r.report()["planned"])
        assertNull(r.report()["expect"])
    }

    @Test
    fun 보고의_run은_실행_폴더_이름이다() {
        val r = RunRecord(root, "calibration-20261124T000000-r7").completedChain("a")
        r.run()
        assertEquals("calibration-20261124T000000-r7", r.report()["run"])
    }

    @Test
    fun planned가_0이면_입력_오류() {
        RunRecord(root).completedChain("a").assertInputError("--planned", "0")
    }

    @Test
    fun planned가_정수가_아니면_입력_오류() {
        RunRecord(root).completedChain("a").assertInputError("--planned", "abc")
    }

    @Test
    fun planned가_소수면_입력_오류() {
        RunRecord(root).completedChain("a").assertInputError("--planned", "1.5")
    }

    // ---- 명령줄 ----

    @Test
    fun 모르는_인자는_입력_오류() {
        RunRecord(root).completedChain("a").assertInputError("--verbose", "1")
    }

    @Test
    fun 값이_없는_인자는_입력_오류() {
        RunRecord(root).completedChain("a").assertInputError("--planned")
    }

    @Test
    fun run이_없으면_종료_코드_1() {
        assertEquals(1, runAnalyzer(arrayOf("--planned", "1")))
    }

    @Test
    fun run에_값이_없으면_종료_코드_1() {
        assertEquals(1, runAnalyzer(arrayOf("--run")))
    }

    @Test
    fun 실행_폴더가_없으면_입력_오류이고_보고를_쓰지_않는다() {
        val missing = root.resolve("no-such-run")
        assertEquals(1, runAnalyzer(arrayOf("--run", missing.toString())))
        assertFalse(missing.resolve("report.json").exists())
    }
}
