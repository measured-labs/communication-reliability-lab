package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 11-3 — 전송 대조의 입력 오류(종료 코드 1, report-transport.json·metrics-transport.tsv 를 쓰지 않고
// 있던 것은 지운다). 틀린 이유로 통과하지 않게: 기준 기록이 종료 0인 것을 맨 앞의 테스트가 지키고, 오류 테스트마다
// 기준에서 칸 하나만 바꾼다. 받아야 하는 값(경계의 안쪽)은 …_받는다 테스트가 지킨다.
// 이 파일은 분석기의 구현을 보지 않고 문서만으로 쓴다.
class TransportInputErrorTest {
    @TempDir
    lateinit var root: Path

    private fun good() = TransportRecord(root, "grpc").okChain("a", T).okChain("b", T + 100)

    @Test
    fun 기준_기록은_종료_0() {
        val r = good()
        assertEquals(0, r.runTransport(), r.lastOutput)
    }

    @Test
    fun HTTP_기준_기록은_종료_0() {
        val r = TransportRecord(root, "http").okChain("a", T)
        assertEquals(0, r.runTransport(), r.lastOutput)
    }

    // ---- 파일 없음 ----

    @Test
    fun 주문의_설정_파일이_없으면_입력_오류() = good().also { it.omit += "settings-order.json" }.assertInputError()

    @Test
    fun 재고의_설정_파일이_없으면_입력_오류() = good().also { it.omit += "settings-inventory.json" }.assertInputError()

    @Test
    fun 결제의_설정_파일이_없으면_입력_오류() = good().also { it.omit += "settings-payment.json" }.assertInputError()

    @Test
    fun transport_counts_json이_없으면_입력_오류() = good().also { it.omit += "transport-counts.json" }.assertInputError()

    @Test
    fun 결제의_시각_기록이_없으면_입력_오류() = good().also { it.omit += "timing/timings-payment.json" }.assertInputError()

    // ---- 설정의 꼴 ----

    private fun settings(rec: TransportRecord, svc: String, transport: String, grpc: String) = rec.also {
        it.settingsText[svc] = """{"service":"$svc","nextLayer":{"requestTimeoutMs":1000},"transport":$transport,"grpc":$grpc}"""
    }

    private val okGrpc = """{"deadlinePropagation":true,"observeCancellation":false}"""

    @Test
    fun 설정을_손으로_적은_기준은_받는다() {
        val r = settings(good(), "inventory", """{"chosen":"grpc"}""", okGrpc)
        assertEquals(0, r.runTransport(), r.lastOutput)
    }

    @Test
    fun 설정에_transport가_없으면_입력_오류() = good().also {
        it.settingsText["inventory"] = """{"service":"inventory","nextLayer":{"requestTimeoutMs":1000},"grpc":$okGrpc}"""
    }.assertInputError()

    @Test
    fun 설정의_transport가_객체가_아니면_입력_오류() = settings(good(), "inventory", "\"grpc\"", okGrpc).assertInputError()

    @Test
    fun 설정의_grpc가_객체가_아니면_입력_오류() = settings(good(), "inventory", """{"chosen":"grpc"}""", "true").assertInputError()

    @Test
    fun 모르는_전송_이름이면_입력_오류() = settings(good(), "inventory", """{"chosen":"tcp"}""", okGrpc).assertInputError()

    @Test
    fun 고른_전송이_문자열이_아니면_입력_오류() = settings(good(), "payment", """{"chosen":1}""", okGrpc).assertInputError()

    @Test
    fun 기한_전파가_불이_아니면_입력_오류() =
        settings(good(), "inventory", """{"chosen":"grpc"}""", """{"deadlinePropagation":"true","observeCancellation":false}""").assertInputError()

    @Test
    fun 취소_관찰이_불이_아니면_입력_오류() =
        settings(good(), "payment", """{"chosen":"grpc"}""", """{"deadlinePropagation":true,"observeCancellation":null}""").assertInputError()

    @Test
    fun 옛_HTTP_실행처럼_설정에_새_칸이_없으면_입력_오류() {
        // task 04-b 까지의 /settings — transport·grpc 객체가 없다(11-1: 이 분석은 둘을 읽는다)
        val r = TransportRecord(root, "http").okChain("a", T)
        for (svc in LAYERS) r.settingsText[svc] = """{"service":"$svc","nextLayer":{"requestTimeoutMs":1000}}"""
        r.assertInputError()
    }

    // ---- transport-counts.json 의 꼴 ----

    private fun counts(inv: String) =
        """{"order":{"inbound":{"http":2,"grpc":0},"outbound":{"http":0,"grpc":2},"grpcCancelledBeforeHandler":0},""" +
            """"inventory":$inv,""" +
            """"payment":{"inbound":{"http":0,"grpc":2},"outbound":{"http":0,"grpc":0},"grpcCancelledBeforeHandler":0}}"""

    private val okInventory = """{"inbound":{"http":0,"grpc":2},"outbound":{"http":0,"grpc":2},"grpcCancelledBeforeHandler":0}"""

    private fun withCounts(text: String) = good().also { it.countsText = text }

    @Test
    fun 센_수를_손으로_적은_기준은_받는다() {
        val r = withCounts(counts(okInventory))
        assertEquals(0, r.runTransport(), r.lastOutput)
    }

    @Test
    fun 서비스가_빠지면_입력_오류() = withCounts(
        """{"order":{"inbound":{"http":2,"grpc":0},"outbound":{"http":0,"grpc":2},"grpcCancelledBeforeHandler":0},""" +
            """"payment":{"inbound":{"http":0,"grpc":2},"outbound":{"http":0,"grpc":0},"grpcCancelledBeforeHandler":0}}""",
    ).assertInputError()

    @Test
    fun 방향이_빠지면_입력_오류() =
        withCounts(counts("""{"inbound":{"http":0,"grpc":2},"grpcCancelledBeforeHandler":0}""")).assertInputError()

    @Test
    fun 전송이_빠지면_입력_오류() =
        withCounts(counts("""{"inbound":{"grpc":2},"outbound":{"http":0,"grpc":2},"grpcCancelledBeforeHandler":0}""")).assertInputError()

    @Test
    fun 센_수가_음수면_입력_오류() =
        withCounts(counts("""{"inbound":{"http":-1,"grpc":2},"outbound":{"http":0,"grpc":2},"grpcCancelledBeforeHandler":0}""")).assertInputError()

    // 11-3 "코드가 하는 대로 정한 것": grpcCancelledBeforeHandler 가 없거나 정수가 아니거나 음수면 입력 오류(차이 줄이 아니다)

    @Test
    fun 처리_코드_전에_취소된_수가_없으면_입력_오류() =
        withCounts(counts("""{"inbound":{"http":0,"grpc":2},"outbound":{"http":0,"grpc":2}}""")).assertInputError()

    @Test
    fun 처리_코드_전에_취소된_수가_음수면_입력_오류() =
        withCounts(counts("""{"inbound":{"http":0,"grpc":2},"outbound":{"http":0,"grpc":2},"grpcCancelledBeforeHandler":-1}""")).assertInputError()

    @Test
    fun 처리_코드_전에_취소된_수가_소수면_입력_오류() =
        withCounts(counts("""{"inbound":{"http":0,"grpc":2},"outbound":{"http":0,"grpc":2},"grpcCancelledBeforeHandler":0.5}""")).assertInputError()

    @Test
    fun 처리_코드_전에_취소된_수가_문자열이면_입력_오류() =
        withCounts(counts("""{"inbound":{"http":0,"grpc":2},"outbound":{"http":0,"grpc":2},"grpcCancelledBeforeHandler":"0"}""")).assertInputError()

    @Test
    fun 센_수가_소수면_입력_오류() =
        withCounts(counts("""{"inbound":{"http":0,"grpc":2.5},"outbound":{"http":0,"grpc":2},"grpcCancelledBeforeHandler":0}""")).assertInputError()

    @Test
    fun 센_수가_문자열이면_입력_오류() =
        withCounts(counts("""{"inbound":{"http":0,"grpc":"2"},"outbound":{"http":0,"grpc":2},"grpcCancelledBeforeHandler":0}""")).assertInputError()

    @Test
    fun transport_counts_json이_JSON이_아니면_입력_오류() = withCounts("not json").assertInputError()

    // ---- 시각 기록의 꼴 ----

    @Test
    fun 시각_기록에_transport가_없으면_입력_오류() = good().change("inventory", "a") { it.copy(transport = null) }.assertInputError()

    @Test
    fun 옛_HTTP_실행의_시각_기록은_입력_오류() {
        // 새 칸 넷이 없는 기록(task 04-b 까지) — 이 분석에서 transport 는 필수(11-1)
        val r = TransportRecord(root, "http").okChain("a", T)
        for (svc in LAYERS) r.change(svc, "a") { it.copy(transport = null) }
        r.assertInputError()
    }

    @Test
    fun 시각_기록의_transport가_둘_밖이면_입력_오류() = good().change("payment", "a") { it.copy(transport = "tcp") }.assertInputError()

    @Test
    fun 멈춘_자리가_세_값_밖이면_입력_오류() =
        TransportRecord(root, "grpc", observe = true).okChain("a", T)
            .change("payment", "a") { it.copy(status = 0, stopped = "cancelled") }.assertInputError()

    @Test
    fun 멈춘_자리_세_값은_받는다() {
        val r = TransportRecord(root, "grpc", observe = true).okChain("a", T).okChain("b", T + 100).okChain("c", T + 200)
        r.change("payment", "a") { it.copy(status = 0, stopped = "deadline-before-start") }
        r.change("payment", "b") { it.copy(status = 0, stopped = "cancelled-in-delay") }
        r.change("payment", "c") { it.copy(status = 0, stopped = "deadline-before-work") }
        assertEquals(0, r.runTransport(), r.lastOutput)
    }

    @Test
    fun 기한이_정수가_아니면_입력_오류() = good().also {
        it.timingsText["payment"] = it.records.filter { r -> r.layer == "payment" }.joinToString(",", "[", "]") { r ->
            if (r.id == "a") r.json().replace("\"deadlineUs\":${r.deadlineUs}", "\"deadlineUs\":1.5") else r.json()
        }
    }.assertInputError()

    @Test
    fun 기한이_문자열이면_입력_오류() = good().also {
        it.timingsText["inventory"] = it.records.filter { r -> r.layer == "inventory" }.joinToString(",", "[", "]") { r ->
            if (r.id == "b") r.json().replace("\"deadlineUs\":${r.deadlineUs}", "\"deadlineUs\":\"${r.deadlineUs}\"") else r.json()
        }
    }.assertInputError()

    @Test
    fun 취소_시각이_음수면_입력_오류() = good().change("payment", "a") { it.copy(cancelledUs = -1) }.assertInputError()

    @Test
    fun 취소_시각_0은_받는다() {
        // 음수만 오류다 — 0은 경계의 안쪽(뜻은 없지만 꼴은 맞다)
        val r = good().change("payment", "a") { it.copy(cancelledUs = 0) }
        val code = r.runTransport()
        assertEquals(true, code == 0 || code == 2, "입력 오류가 아니어야 한다(종료 $code): ${r.lastOutput}")
    }

    @Test
    fun 시각_기록의_차례가_틀리면_입력_오류() =
        // 7-2: endUs < startUs
        good().change("payment", "a") { it.copy(endUs = it.startUs - 1) }.assertInputError()

    // ---- 인자 ----

    @Test
    fun run_없이_부르면_입력_오류() {
        val (code, out) = analyzerCli("transport")
        assertEquals(1, code, out)
    }

    @Test
    fun 모르는_인자는_입력_오류() = good().assertInputError("--slow-label", "x")

    @Test
    fun run을_두_번_주면_입력_오류() {
        val r = good()
        r.assertInputError("--run", r.dir.toString())
    }

    @Test
    fun 기대_파일이_없으면_입력_오류() = good().assertInputError("--expect", root.resolve("nope.json").toString())

    @Test
    fun 기대_파일의_값이_범위_꼴이_아니면_입력_오류() {
        val e = writeExpect(root, """{"records.payment.count":{"min":3,"max":1}}""", "expect.transport.json")
        good().assertInputError("--expect", e.toString())
    }
}
