package lab.reliability.chain

import io.grpc.Metadata
import io.grpc.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// docs/experiments/task-05-design.md 2절(gRPC의 끝을 기존 갈래로 읽는 규칙)과 9절의 입구 — Transport, GrpcOutcomes.
// 서버: 2xx → (OK, null), 그 밖 → (FAILED_PRECONDITION, 그 수).
// 부른 쪽(차례대로 처음 맞는 것): 트레일러 chain-status 가 있으면 HTTP와 같은 규칙(2xx Completed · 504 Unknown:CHAIN_504 ·
// 그 밖 Failed:CHAIN_<n>) — 코드는 보지 않는다. 없고 OK 면 Completed. 없고 그 밖이면 Unknown:<코드 이름>.
// 이 파일은 구현을 보지 않고 문서만으로 쓴다(LayerOutcome 의 이름은 RetryFixtures 의 nameOf 로 읽는다).
class GrpcOutcomesTest {
    private fun read(code: Status.Code, chainStatus: Int?) = nameOf(GrpcOutcomes.read(code, chainStatus))

    private val chainKey: Metadata.Key<String> = Metadata.Key.of("chain-status", Metadata.ASCII_STRING_MARSHALLER)

    private fun trailers(value: String?) = Metadata().also { md -> value?.let { md.put(chainKey, it) } }

    // ---- 전송 해석 ----

    @Test
    fun http와_grpc를_읽는다() {
        assertEquals(listOf("http", "grpc"), listOf(Transport.parse("http").label, Transport.parse("grpc").label))
    }

    @Test
    fun 전송의_앞뒤_공백은_무시한다() {
        assertEquals(listOf("http", "grpc"), listOf(Transport.parse(" http ").label, Transport.parse("\tgrpc\n").label))
    }

    @Test
    fun 모르는_전송은_IllegalArgumentException() {
        for (raw in listOf("tcp", "", "  ", "http2", "grpc-web")) {
            assertFailsWith<IllegalArgumentException>("'$raw'") { Transport.parse(raw) }
        }
    }

    @Test
    fun 전송_이름은_소문자_둘만_받는다() {
        // 9절: "http"·"grpc"(앞뒤 공백 무시) 밖은 IllegalArgumentException — 대소문자를 무시한다는 말은 없다
        for (raw in listOf("HTTP", "Grpc")) {
            assertFailsWith<IllegalArgumentException>("'$raw'") { Transport.parse(raw) }
        }
    }

    // ---- 서버가 닫는 꼴 ----

    @Test
    fun 서버는_2xx면_OK로_트레일러_없이_닫는다() {
        for (s in listOf(200, 201, 299)) {
            val c = GrpcOutcomes.close(s)
            assertEquals(Status.Code.OK to null, c.code to c.chainStatus, "status $s")
        }
    }

    @Test
    fun 서버는_2xx가_아니면_FAILED_PRECONDITION과_그_수로_닫는다() {
        for (s in listOf(199, 300, 400, 500, 502, 504)) {
            val c = GrpcOutcomes.close(s)
            assertEquals(Status.Code.FAILED_PRECONDITION to s, c.code to c.chainStatus, "status $s")
        }
    }

    // ---- 트레일러가 있을 때 — HTTP와 같은 표 ----

    @Test
    fun 트레일러가_2xx면_완료() {
        assertEquals(listOf("Completed", "Completed", "Completed"), listOf(200, 201, 299).map { read(Status.Code.FAILED_PRECONDITION, it) })
    }

    @Test
    fun 트레일러가_504면_결과_모름() {
        assertEquals("Unknown:CHAIN_504", read(Status.Code.FAILED_PRECONDITION, 504))
    }

    @Test
    fun 트레일러가_2xx도_504도_아니면_실패() {
        assertEquals(
            listOf("Failed:CHAIN_199", "Failed:CHAIN_300", "Failed:CHAIN_400", "Failed:CHAIN_500", "Failed:CHAIN_502", "Failed:CHAIN_503"),
            listOf(199, 300, 400, 500, 502, 503).map { read(Status.Code.FAILED_PRECONDITION, it) },
        )
    }

    @Test
    fun 트레일러가_있으면_코드는_보지_않는다() {
        assertEquals(
            listOf("Failed:CHAIN_502", "Completed", "Unknown:CHAIN_504", "Failed:CHAIN_500"),
            listOf(
                read(Status.Code.OK, 502),
                read(Status.Code.DEADLINE_EXCEEDED, 201),
                read(Status.Code.CANCELLED, 504),
                read(Status.Code.UNAVAILABLE, 500),
            ),
        )
    }

    // ---- 트레일러가 없을 때 ----

    @Test
    fun 트레일러_없이_OK면_완료() {
        assertEquals("Completed", read(Status.Code.OK, null))
    }

    @Test
    fun 트레일러_없이_DEADLINE_EXCEEDED면_결과_모름() {
        assertEquals("Unknown:DEADLINE_EXCEEDED", read(Status.Code.DEADLINE_EXCEEDED, null))
    }

    @Test
    fun 트레일러_없이_CANCELLED면_결과_모름() {
        assertEquals("Unknown:CANCELLED", read(Status.Code.CANCELLED, null))
    }

    @Test
    fun 트레일러_없이_UNAVAILABLE이면_실패가_아니라_결과_모름() {
        assertEquals("Unknown:UNAVAILABLE", read(Status.Code.UNAVAILABLE, null))
    }

    @Test
    fun 트레일러_없이_OK가_아닌_코드는_모두_결과_모름이고_이름은_코드_이름이다() {
        for (code in Status.Code.values().filter { it != Status.Code.OK }) {
            assertEquals("Unknown:${code.name}", read(code, null), "code $code")
        }
    }

    @Test
    fun 트레일러_없는_FAILED_PRECONDITION도_결과_모름이다() {
        // 갈래는 트레일러의 수로 읽고 코드 이름으로 읽지 않는다
        assertEquals("Unknown:FAILED_PRECONDITION", read(Status.Code.FAILED_PRECONDITION, null))
    }

    @Test
    fun 결과_이름은_callOutcome의_꼴이다() {
        // docs/measurement.md 7-2: Completed|(Failed|Unknown):\S+ 전체
        val form = Regex("""Completed|(Failed|Unknown):\S+""")
        val names = Status.Code.values().map { read(it, null) } + listOf(201, 400, 500, 504).map { read(Status.Code.FAILED_PRECONDITION, it) }
        for (n in names) assertTrue(form.matches(n), "꼴이 틀린 이름: '$n'")
    }

    @Test
    fun 서버가_닫은_꼴을_부른_쪽이_읽으면_HTTP_경로와_같은_갈래다() {
        // HTTP 경로의 갈래: 2xx 완료 · 504 결과 모름 · 그 밖 실패(처리 코드가 내는 수 — 201·502·504·500·400)
        assertEquals(
            listOf("Completed", "Failed:CHAIN_502", "Unknown:CHAIN_504", "Failed:CHAIN_500", "Failed:CHAIN_400"),
            listOf(201, 502, 504, 500, 400).map { s -> GrpcOutcomes.close(s).let { read(it.code, it.chainStatus) } },
        )
    }

    // ---- 트레일러 읽기 ----

    @Test
    fun 트레일러의_키_이름은_chain_status다() {
        assertEquals("chain-status", GrpcOutcomes.CHAIN_STATUS.name())
    }

    @Test
    fun 트레일러의_수를_읽는다() {
        assertEquals(504, GrpcOutcomes.chainStatusOf(trailers("504")))
    }

    @Test
    fun 트레일러의_앞뒤_공백은_무시한다() {
        assertEquals(502, GrpcOutcomes.chainStatusOf(trailers(" 502 ")))
    }

    @Test
    fun 트레일러가_없으면_null() {
        assertEquals(listOf<Int?>(null, null), listOf(GrpcOutcomes.chainStatusOf(null), GrpcOutcomes.chainStatusOf(Metadata())))
    }

    @Test
    fun 트레일러가_정수가_아니면_null() {
        for (v in listOf("abc", "", "5.0", "50x")) assertNull(GrpcOutcomes.chainStatusOf(trailers(v)), "'$v'")
    }

    @Test
    fun 다른_키의_값은_읽지_않는다() {
        val md = Metadata().also { it.put(Metadata.Key.of("x-chain-status", Metadata.ASCII_STRING_MARSHALLER), "504") }
        assertNull(GrpcOutcomes.chainStatusOf(md))
    }
}
