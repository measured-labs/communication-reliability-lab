package lab.reliability.chain

import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// docs/idempotency.md 3·5·6절 — DB 없이 만들 수 있는 범위:
// IdempotencyCounters(add·counts·reset, 종류 여섯은 KINDS 차례), Idempotency.response / firstResponse / settings / json.
// Idempotency 는 IdempotencyKeys(jdbc)를 받지만 이 테스트들은 SQL 을 부르지 않는다 — 연결하지 않는 DataSource 로 만든다.
// repeated(SQL 을 거친다)·IdempotencyKeys 의 SQL 은 DB 가 있어야 해 쓰지 않는다(문서 6절).
// 이 파일은 구현을 보지 않고 문서만으로 쓴다.
class IdempotencyTest {
    private val mapper = ObjectMapper()

    /** 연결하지 않는 JdbcClient — 부르면 연결 실패로 드러난다. */
    private fun keys() = IdempotencyKeys(JdbcClient.create(DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/none")))

    private fun idem(enabled: Boolean = true, counters: IdempotencyCounters = IdempotencyCounters()) =
        Idempotency(enabled, keys(), counters)

    /** 응답 본문을 JSON 객체로(문자열이면 읽고, 객체면 그대로 JSON 으로 돌려 읽는다). */
    private fun bodyJson(r: ResponseEntity<*>): Map<*, *> {
        val b = r.body
        val text = if (b is String) b else mapper.writeValueAsString(b)
        return mapper.readValue(text, Map::class.java)
    }

    /** 응답 본문의 바이트(문자열 그대로, 아니면 JSON). */
    private fun bodyText(r: ResponseEntity<*>): String {
        val b = r.body
        return if (b is String) b else if (b is ByteArray) String(b, Charsets.UTF_8) else mapper.writeValueAsString(b)
    }

    // ---- IdempotencyCounters ----

    @Test
    fun 센_수의_종류는_여섯이고_이_차례다() {
        assertEquals(
            listOf("new", "replayed", "in-progress", "mismatch", "resumed", "missing-key"),
            IdempotencyCounters().counts().keys.toList(),
        )
    }

    @Test
    fun 처음에는_모두_0이다() {
        assertTrue(IdempotencyCounters().counts().values.all { it == 0L })
    }

    @Test
    fun add는_그_종류만_하나_늘린다() {
        val c = IdempotencyCounters()
        c.add("replayed")
        c.add("replayed")
        c.add("missing-key")
        assertEquals(
            mapOf("new" to 0L, "replayed" to 2L, "in-progress" to 0L, "mismatch" to 0L, "resumed" to 0L, "missing-key" to 1L),
            c.counts(),
        )
    }

    @Test
    fun reset은_모두_0으로_되돌린다() {
        val c = IdempotencyCounters()
        listOf("new", "replayed", "in-progress", "mismatch", "resumed", "missing-key").forEach { c.add(it) }
        c.reset()
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L), c.counts().values.toList())
        assertEquals(6, c.counts().size)
    }

    @Test
    fun 동시에_더해도_잃지_않는다() {
        val c = IdempotencyCounters()
        val pool = Executors.newFixedThreadPool(32)
        val start = CountDownLatch(1)
        try {
            repeat(2000) { i -> pool.submit { start.await(); c.add(if (i % 2 == 0) "new" else "in-progress") } }
            start.countDown()
        } finally {
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
        assertEquals(1000L, c.counts()["new"])
        assertEquals(1000L, c.counts()["in-progress"])
    }

    // ---- response ----

    @Test
    fun Mismatch는_422와_정한_오류_본문이다() {
        val r = idem().response(Repeat.Mismatch)
        assertEquals(422, r.statusCode.value())
        assertEquals(mapOf("error" to "Idempotency-Key reused with a different amount"), bodyJson(r))
        assertNull(r.headers.getFirst("Idempotent-Replayed"))
    }

    @Test
    fun InProgress는_409와_정한_오류_본문이다() {
        val r = idem().response(Repeat.InProgress)
        assertEquals(409, r.statusCode.value())
        assertEquals(mapOf("error" to "a request with the same Idempotency-Key is in progress"), bodyJson(r))
        assertNull(r.headers.getFirst("Idempotent-Replayed"))
    }

    @Test
    fun Replay는_저장한_상태와_본문_그대로에_되돌림_헤더를_붙인다() {
        val stored = """{"requestId":"r1","status":"COMPLETED","outcome":"Completed"}"""
        val r = idem().response(Repeat.Replay(201, stored))
        assertEquals(201, r.statusCode.value())
        assertEquals(stored, bodyText(r))
        assertEquals("true", r.headers.getFirst("Idempotent-Replayed"))
    }

    @Test
    fun 저장한_실패도_그_상태로_되돌려_준다() {
        val stored = """{"requestId":"r1","status":"FAILED","outcome":"Failed:HTTP_500"}"""
        val r = idem().response(Repeat.Replay(502, stored))
        assertEquals(502, r.statusCode.value())
        assertEquals(stored, bodyText(r))
        assertEquals("true", r.headers.getFirst("Idempotent-Replayed"))
    }

    @Test
    fun Resume은_응답이_아니다() {
        assertFailsWith<IllegalStateException> { idem().response(Repeat.Resume(1)) }
    }

    // ---- firstResponse ----

    @Test
    fun 처음_응답은_그_상태와_본문이고_되돌림_헤더가_없다() {
        val body = """{"requestId":"r1","status":"UNKNOWN","outcome":"Unknown:REQUEST_TIMEOUT"}"""
        val r = idem().firstResponse(504, body)
        assertEquals(504, r.statusCode.value())
        assertEquals(body, bodyText(r))
        assertNull(r.headers.getFirst("Idempotent-Replayed"))
    }

    @Test
    fun 처음_응답과_되돌려_주는_응답은_같은_바이트다() {
        val i = idem()
        val body = i.json(mapOf("requestId" to "r1", "status" to "COMPLETED", "outcome" to "Completed"))
        assertEquals(bodyText(i.firstResponse(201, body)), bodyText(i.response(Repeat.Replay(201, body))))
    }

    // ---- json ----

    @Test
    fun json은_JSON_객체_문자열이다() {
        val text = idem().json(mapOf("requestId" to "r1", "status" to "APPROVED"))
        assertEquals(mapOf("requestId" to "r1", "status" to "APPROVED"), mapper.readValue(text, Map::class.java))
    }

    // ---- settings ----

    @Test
    fun 켜진_설정() {
        assertEquals(
            mapOf(
                "enabled" to true,
                "header" to "Idempotency-Key",
                "inProgress" to "reject-409",
                "mismatch" to "reject-422",
                "replayHeader" to "Idempotent-Replayed",
                "inProgressAsUnknown" to false,
            ),
            idem(true).settings(),
        )
    }

    @Test
    fun 켜진_설정은_409를_읽는_설정을_그대로_낸다() {
        // docs/idempotency.md 1절·3-1절: 3-1절의 설정을 켜면 "inProgressAsUnknown": true
        val s = Idempotency(true, keys(), IdempotencyCounters(), inProgressAsUnknown = true).settings()
        assertEquals(true, s["inProgressAsUnknown"])
        assertEquals(true, s["enabled"])
        assertEquals(6, s.size, s.toString())
    }

    @Test
    fun 꺼진_설정은_409를_읽는_설정이_없다() {
        assertEquals(mapOf("enabled" to false), Idempotency(false, keys(), IdempotencyCounters()).settings())
    }

    // ---- IdempotencySettings (6절: enabled=false 인데 inProgressAsUnknown=true 면 IllegalArgumentException) ----

    @Test
    fun 키가_꺼졌는데_409를_결과_모름으로_읽게_하면_설정이_거절된다() {
        assertFailsWith<IllegalArgumentException> { IdempotencySettings(enabled = false, inProgressAsUnknown = true) }
    }

    @Test
    fun 키가_켜졌으면_409를_결과_모름으로_읽는_설정을_받는다() {
        assertEquals(true, IdempotencySettings(enabled = true, inProgressAsUnknown = true).inProgressAsUnknown)
    }

    @Test
    fun 기본은_409를_실패로_읽는다() {
        assertEquals(false, IdempotencySettings(enabled = true).inProgressAsUnknown)
        assertEquals(false, IdempotencySettings(enabled = false).inProgressAsUnknown)
        assertEquals(false, Idempotency(true, keys(), IdempotencyCounters()).inProgressAsUnknown)
    }

    @Test
    fun 꺼진_설정은_enabled_하나다() {
        assertEquals(mapOf("enabled" to false), idem(false).settings())
    }

    @Test
    fun response는_SQL을_부르지_않고_센_수를_바꾸지_않는다() {
        // 6절: 세는 것은 repeated — response 는 응답만 만든다(연결하지 않는 DB 로도 돈다)
        val c = IdempotencyCounters()
        val i = idem(counters = c)
        i.response(Repeat.Mismatch)
        i.response(Repeat.InProgress)
        i.response(Repeat.Replay(201, "{}"))
        assertTrue(c.counts().values.all { it == 0L }, c.counts().toString())
    }
}
