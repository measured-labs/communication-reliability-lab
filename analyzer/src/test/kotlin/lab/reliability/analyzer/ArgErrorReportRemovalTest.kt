package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.assertEquals

// docs/measurement.md 5절 "인자 오류에서 있던 보고를 지우는 경계" — 대조 분석과 하위 명령 timing·retry·idem·overload·transport
// 모두 같은 규칙. 실행 폴더를 알 수 있으면(첫 --run 바로 뒤에 값이 있고, --로 시작하지 않고, 빈 값·/ 가 아님) 그 폴더의
// **그 명령의** 보고를 지운다(모르는 인자, 짝이 안 맞음, --run 두 번 — 첫 값의 폴더, --expect 의 꼴).
// 알 수 없으면 아무것도 지우지 않는다(--run 없음, --run 이 마지막, --run 뒤가 다른 인자 이름, 빈 값). 종료 코드는 모두 1.
// 출력 파일: 대조 분석 report.json·report.md(5절), timing report-timing.json·metrics.tsv(7-4), retry report-retry.json·
// metrics-retry.tsv(8-3), idem report-idem.json·metrics-idem.tsv(9-3), overload report-overload.json·metrics-overload.tsv(10),
// transport report-transport.json·metrics-transport.tsv(11-3).
// 테스트마다 여섯 명령을 모두 돌려 명령 → (종료 코드, 남은 파일)을 한 번에 견준다(어느 명령이 어긋나는지 실패 메시지에 다 나온다).
// 이 파일은 분석기의 구현을 보지 않고 문서만으로 쓴다. 폴더에는 입력이 없다 — 인자에서 멈춰야 하는 경우만 본다.
class ArgErrorReportRemovalTest {
    @TempDir
    lateinit var root: Path

    /** 명령 이름("" = 대조 분석) → 그 명령의 두 출력 파일 */
    private val outputs = linkedMapOf(
        "" to listOf("report.json", "report.md"),
        "timing" to listOf("report-timing.json", "metrics.tsv"),
        "retry" to listOf("report-retry.json", "metrics-retry.tsv"),
        "idem" to listOf("report-idem.json", "metrics-idem.tsv"),
        "overload" to listOf("report-overload.json", "metrics-overload.tsv"),
        "transport" to listOf("report-transport.json", "metrics-transport.tsv"),
    )
    private val allOutputs = outputs.values.flatten()

    /** 모든 명령의 옛 보고가 든 실행 폴더(명령마다 따로 — 앞 명령이 지운 것이 섞이지 않게) */
    private fun runDir(name: String): Path =
        root.resolve(name).createDirectories().also { d -> allOutputs.forEach { d.resolve(it).writeText("old") } }

    private fun cli(cmd: String, args: List<String>): Int =
        (if (cmd.isEmpty()) analyzerCli(*args.toTypedArray()) else analyzerCli(cmd, *args.toTypedArray())).first

    private fun remaining(dir: Path) = allOutputs.filter { dir.resolve(it).exists() }

    /**
     * 명령마다 새 폴더 둘(first·second)을 만들고 [args](폴더 → 인자)로 돌린다.
     * 돌려주는 것: 명령 → "종료 코드 | first 에 남은 것 | second 에 남은 것".
     */
    private fun runAll(args: (first: Path, second: Path) -> List<String>): Map<String, String> =
        outputs.keys.associateWith { cmd ->
            val tag = cmd.ifEmpty { "reconcile" }
            val first = runDir("$tag-first-20261125T000000-r1")
            val second = runDir("$tag-second-20261125T000000-r1")
            val code = cli(cmd, args(first, second))
            "$code | ${remaining(first)} | ${remaining(second)}"
        }

    /** 그 명령의 두 파일만 first 에서 지워지고 second 는 그대로 */
    private fun removedOwnInFirst(): Map<String, String> =
        outputs.mapValues { (_, own) -> "1 | ${allOutputs - own.toSet()} | $allOutputs" }

    private fun removedNothing(): Map<String, String> = outputs.mapValues { "1 | $allOutputs | $allOutputs" }

    // ---- 실행 폴더를 알 수 있다 → 그 명령의 보고만 지운다 ----

    @Test
    fun 모르는_인자면_그_폴더의_그_명령의_보고만_지운다() {
        assertEquals(removedOwnInFirst(), runAll { f, _ -> listOf("--run", f.toString(), "--bogus", "x") })
    }

    @Test
    fun 짝이_안_맞으면_그_폴더의_그_명령의_보고만_지운다() {
        assertEquals(removedOwnInFirst(), runAll { f, _ -> listOf("--run", f.toString(), "--expect") })
    }

    @Test
    fun run이_두_번이면_첫_값의_폴더에서만_지운다() {
        assertEquals(removedOwnInFirst(), runAll { f, s -> listOf("--run", f.toString(), "--run", s.toString()) })
    }

    @Test
    fun 기대_파일이_없으면_그_명령의_보고를_지운다() {
        // --expect 의 꼴(파일 없음) — 대조 분석은 5절, 하위 명령은 각 절의 입력 오류. 인자를 지난 뒤의 오류여도 지운다
        assertEquals(removedOwnInFirst(), runAll { f, _ -> listOf("--run", f.toString(), "--expect", f.resolve("nope.json").toString()) })
    }

    @Test
    fun 끝의_슬래시가_있는_run도_그_폴더다() {
        assertEquals(removedOwnInFirst(), runAll { f, _ -> listOf("--run", "$f/", "--bogus", "x") })
    }

    // ---- 실행 폴더를 알 수 없다 → 아무것도 지우지 않는다 ----

    @Test
    fun run이_마지막_인자면_아무것도_지우지_않는다() {
        assertEquals(removedNothing(), runAll { f, _ -> listOf("--bogus", f.toString(), "--run") })
    }

    @Test
    fun run_뒤가_다른_인자_이름이면_아무것도_지우지_않는다() {
        assertEquals(removedNothing(), runAll { f, _ -> listOf("--run", "--expect", f.toString()) })
    }

    @Test
    fun run이_빈_값이면_아무것도_지우지_않는다() {
        assertEquals(removedNothing(), runAll { f, _ -> listOf("--run", "", "--bogus", f.toString()) })
    }

    @Test
    fun run이_없으면_아무것도_지우지_않는다() {
        assertEquals(removedNothing(), runAll { f, _ -> listOf("--bogus", f.toString()) })
    }
}
