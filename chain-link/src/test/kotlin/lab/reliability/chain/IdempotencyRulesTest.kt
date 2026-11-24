package lab.reliability.chain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// docs/idempotency.md 3절 "다시 온 키"의 표 — IdemotencyRules.onRepeat(row, amount)는 차례 1~4 가운데 처음 맞는 것.
// 1 금액이 다르면 Mismatch · 2 COMPLETED·FAILED 면 Replay(저장한 상태, 본문) — 응답 칸이 null 이면 IllegalStateException
// · 3 UNKNOWN 이면 Resume(rowId) · 4 IN_PROGRESS 면 InProgress.
// 그리고 KeyState.of(LayerOutcome) — Completed→COMPLETED, Failed→FAILED, Unknown→UNKNOWN.
// 이 파일은 구현을 보지 않고 docs/idempotency.md 만으로 쓴다.
class IdempotencyRulesTest {
    private val body201 = """{"requestId":"r1","status":"COMPLETED","outcome":"Completed"}"""
    private val body502 = """{"requestId":"r1","status":"FAILED","outcome":"Failed:HTTP_500"}"""

    private fun row(
        state: KeyState,
        status: Int? = null,
        body: String? = null,
        amount: Long = 1234,
        rowId: Long = 77,
    ) = KeyRow("k1", "r1", rowId, amount, state, status, body)

    // ---- 2: 저장한 응답 ----

    @Test
    fun COMPLETED면_저장한_상태와_본문을_돌려준다() {
        assertEquals(Repeat.Replay(201, body201), IdempotencyRules.onRepeat(row(KeyState.COMPLETED, 201, body201), 1234))
    }

    @Test
    fun FAILED도_저장한_상태와_본문을_돌려준다() {
        assertEquals(Repeat.Replay(502, body502), IdempotencyRules.onRepeat(row(KeyState.FAILED, 502, body502), 1234))
    }

    @Test
    fun 저장한_응답은_결과와_무관하게_칸의_값_그대로다() {
        // 저장한 상태를 정하는 것은 처음 처리 — 규칙은 칸의 값을 그대로 낸다(결제의 201 도, 다른 값도)
        assertEquals(Repeat.Replay(299, "x"), IdempotencyRules.onRepeat(row(KeyState.COMPLETED, 299, "x"), 1234))
    }

    @Test
    fun COMPLETED인데_응답_상태가_null이면_IllegalStateException() {
        assertFailsWith<IllegalStateException> { IdempotencyRules.onRepeat(row(KeyState.COMPLETED, null, body201), 1234) }
    }

    @Test
    fun COMPLETED인데_응답_본문이_null이면_IllegalStateException() {
        assertFailsWith<IllegalStateException> { IdempotencyRules.onRepeat(row(KeyState.COMPLETED, 201, null), 1234) }
    }

    @Test
    fun FAILED인데_응답_칸이_null이면_IllegalStateException() {
        assertFailsWith<IllegalStateException> { IdempotencyRules.onRepeat(row(KeyState.FAILED, null, null), 1234) }
        assertFailsWith<IllegalStateException> { IdempotencyRules.onRepeat(row(KeyState.FAILED, 502, null), 1234) }
        assertFailsWith<IllegalStateException> { IdempotencyRules.onRepeat(row(KeyState.FAILED, null, body502), 1234) }
    }

    // ---- 3: 이어 하기 ----

    @Test
    fun UNKNOWN이면_저장한_업무_행으로_이어_한다() {
        assertEquals(Repeat.Resume(77), IdempotencyRules.onRepeat(row(KeyState.UNKNOWN, 504, "{}", rowId = 77), 1234))
    }

    @Test
    fun UNKNOWN은_응답_칸이_null이어도_이어_한다() {
        // 응답 칸이 null 이면 예외인 것은 2(저장한 응답)뿐이다
        assertEquals(Repeat.Resume(5), IdempotencyRules.onRepeat(row(KeyState.UNKNOWN, null, null, rowId = 5), 1234))
    }

    @Test
    fun 이어_하기의_rowId는_키_행의_rowId다() {
        assertEquals(Repeat.Resume(9_000_000_001), IdempotencyRules.onRepeat(row(KeyState.UNKNOWN, 504, "{}", rowId = 9_000_000_001), 1234))
    }

    // ---- 4: 처리 중 ----

    @Test
    fun IN_PROGRESS면_409_갈래다() {
        assertEquals(Repeat.InProgress, IdempotencyRules.onRepeat(row(KeyState.IN_PROGRESS), 1234))
    }

    @Test
    fun IN_PROGRESS는_응답_칸이_있어도_409_갈래다() {
        assertEquals(Repeat.InProgress, IdempotencyRules.onRepeat(row(KeyState.IN_PROGRESS, 201, body201), 1234))
    }

    // ---- 1: 금액이 다르면 — 상태보다 먼저 ----

    @Test
    fun 금액이_다르면_COMPLETED여도_거절한다() {
        assertEquals(Repeat.Mismatch, IdempotencyRules.onRepeat(row(KeyState.COMPLETED, 201, body201, amount = 1234), 1235))
    }

    @Test
    fun 금액이_다르면_FAILED여도_거절한다() {
        assertEquals(Repeat.Mismatch, IdempotencyRules.onRepeat(row(KeyState.FAILED, 502, body502, amount = 1234), 1233))
    }

    @Test
    fun 금액이_다르면_UNKNOWN이어도_이어_하지_않는다() {
        assertEquals(Repeat.Mismatch, IdempotencyRules.onRepeat(row(KeyState.UNKNOWN, 504, "{}", amount = 1234), 0))
    }

    @Test
    fun 금액이_다르면_IN_PROGRESS여도_422_갈래다() {
        assertEquals(Repeat.Mismatch, IdempotencyRules.onRepeat(row(KeyState.IN_PROGRESS, amount = 1234), -1234))
    }

    @Test
    fun 금액_검사는_응답_칸이_null인_예외보다_먼저다() {
        assertEquals(Repeat.Mismatch, IdempotencyRules.onRepeat(row(KeyState.COMPLETED, null, null, amount = 1), 2))
    }

    @Test
    fun 금액이_같으면_상태로_간다() {
        // 경계의 안쪽 — 큰 값도 정확히 같으면 같다
        val big = Long.MAX_VALUE
        assertEquals(Repeat.Replay(201, body201), IdempotencyRules.onRepeat(row(KeyState.COMPLETED, 201, body201, amount = big), big))
        assertEquals(Repeat.Mismatch, IdempotencyRules.onRepeat(row(KeyState.COMPLETED, 201, body201, amount = big), big - 1))
    }

    // ---- KeyState.of ----

    @Test
    fun 아래_층이_처리했으면_COMPLETED() {
        assertEquals(KeyState.COMPLETED, KeyState.of(LayerOutcome.Completed))
    }

    @Test
    fun 아래_층이_처리하지_않았으면_FAILED() {
        assertEquals(KeyState.FAILED, KeyState.of(LayerOutcome.Failed("HTTP_500")))
        assertEquals(KeyState.FAILED, KeyState.of(LayerOutcome.Failed("HTTP_409")))
    }

    @Test
    fun 결과를_모르면_UNKNOWN() {
        assertEquals(KeyState.UNKNOWN, KeyState.of(LayerOutcome.Unknown("REQUEST_TIMEOUT")))
        assertEquals(KeyState.UNKNOWN, KeyState.of(LayerOutcome.Unknown("IO_IOException")))
    }

    @Test
    fun 키_상태는_넷이다() {
        assertEquals(listOf("IN_PROGRESS", "COMPLETED", "FAILED", "UNKNOWN"), KeyState.entries.map { it.name })
    }

    @Test
    fun 키_헤더의_이름() {
        assertEquals("Idempotency-Key", ChainHeaders.IDEMPOTENCY_KEY)
    }
}
