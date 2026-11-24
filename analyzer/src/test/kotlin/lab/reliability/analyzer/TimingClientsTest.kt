package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 7-3 — clients.*: 응답을 받지 못한 요청(status 0)과 그 가운데 서버의 업무 입구(주문의 시각 기록)에
// 닿지 못한 요청, 그 요청들의 sentAtMs − (정답의 첫 sentAtMs)의 최소·최대.
class TimingClientsTest {
    @TempDir
    lateinit var root: Path

    /**
     * a 정상(T). b 응답 없음(T+100)이지만 주문의 시각 기록이 있다(서버에 닿음).
     * c(T+300)·d(T+500) 응답 없음, 시각 기록·행 없음(서버에 닿지 못함). e(T+600) 504, 시각 기록 없음 — 응답은 받았다.
     * → noResponse 3, notReachedServer 2, first 300, last 500.
     */
    private fun mixed() = TimingRecord(root)
        .okChain("a", T)
        .okChain("b", T + 100, status = 0)
        .answer("c", T + 300, 0, elapsedMs = 30)
        .answer("d", T + 500, 0, elapsedMs = 30)
        .answer("e", T + 600, 504, elapsedMs = 30)

    @Test
    fun 응답을_받지_못한_요청은_status_0인_정답의_수다() {
        assertEquals(3, mixed().analyzeAny().long("clients", "noResponse"))
    }

    @Test
    fun 서버에_닿지_못한_요청은_응답이_없고_주문의_시각_기록이_없는_것이다() {
        // e 는 시각 기록이 없지만 응답(504)을 받았으므로 들지 않는다, b 는 응답이 없지만 주문에 닿았으므로 들지 않는다
        assertEquals(2, mixed().analyzeAny().long("clients", "notReachedServer"))
    }

    @Test
    fun 서버에_닿지_못한_요청의_첫_송신은_정답의_첫_sentAtMs로부터의_ms다() {
        assertEquals(300, mixed().analyzeAny().long("clients", "notReachedServerFirstSentMs"))
    }

    @Test
    fun 서버에_닿지_못한_요청의_마지막_송신은_정답의_첫_sentAtMs로부터의_ms다() {
        assertEquals(500, mixed().analyzeAny().long("clients", "notReachedServerLastSentMs"))
    }

    @Test
    fun 서버에_닿지_못한_요청이_없으면_첫_송신과_마지막_송신은_null이다() {
        // b 는 응답이 없지만 주문에 닿았다 — 닿지 못한 요청 0건
        val r = TimingRecord(root).okChain("a", T).okChain("b", T + 100, status = 0).analyzeAny()
        assertEquals(listOf(null, null), listOf("notReachedServerFirstSentMs", "notReachedServerLastSentMs").map { r.at("clients", it) })
    }

    @Test
    fun 서버에_닿지_못한_요청이_맨_처음_보낸_것이면_첫_송신은_0이다() {
        // 첫 정답 c(T)가 닿지 못했다 — 기준도 c 의 sentAtMs
        val r = TimingRecord(root).answer("c", T, 0, elapsedMs = 30).okChain("a", T + 100).analyzeAny()
        assertEquals(0, r.long("clients", "notReachedServerFirstSentMs"))
    }

    @Test
    fun 서버에_닿지_못한_요청이_하나면_첫_송신과_마지막_송신이_같다() {
        val r = TimingRecord(root).okChain("a", T).answer("c", T + 300, 0, elapsedMs = 30).analyzeAny()
        assertEquals(listOf(300L, 300L), listOf("notReachedServerFirstSentMs", "notReachedServerLastSentMs").map { r.long("clients", it) })
    }
}
