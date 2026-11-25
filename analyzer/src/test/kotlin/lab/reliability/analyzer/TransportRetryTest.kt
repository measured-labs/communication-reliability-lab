package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 11-3 ④ — 재시도가 켜진 층(settings-<서비스>.json 의 retry.enabled 가 true)은 나간 수(T)를
// timing/attempts-<서비스>.json 의 원소 수(시도 기록)와 견준다. 꺼진 층은 시각 기록의 call 수. retry 가 객체가 아니거나
// enabled 가 없으면 꺼진 층. enabled 가 불이 아니면 입력 오류. 켜진 층의 시도 기록 파일이 없음·배열 아님·원소가 객체 아님 → 입력 오류.
// 보고 records.<서비스>.attempts(꺼진 층과 결제는 null). 입구 attemptRecordCount(path), TransportSetting.retryEnabled.
// 틀린 이유로 통과하지 않게: 기준(재고 재시도 켬, 시도 기록 3 = 나간 수 3)이 종료 0인 것을 먼저 지키고, 칸 하나씩 바꾼다.
// 이 파일은 분석기의 구현을 보지 않고 문서만으로 쓴다.
class TransportRetryTest {
    @TempDir
    lateinit var root: Path

    /** 시도 기록 하나(8-2의 꼴). */
    private fun attempt(id: String, callId: Int, n: Int, startMs: Long, outcome: String, decision: String) =
        """{"requestId":"$id","callId":$callId,"attempt":$n,"startUs":${us(startMs)},"endUs":${us(startMs + 3)},"outcome":"$outcome","delayBeforeMs":${if (n == 1) 0 else 1},"decision":"$decision"}"""

    /** a: 첫 시도 실패(재시도) + 둘째 완료, b: 한 번에 완료 → 시도 기록 3 */
    private val threeAttempts = listOf(
        attempt("a", 1, 1, T + 4, "Unknown:CANCELLED", "retry"),
        attempt("a", 1, 2, T + 8, "Completed", "success"),
        attempt("b", 2, 1, T + 104, "Completed", "success"),
    )

    /** gRPC G2, 재고 재시도 켬, 시도 기록 [attempts], 재고가 내보낸 gRPC 수 = call 수 2 + [extraOutbound]. */
    private fun withRetry(attempts: List<String> = threeAttempts, extraOutbound: Long = 1) =
        TransportRecord(root, "grpc").okChain("a", T).okChain("b", T + 100).also {
            it.retryJson["inventory"] = """{"enabled":true}"""
            it.attemptsText["inventory"] = attempts.joinToString(",", "[", "]")
            it.countDelta["inventory.outbound.grpc"] = extraOutbound
        }

    // ---- 기준 ----

    @Test
    fun 재시도가_켜진_층의_나간_수가_시도_기록_수와_같으면_종료_0() {
        assertEquals(emptyList(), withRetry().expectCode(0))
    }

    @Test
    fun 보고의_attempts는_켜진_층의_시도_기록_수이고_꺼진_층과_결제는_null() {
        val rec = withRetry()
        assertEquals(0, rec.runTransport(), rec.lastOutput)
        val r = rec.report()
        assertEquals(listOf<Any?>(3L, null, null), listOf(r.long("records", "inventory", "attempts"), r.at("records", "order", "attempts"), r.at("records", "payment", "attempts")))
    }

    @Test
    fun retry_칸이_없는_기준도_종료_0이고_attempts는_null() {
        val rec = TransportRecord(root, "grpc").okChain("a", T)
        assertEquals(0, rec.runTransport(), rec.lastOutput)
        assertEquals(null, rec.report().at("records", "inventory", "attempts"))
    }

    // ---- 불일치 ----

    @Test
    fun 시도_기록이_나간_수보다_하나_모자라면_불일치다() {
        val r = withRetry(threeAttempts.take(2))
        assertEquals(listOf("counts.inventory.outbound.grpc: 시도 기록 수 2와 같아야 한다(재시도 켬), 실제 3"), r.expectCode(2))
    }

    @Test
    fun 시도_기록이_나간_수보다_많아도_불일치다() {
        val r = withRetry(extraOutbound = 0) // 나간 수 2, 시도 기록 3
        assertEquals(listOf("counts.inventory.outbound.grpc: 시도 기록 수 3와 같아야 한다(재시도 켬), 실제 2"), r.expectCode(2))
    }

    @Test
    fun 재시도가_켜졌으면_call_수와_같아도_시도_기록_수와_견준다() {
        // 나간 수 2 = call 수 2 이지만 시도 기록은 3 — 켜진 층은 call 수로 통과하지 않는다
        val r = withRetry(extraOutbound = 0)
        assertEquals(2, r.runTransport(), r.lastOutput)
    }

    @Test
    fun 재시도가_꺼졌으면_나간_수를_call_수와_견준다() {
        val r = withRetry().also { it.retryJson["inventory"] = """{"enabled":false}""" }
        assertEquals(listOf("counts.inventory.outbound.grpc: 기록의 call 수 2와 같아야 한다, 실제 3"), r.expectCode(2))
    }

    @Test
    fun retry가_객체가_아니면_꺼진_층으로_보고_시도가_더_나간_것은_드러난다() {
        val r = withRetry().also { it.retryJson["inventory"] = "\"없음(맨 아래 층)\"" }
        assertEquals(listOf("counts.inventory.outbound.grpc: 기록의 call 수 2와 같아야 한다, 실제 3"), r.expectCode(2))
    }

    @Test
    fun retry에_enabled가_없으면_꺼진_층으로_본다() {
        val r = withRetry().also { it.retryJson["inventory"] = "{}" }
        assertEquals(listOf("counts.inventory.outbound.grpc: 기록의 call 수 2와 같아야 한다, 실제 3"), r.expectCode(2))
    }

    @Test
    fun 주문의_재시도도_시도_기록과_견준다() {
        val r = TransportRecord(root, "grpc").okChain("a", T).okChain("b", T + 100).also {
            it.retryJson["order"] = """{"enabled":true}"""
            it.attemptsText["order"] = threeAttempts.joinToString(",", "[", "]")
        } // 주문이 내보낸 수 2(call 수), 시도 기록 3
        assertEquals(listOf("counts.order.outbound.grpc: 시도 기록 수 3와 같아야 한다(재시도 켬), 실제 2"), r.expectCode(2))
    }

    // ---- 입력 오류 ----

    @Test
    fun retry_enabled가_문자열이면_입력_오류() =
        withRetry().also { it.retryJson["inventory"] = """{"enabled":"true"}""" }.assertInputError()

    @Test
    fun retry_enabled가_null이면_입력_오류() =
        // "retry.enabled 가 있는데 불이 아니면 입력 오류" — null 은 불이 아니다(키는 있다)
        withRetry().also { it.retryJson["inventory"] = """{"enabled":null}""" }.assertInputError()

    @Test
    fun 켜진_층의_시도_기록_파일이_없으면_입력_오류() = withRetry().also { it.attemptsText.remove("inventory") }.assertInputError()

    @Test
    fun 켜진_층의_시도_기록이_배열이_아니면_입력_오류() = withRetry().also { it.attemptsText["inventory"] = "{}" }.assertInputError()

    @Test
    fun 켜진_층의_시도_기록의_원소가_객체가_아니면_입력_오류() =
        withRetry().also { it.attemptsText["inventory"] = "[1,2,3]" }.assertInputError()

    @Test
    fun 꺼진_층의_시도_기록은_없어도_된다() {
        // 재고 재시도 꺼짐, 시도 기록 파일 없음, 나간 수 = call 수
        val r = withRetry(extraOutbound = 0).also { it.retryJson["inventory"] = """{"enabled":false}"""; it.attemptsText.remove("inventory") }
        assertEquals(0, r.runTransport(), r.lastOutput)
    }

    // ---- 입구 ----

    @Test
    fun attemptRecordCount는_시도_기록의_원소_수다() {
        val rec = withRetry()
        rec.write()
        assertEquals(3L, attemptRecordCount(rec.dir.resolve("timing/attempts-inventory.json")).toLong())
    }

    @Test
    fun TransportSetting의_retryEnabled는_설정의_retry_enabled다() {
        val rec = withRetry().also { it.retryJson["order"] = """{"enabled":false}""" }
        rec.write()
        assertEquals(
            listOf<Any?>(true, false),
            listOf(
                readTransportSetting(rec.dir.resolve("settings-inventory.json")).retryEnabled,
                readTransportSetting(rec.dir.resolve("settings-order.json")).retryEnabled,
            ),
        )
    }
}
