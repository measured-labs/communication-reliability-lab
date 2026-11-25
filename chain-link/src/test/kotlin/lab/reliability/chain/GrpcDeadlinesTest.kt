package lab.reliability.chain

import io.grpc.Context
import io.grpc.Metadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// docs/experiments/task-05-design.md 9절의 입구 — GrpcHeaders(메타데이터), GrpcDeadlines(아래 호출의 문맥, 시작해도 되나,
// 기한의 절대 시각, 취소를 보며 자기), StopReason. 3절(조건 넷 — G1 넘기지 않음 · G2 넘김 · G3 봄)의 규칙.
// 이 파일은 구현을 보지 않고 문서만으로 쓴다.
class GrpcDeadlinesTest {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    @AfterEach
    fun stop() {
        scheduler.shutdownNow()
    }

    private fun key(name: String): Metadata.Key<String> = Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER)

    // ---- 메타데이터 ----

    @Test
    fun 내보내는_메타데이터의_키는_HTTP_헤더_이름의_소문자이고_값은_그대로다() {
        val md = GrpcHeaders.outbound("r-1", "payment:delay=1500", "inventory:error@1", "payment:drop", "k-9")
        assertEquals(
            listOf<String?>("r-1", "payment:delay=1500", "inventory:error@1", "payment:drop", "k-9"),
            listOf("x-request-id", "x-fault", "x-attempt-fault", "x-drop-fault", "idempotency-key").map { md.get(key(it)) },
        )
    }

    @Test
    fun null인_값은_붙이지_않는다() {
        val md = GrpcHeaders.outbound("r-1", null, null, null, null)
        assertEquals(
            listOf(true, false, false, false, false),
            listOf("x-request-id", "x-fault", "x-attempt-fault", "x-drop-fault", "idempotency-key").map { md.containsKey(key(it)) },
        )
    }

    @Test
    fun 빈_값도_바꾸지_않고_넘긴다() {
        // null 만 붙이지 않는다 — 빈 문자열은 값이다(바꾸지 않는다)
        val md = GrpcHeaders.outbound("r-1", "", null, null, null)
        assertEquals("", md.get(key("x-fault")))
    }

    @Test
    fun 받은_메타데이터를_읽는다() {
        val md = Metadata().also {
            it.put(key("x-request-id"), "r-2")
            it.put(key("x-fault"), " payment:delay=600 ")
            it.put(key("x-attempt-fault"), "payment:error@1")
            it.put(key("x-drop-fault"), "payment:drop")
            it.put(key("idempotency-key"), "k-1")
        }
        val i = GrpcHeaders.read(md)
        assertEquals(
            listOf<String?>("r-2", " payment:delay=600 ", "payment:error@1", "payment:drop", "k-1"),
            listOf(i.requestId, i.rawFault, i.rawAttemptFault, i.rawDropFault, i.idempotencyKey),
        )
    }

    @Test
    fun 받은_메타데이터에_없는_것은_null이다() {
        val i = GrpcHeaders.read(Metadata())
        assertEquals(listOf<String?>(null, null, null, null, null), listOf(i.requestId, i.rawFault, i.rawAttemptFault, i.rawDropFault, i.idempotencyKey))
    }

    @Test
    fun 내보낸_것을_읽으면_같은_값이다() {
        val i = GrpcHeaders.read(GrpcHeaders.outbound("r-3", "inventory:delay=2500", null, "payment:drop", null))
        assertEquals(
            listOf<String?>("r-3", "inventory:delay=2500", null, "payment:drop", null),
            listOf(i.requestId, i.rawFault, i.rawAttemptFault, i.rawDropFault, i.idempotencyKey),
        )
    }

    // ---- 아래 호출의 문맥 ----

    private fun withDeadline(ms: Long): Context.CancellableContext =
        Context.current().withDeadlineAfter(ms, TimeUnit.MILLISECONDS, scheduler)

    @Test
    fun 전파를_켜면_받은_문맥_그대로다() {
        val current = withDeadline(10_000)
        try {
            assertSame(current, GrpcDeadlines.downstreamContext(current, true))
        } finally {
            current.cancel(null)
        }
    }

    @Test
    fun 전파를_켜면_위의_취소가_아래_문맥에_닿는다() {
        val current = withDeadline(10_000)
        val down = GrpcDeadlines.downstreamContext(current, true)
        current.cancel(null)
        assertTrue(down.isCancelled)
    }

    @Test
    fun 전파를_끄면_기한이_없다() {
        val current = withDeadline(10_000)
        try {
            assertEquals(listOf(true, true), listOf(current.deadline != null, GrpcDeadlines.downstreamContext(current, false).deadline == null))
        } finally {
            current.cancel(null)
        }
    }

    @Test
    fun 전파를_끄면_위가_취소돼도_아래_문맥은_취소되지_않는다() {
        val current = withDeadline(10_000)
        val down = GrpcDeadlines.downstreamContext(current, false)
        current.cancel(null)
        assertEquals(listOf(true, false), listOf(current.isCancelled, down.isCancelled))
    }

    @Test
    fun 전파를_끄면_위의_기한이_지나도_아래_문맥은_취소되지_않는다() {
        val current = withDeadline(20)
        val down = GrpcDeadlines.downstreamContext(current, false)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!current.isCancelled && System.nanoTime() < deadline) Thread.sleep(5)
        assertEquals(listOf(true, false), listOf(current.isCancelled, down.isCancelled))
    }

    @Test
    fun 전파를_끄면_이미_취소된_문맥에서도_취소되지_않은_문맥을_준다() {
        val current = withDeadline(10_000)
        current.cancel(null)
        assertFalse(GrpcDeadlines.downstreamContext(current, false).isCancelled)
    }

    // ---- 시작해도 되나 ----

    @Test
    fun 취소되지_않았고_남은_기한이_있으면_시작한다() {
        assertTrue(GrpcDeadlines.shouldStart(false, 1))
    }

    @Test
    fun 기한이_없으면_시작한다() {
        assertTrue(GrpcDeadlines.shouldStart(false, null))
    }

    @Test
    fun 남은_기한이_0이면_시작하지_않는다() {
        assertFalse(GrpcDeadlines.shouldStart(false, 0))
    }

    @Test
    fun 남은_기한이_음수면_시작하지_않는다() {
        assertFalse(GrpcDeadlines.shouldStart(false, -1))
    }

    @Test
    fun 취소됐으면_기한과_무관하게_시작하지_않는다() {
        assertEquals(listOf(false, false), listOf(GrpcDeadlines.shouldStart(true, null), GrpcDeadlines.shouldStart(true, 1_000_000_000)))
    }

    // ---- 기한의 절대 시각 ----

    @Test
    fun 기한의_절대_시각은_지금_더하기_남은_기한의_마이크로초다() {
        assertEquals(3_000L, GrpcDeadlines.absoluteDeadlineUs(1_000, 2_000_000))
    }

    @Test
    fun 남은_기한의_마이크로초_아래는_버린다() {
        assertEquals(listOf<Long?>(1_001, 1_000), listOf(GrpcDeadlines.absoluteDeadlineUs(1_000, 1_999), GrpcDeadlines.absoluteDeadlineUs(1_000, 999)))
    }

    @Test
    fun 기한이_없으면_null() {
        assertNull(GrpcDeadlines.absoluteDeadlineUs(1_000, null))
    }

    @Test
    fun 음수_남은_기한도_그대로_더한다() {
        assertEquals(998L, GrpcDeadlines.absoluteDeadlineUs(1_000, -2_000))
    }

    @Test
    fun 음수_남은_기한의_마이크로초_아래는_floor로_내린다() {
        // 9절: nowUs + floor(remainingNanos / 1000) — floor(−1.5) = −2 (0 쪽으로 자르면 −1)
        assertEquals(998L, GrpcDeadlines.absoluteDeadlineUs(1_000, -1_500))
    }

    @Test
    fun 음수_1나노초는_1마이크로초를_뺀다() {
        // 9절: -1 ns → -1 μs (Math.floorDiv), -1000 ns → -1 μs
        assertEquals(listOf<Long?>(999, 999), listOf(GrpcDeadlines.absoluteDeadlineUs(1_000, -1), GrpcDeadlines.absoluteDeadlineUs(1_000, -1_000)))
    }

    // ---- 취소를 보며 자기 ----

    @Test
    fun 취소되지_않으면_다_자고_true() {
        val started = System.nanoTime()
        assertTrue(GrpcDeadlines.awaitDelay(50, CountDownLatch(1)))
        assertTrue(System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(50), "다 자야 한다")
    }

    @Test
    fun 자는_중에_취소되면_멈추고_false() {
        val latch = CountDownLatch(1)
        scheduler.schedule({ latch.countDown() }, 50, TimeUnit.MILLISECONDS)
        val started = System.nanoTime()
        assertFalse(GrpcDeadlines.awaitDelay(10_000, latch))
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5), "취소되면 곧 멈춰야 한다")
    }

    @Test
    fun 이미_취소됐으면_바로_false() {
        val latch = CountDownLatch(1).also { it.countDown() }
        val started = System.nanoTime()
        assertFalse(GrpcDeadlines.awaitDelay(10_000, latch))
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(1), "바로 돌아와야 한다")
    }

    @Test
    fun 잘_시간이_0이고_취소되지_않았으면_true() {
        assertTrue(GrpcDeadlines.awaitDelay(0, CountDownLatch(1)))
    }

    @Test
    fun 잘_시간이_0이어도_이미_취소됐으면_false() {
        // 9절: ms 0 — 이미 열려 있으면 false(열린 것이 먼저다)
        assertFalse(GrpcDeadlines.awaitDelay(0, CountDownLatch(1).also { it.countDown() }))
    }

    // ---- 멈춘 자리 ----

    @Test
    fun 멈춘_자리의_값은_시각_기록의_낱말이다() {
        assertEquals(
            listOf("deadline-before-start", "cancelled-in-delay", "deadline-before-work"),
            listOf(StopReason.BEFORE_START, StopReason.IN_DELAY, StopReason.BEFORE_WORK),
        )
    }

    @Test
    fun 멈춘_자리는_셋뿐이다() {
        // 탐침(컴파일 오류)으로 안 형: 자리는 String 상수, ALL 은 String 의 모음
        assertEquals(
            setOf("deadline-before-start", "cancelled-in-delay", "deadline-before-work"),
            StopReason.ALL.toSet(),
        )
    }
}
