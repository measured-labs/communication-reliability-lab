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
            """{"answers":3,"lostTotal":0,"doubledTotal":0,"layerMismatch":0,"orphanRows":0,"pendingRows":0,"amountMismatch":0,
               "faultCountsMatch":true,
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

    // 5-3: 정답에 없는 행·PENDING 행은 기대와 상관없이 늘 불일치(종료 코드 2). 기대 값이 세 층의 합과 같으면
    // 기대 쪽 차이 줄은 없고 "늘 0" 줄 하나만 남는다 — 그래서 차이 목록으로 "세 층의 합과 견준다"를 본다.

    @Test
    fun 기대의_orphanRows는_세_층의_합과_견준다() {
        val e = writeExpect(root, """{"orphanRows":3}""")
        val r = RunRecord(root).completedChain("a")
            .row("order", "x", "COMPLETED").row("inventory", "y", "RESERVED").row("payment", "z", "APPROVED")
        r.run("--expect", e.toString())
        assertEquals(listOf("orphanRows: 늘 0, 실제 3"), r.report().differences())
    }

    @Test
    fun 기대의_pendingRows는_세_층의_합과_견준다() {
        val e = writeExpect(root, """{"pendingRows":2}""")
        val r = RunRecord(root).answer("a", 0).rows("a", "PENDING", "PENDING", null)
        r.run("--expect", e.toString())
        assertEquals(listOf("pendingRows: 늘 0, 실제 2"), r.report().differences())
    }

    @Test
    fun 정답에_없는_행은_기대가_맞아도_종료_코드_2() {
        val e = writeExpect(root, """{"orphanRows":1}""")
        val r = RunRecord(root).completedChain("a").row("order", "x", "COMPLETED")
        assertEquals(2, r.run("--expect", e.toString()))
    }

    @Test
    fun PENDING_행은_기대가_맞아도_종료_코드_2() {
        val e = writeExpect(root, """{"pendingRows":1}""")
        val r = RunRecord(root).answer("a", 0).rows("a", "PENDING", null, null)
        assertEquals(2, r.run("--expect", e.toString()))
    }

    @Test
    fun 장애_수_일치가_기대와_다르면_종료_코드_2이고_차이_한_줄() {
        val e = writeExpect(root, """{"faultCountsMatch":true}""")
        val r = RunRecord(root).answer("a", 500, "order:error") // 센 수 0 → 불일치
        assertEquals(2, r.run("--expect", e.toString()))
        assertEquals(listOf("faultCountsMatch: 기대 true, 실제 false"), r.report().differences())
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
        assertEquals(listOf("planned: 계획 1, 정답 2줄"), r.report().differences())
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

    // ---- 기대 파일 — 0과 없음이 같다(5-2) ----

    @Test
    fun 기대에_0으로_적은_서명이_실제에_없으면_일치다() {
        val e = writeExpect(
            root,
            """{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":1,"failed | - | - | -":0}}}""",
        )
        assertEquals(0, RunRecord(root).completedChain("a").run("--expect", e.toString()))
    }

    @Test
    fun 기대에_0으로_적은_라벨이_실제에_없으면_일치다() {
        val e = writeExpect(
            root,
            """{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":1},"order:error":{"failed | - | - | -":0}}}""",
        )
        assertEquals(0, RunRecord(root).completedChain("a").run("--expect", e.toString()))
    }

    @Test
    fun 기대에_0으로_적은_서명이_실제에_있으면_적지_않은_것과_같은_차이_줄이다() {
        val written = writeExpect(
            root,
            """{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":2,"completed | COMPLETED | RESERVED | -":0}}}""",
            "written.json",
        )
        val omitted = writeExpect(root, """{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":2}}}""", "omitted.json")
        val a = oneLost()
        a.run("--expect", written.toString())
        val b = RunRecord(root, "test-20261124T000000-r2").completedChain("a").completedChain("b")
            .answer("c").rows("c", "COMPLETED", "RESERVED", null)
        b.run("--expect", omitted.toString())
        assertEquals(listOf("labels['']['completed | COMPLETED | RESERVED | -']: 기대 0, 실제 1"), a.report().differences())
        assertEquals(a.report().differences(), b.report().differences())
    }

    // ---- 기대 파일 — 값의 형(5-2): 차이가 아니라 입력 오류 ----

    @Test
    fun 기대의_값이_맞는_형이면_통과한다() {
        // 아래 형 오류 테스트의 짝: 같은 기록, 같은 키에 맞는 형의 값
        val e = writeExpect(
            root,
            """{"answers":1,"amountMismatch":0,"faultCountsMatch":true,"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":1}}}""",
        )
        assertEquals(0, RunRecord(root).completedChain("a").run("--expect", e.toString()))
    }

    private fun assertExpectTypeError(json: String) {
        val e = writeExpect(root, json)
        RunRecord(root).completedChain("a").assertInputError("--expect", e.toString())
    }

    @Test
    fun 기대의_정수_키에_문자열이면_입력_오류() = assertExpectTypeError("""{"answers":"1"}""")

    @Test
    fun 기대의_정수_키에_소수면_입력_오류() = assertExpectTypeError("""{"answers":1.0}""")

    @Test
    fun 기대의_정수_키에_음수면_입력_오류() = assertExpectTypeError("""{"amountMismatch":-1}""")

    @Test
    fun 기대의_정수_키에_불이면_입력_오류() = assertExpectTypeError("""{"answers":true}""")

    @Test
    fun 기대의_faultCountsMatch가_문자열이면_입력_오류() = assertExpectTypeError("""{"faultCountsMatch":"true"}""")

    @Test
    fun 기대의_faultCountsMatch가_수면_입력_오류() = assertExpectTypeError("""{"faultCountsMatch":1}""")

    @Test
    fun 기대의_labels가_객체가_아니면_입력_오류() = assertExpectTypeError("""{"labels":[]}""")

    @Test
    fun 기대의_서명_값이_문자열이면_입력_오류() =
        assertExpectTypeError("""{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":"1"}}}""")

    @Test
    fun 기대의_서명_값이_음수면_입력_오류() =
        assertExpectTypeError("""{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":-1}}}""")

    @Test
    fun 기대의_서명_값이_소수면_입력_오류() =
        assertExpectTypeError("""{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":1.5}}}""")

    // ---- 늘 0이어야 하는 지표(5-3) — 기대 파일과 --planned 가 없어도 ----

    @Test
    fun 정답에_없는_행이_있으면_기대_파일이_없어도_종료_코드_2() {
        assertEquals(2, RunRecord(root).completedChain("a").row("inventory", "ghost", "RESERVED").run())
    }

    @Test
    fun 정답에_없는_행이_있으면_늘_0_차이_줄을_적는다() {
        val r = RunRecord(root).completedChain("a").row("inventory", "ghost", "RESERVED").row("order", "ghost", "FAILED")
        r.run()
        assertEquals(listOf("orphanRows: 늘 0, 실제 2"), r.report().differences())
    }

    @Test
    fun PENDING_행이_있으면_기대_파일이_없어도_종료_코드_2() {
        assertEquals(2, RunRecord(root).answer("a", 0).rows("a", "PENDING", null, null).run())
    }

    @Test
    fun PENDING_행이_있으면_늘_0_차이_줄을_적는다() {
        val r = RunRecord(root).answer("a", 0).rows("a", "PENDING", "PENDING", null)
        r.run()
        assertEquals(listOf("pendingRows: 늘 0, 실제 2"), r.report().differences())
    }

    @Test
    fun 금액이_다른_처리_행이_있으면_기대_파일이_없어도_종료_코드_2() {
        val r = RunRecord(root).answer("a", 201, amount = 1000).rows("a", "COMPLETED", "RESERVED", "APPROVED", amount = 1001)
        assertEquals(2, r.run())
    }

    @Test
    fun 금액이_다른_처리_행이_있으면_늘_0_차이_줄을_적는다() {
        val r = RunRecord(root).answer("a", 201, amount = 1000).rows("a", "COMPLETED", "RESERVED", "APPROVED", amount = 1001)
        r.run()
        assertEquals(listOf("amountMismatch: 늘 0, 실제 2"), r.report().differences())
    }

    @Test
    fun 늘_0_지표와_기대가_모두_어기면_줄이_둘이다() {
        val e = writeExpect(root, """{"orphanRows":0}""")
        val r = RunRecord(root).completedChain("a")
            .row("order", "x", "COMPLETED").row("inventory", "y", "RESERVED").row("payment", "z", "APPROVED")
        r.run("--expect", e.toString())
        assertEquals(listOf("orphanRows: 늘 0, 실제 3", "orphanRows: 기대 0, 실제 3"), r.report().differences())
    }

    // ---- differences 의 차례(5-4) ----

    @Test
    fun 차이_줄은_planned_늘_0_기대_정수_faultCountsMatch_labels_차례다() {
        // 기대 파일의 키는 일부러 문서의 차례와 다르게 적는다
        val e = writeExpect(
            root,
            """{"labels":{"":{"completed | COMPLETED | RESERVED | APPROVED":1}},"faultCountsMatch":true,
               "orphanRows":0,"lostTotal":1,"answers":3}""",
        )
        val r = RunRecord(root)
            .completedChain("a")
            .row("order", "ghost", "FAILED") // orphanRows 1
            .answer("p", 0).rows("p", "PENDING", null, null) // pendingRows 1
            .answer("m", 201, amount = 2000).rows("m", "COMPLETED", "RESERVED", null, amount = 2000)
            .row("payment", "m", "APPROVED", amount = 2001) // amountMismatch 1(payment)
            .answer("f", 500, "order:error") // 센 수 0 → faultCountsMatch false
        assertEquals(2, r.run("--expect", e.toString(), "--planned", "5"))
        assertEquals(
            listOf(
                "planned: 계획 5, 정답 4줄",
                "orphanRows: 늘 0, 실제 1",
                "pendingRows: 늘 0, 실제 1",
                "amountMismatch: 늘 0, 실제 1",
                "answers: 기대 3, 실제 4",
                "lostTotal: 기대 1, 실제 0",
                "orphanRows: 기대 0, 실제 1",
                "faultCountsMatch: 기대 true, 실제 false",
                "labels['']['completed | COMPLETED | RESERVED | APPROVED']: 기대 1, 실제 2",
                "labels['']['unknown | PENDING | - | -']: 기대 0, 실제 1",
                "labels['order:error']['failed | - | - | -']: 기대 0, 실제 1",
            ),
            r.report().differences(),
        )
    }

    @Test
    fun 라벨과_서명의_차이_줄은_UTF16_사전순이다() {
        // 라벨: '' < '(mixed)' < 'inventory:error' < 'order:error'
        // 서명: 'completed | …'(공백 0x20) < 'completed+failed | …'(+ 0x2B)
        // 기대의 labels 는 빈 객체 — 실제의 서명이 모두 "기대 0" 줄이 된다
        val e = writeExpect(root, """{"labels":{}}""")
        val r = RunRecord(root)
            .answer("o", 500, "order:error")
            .answer("i", 502, "inventory:error").rows("i", "FAILED", null, null)
            .answer("x", 201, "").answer("x", 500, "payment:error").rows("x", "COMPLETED", "RESERVED", "APPROVED")
            .completedChain("a")
            .answer("b", 201).answer("b", 500).rows("b", "COMPLETED", "RESERVED", "APPROVED")
        assertEquals(2, r.run("--expect", e.toString()))
        assertEquals(
            listOf(
                "labels['']['completed | COMPLETED | RESERVED | APPROVED']: 기대 0, 실제 1",
                "labels['']['completed+failed | COMPLETED | RESERVED | APPROVED']: 기대 0, 실제 1",
                "labels['(mixed)']['completed+failed | COMPLETED | RESERVED | APPROVED']: 기대 0, 실제 1",
                "labels['inventory:error']['failed | FAILED | - | -']: 기대 0, 실제 1",
                "labels['order:error']['failed | - | - | -']: 기대 0, 실제 1",
            ),
            r.report().differences(),
        )
    }

    // ---- --planned 를 수로 견준다(5절) ----

    @Test
    fun planned의_앞의_0은_받고_수로_견준다() {
        val r = RunRecord(root)
        (1..600).forEach { r.answer("r-$it", 500) }
        assertEquals(0, r.run("--planned", "0600"))
    }

    @Test
    fun planned의_앞의_0을_뗀_수를_보고에_적는다() {
        val r = RunRecord(root).completedChain("a")
        r.run("--planned", "01")
        assertEquals(1L, r.report().long("planned"))
    }

    @Test
    fun planned에_더하기_부호가_있으면_입력_오류() {
        RunRecord(root).completedChain("a").assertInputError("--planned", "+1")
    }

    @Test
    fun planned_1은_받는다() {
        // 아래 "같은 인자가 두 번" 테스트의 짝
        assertEquals(0, RunRecord(root).completedChain("a").run("--planned", "1"))
    }

    @Test
    fun 같은_인자가_두_번이면_입력_오류() {
        RunRecord(root).completedChain("a").assertInputError("--planned", "1", "--planned", "1")
    }

    // ---- --run 의 끝 / 와 이름 없는 경로(5절) ----

    @Test
    fun run의_끝_슬래시는_받는다() {
        val r = RunRecord(root, "calibration-20261124T000000-r3").completedChain("a")
        assertEquals(0, r.runWithRunArg(r.dir.toString() + "/"))
    }

    @Test
    fun run의_끝_슬래시가_있어도_보고의_run은_실행_폴더_이름이다() {
        val r = RunRecord(root, "calibration-20261124T000000-r3").completedChain("a")
        r.runWithRunArg(r.dir.toString() + "/")
        assertEquals("calibration-20261124T000000-r3", r.report()["run"])
    }

    @Test
    fun run이_루트_경로면_종료_코드_1() {
        assertEquals(1, runAnalyzer(arrayOf("--run", "/")))
    }

    @Test
    fun run이_빈_값이면_종료_코드_1() {
        assertEquals(1, runAnalyzer(arrayOf("--run", "")))
    }
}
