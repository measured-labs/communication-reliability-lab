package lab.reliability.chain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// docs/idempotency.md 3-1절 끝의 표("응답 상태를 결과로 읽는 규칙 전체")와 6절의 서명 — HttpOutcomes.read.
// 위에서 아래로 처음 맞는 줄: 200~299 Completed · 504 Unknown:HTTP_504 · 409는 inProgressAsUnknown이 false면
// Failed:HTTP_409, true면 Unknown:HTTP_409 · 그 밖의 모든 정수 Failed:HTTP_<10진>. 설정은 409에만 닿고 예외는 없다.
// 이 파일은 구현을 보지 않고 문서만으로 쓴다(결과의 갈래는 RetryFixtures 의 nameOf 로 읽는다).
class HttpOutcomesTest {
    /** 결과의 갈래(nameOf — 타입과 reason)와 label()을 함께 확인한다. 둘은 같은 문자열이어야 한다. */
    private fun assertReads(expected: String, status: Int, inProgressAsUnknown: Boolean) {
        val o = HttpOutcomes.read(status, inProgressAsUnknown)
        assertEquals(expected, nameOf(o), "갈래: status=$status, inProgressAsUnknown=$inProgressAsUnknown")
        assertEquals(expected, o.label(), "label(): status=$status, inProgressAsUnknown=$inProgressAsUnknown")
    }

    private fun assertReadsBoth(expected: String, status: Int) {
        assertReads(expected, status, false)
        assertReads(expected, status, true)
    }

    // ---- 200~299(양끝 포함) → Completed ----

    @Test
    fun 상태_200부터_299까지는_설정과_무관하게_Completed() {
        for (s in listOf(200, 201, 204, 299)) assertReadsBoth("Completed", s)
    }

    @Test
    fun 상태_199와_300은_Completed가_아니라_Failed() {
        assertReadsBoth("Failed:HTTP_199", 199)
        assertReadsBoth("Failed:HTTP_300", 300)
    }

    // ---- 504 → Unknown:HTTP_504 ----

    @Test
    fun 상태_504는_설정과_무관하게_Unknown_HTTP_504() {
        assertReadsBoth("Unknown:HTTP_504", 504)
    }

    @Test
    fun 상태_503과_505는_Failed() {
        assertReadsBoth("Failed:HTTP_503", 503)
        assertReadsBoth("Failed:HTTP_505", 505)
    }

    // ---- 409 ----

    @Test
    fun 상태_409는_설정이_꺼지면_Failed_HTTP_409() {
        assertReads("Failed:HTTP_409", 409, false)
    }

    @Test
    fun 상태_409는_설정이_켜지면_Unknown_HTTP_409() {
        assertReads("Unknown:HTTP_409", 409, true)
    }

    @Test
    fun 상태_408과_410은_설정과_무관하게_Failed() {
        assertReadsBoth("Failed:HTTP_408", 408)
        assertReadsBoth("Failed:HTTP_410", 410)
    }

    // ---- 그 밖의 모든 정수 → Failed:HTTP_<status> ----

    @Test
    fun 그_밖의_1xx_3xx_4xx_5xx는_Failed_HTTP_상태() {
        for (s in listOf(100, 101, 301, 304, 399, 400, 404, 422, 429, 499, 500, 502, 599)) {
            assertReadsBoth("Failed:HTTP_$s", s)
        }
    }

    @Test
    fun 범위를_검사하지_않아_0과_음수와_600_이상도_Failed_HTTP_10진() {
        assertReadsBoth("Failed:HTTP_0", 0)
        assertReadsBoth("Failed:HTTP_-1", -1)
        assertReadsBoth("Failed:HTTP_-409", -409)
        assertReadsBoth("Failed:HTTP_600", 600)
        assertReadsBoth("Failed:HTTP_1000", 1000)
        assertReadsBoth("Failed:HTTP_${Int.MAX_VALUE}", Int.MAX_VALUE)
        assertReadsBoth("Failed:HTTP_${Int.MIN_VALUE}", Int.MIN_VALUE)
    }

    @Test
    fun 상태가_한_자리나_두_자리여도_앞에_0을_붙이지_않는다() {
        assertReadsBoth("Failed:HTTP_7", 7)
        assertReadsBoth("Failed:HTTP_42", 42)
    }

    // ---- 설정은 409에만 닿는다 ----

    @Test
    fun 설정은_409가_아닌_상태의_결과를_바꾸지_않는다() {
        val statuses = listOf(-1, 0, 100, 199, 200, 250, 299, 300, 400, 408, 410, 500, 503, 504, 505, 600)
        for (s in statuses) {
            val off = HttpOutcomes.read(s, false)
            val on = HttpOutcomes.read(s, true)
            assertEquals(nameOf(off), nameOf(on), "갈래: status=$s")
            assertEquals(off.label(), on.label(), "label(): status=$s")
        }
    }

    @Test
    fun 설정은_409의_결과를_바꾼다() {
        assertEquals(
            listOf("Failed:HTTP_409", "Unknown:HTTP_409"),
            listOf(HttpOutcomes.read(409, false).label(), HttpOutcomes.read(409, true).label()),
        )
    }
}
