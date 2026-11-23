package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 7-2 — 시각 분석의 입력 오류(종료 코드 1, report-timing.json·metrics.tsv 를 쓰지 않음).
// 틀린 이유로 통과하지 않게: 오류 테스트마다 기준 기록에서 한 가지만 바꾼다. 기준 기록이 통과(0)하는 것과
// 경계의 안쪽(받아야 하는 값)이 통과하는 것은 기준_… 과 …_받는다 테스트가 지킨다.
class TimingInputErrorTest {
    @TempDir
    lateinit var root: Path

    private val SLOW = "payment:delay=5000"

    /** c 의 주문 시각 기록의 칸(JSON 값 그대로) — 재고가 inventory:error 로 500, 주문은 Failed:HTTP_500 으로 502. */
    private val cOrder = linkedMapOf(
        "requestId" to "\"c\"",
        "startUs" to "${us(T + 201)}",
        "endUs" to "${us(T + 210)}",
        "status" to "502",
        "callStartUs" to "${us(T + 202)}",
        "callEndUs" to "${us(T + 208)}",
        "callOutcome" to "\"Failed:HTTP_500\"",
    )

    /** c 의 주문 기록에서 칸 몇 개를 바꾼 것. 값이 null 이면 그 칸을 뺀다. */
    private fun cOrderWith(vararg change: Pair<String, String?>): String {
        val f = LinkedHashMap<String, String?>(cOrder)
        change.forEach { (k, v) -> f[k] = v }
        return f.filterValues { it != null }.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
    }

    /**
     * 기준 기록: a 정상(T), b 재고→결제 거짓 타임아웃(T+100, 경과 1010 → 부하 구간의 끝 T+1110),
     * c 재고 오류(T+200, 주문 행 FAILED — 재고·결제 행 없음, 재고의 시각 기록은 있고 결제의 시각 기록은 없다).
     * 표본은 서비스마다 T~T+1110, 100ms 간격(마지막은 T+1110).
     */
    private fun good(cOrderRecord: String = cOrderWith()) = TimingRecord(root)
        .okChain("a", T)
        .inventoryTimesOut("b", T + 100)
        .answer("c", T + 200, 502, "inventory:error", 20)
        .rawTiming("order", cOrderRecord)
        .timing("inventory", "c", us(T + 203), us(T + 207), 500)
        .row("order", "c", "FAILED")

    private fun samplesJson(list: List<TestSample>, overflow: String = "false", interval: String = "\"intervalMs\":100,") =
        """{${interval}"overflow":$overflow,"samples":${list.joinToString(",", "[", "]") { it.json() }}}"""

    private val fullGrid get() = grid(T, T + 1110).map { TestSample(it) }

    // ---- 기준 ----

    @Test
    fun 기준_기록은_통과한다() {
        assertEquals(0, good().runTiming())
    }

    @Test
    fun 기준_표본을_손으로_써도_통과한다() {
        val r = good().apply { samplesText["order"] = samplesJson(fullGrid) }
        assertEquals(0, r.runTiming())
    }

    // ---- 정답 ----

    @Test
    fun 정답에_sentAtMs가_없으면_입력_오류() {
        good().rawAnswer("""{"type":"answer","requestId":"d","fault":"","amount":1500,"status":500,"errorCode":0,"elapsedMs":10}""")
            .assertInputError()
    }

    @Test
    fun 정답의_sentAtMs가_소수면_입력_오류() {
        good().rawAnswer("""{"type":"answer","requestId":"d","fault":"","amount":1500,"sentAtMs":${T + 300}.5,"status":500,"errorCode":0,"elapsedMs":10}""")
            .assertInputError()
    }

    @Test
    fun 정답의_sentAtMs가_문자열이면_입력_오류() {
        good().rawAnswer("""{"type":"answer","requestId":"d","fault":"","amount":1500,"sentAtMs":"${T + 300}","status":500,"errorCode":0,"elapsedMs":10}""")
            .assertInputError()
    }

    @Test
    fun sentAtMs가_있는_정답_줄을_더해도_통과한다() {
        assertEquals(0, good().answer("d", T + 300, 500).runTiming())
    }

    @Test
    fun 같은_requestId가_정답에_두_줄이면_입력_오류() {
        good().answer("c", T + 300, 502, "inventory:error").assertInputError()
    }

    @Test
    fun 정답의_elapsedMs가_음수면_입력_오류() {
        good().answer("d", T + 300, 500, elapsedMs = -1).assertInputError()
    }

    // ---- 시각 기록 — 파일 ----

    @Test
    fun 시각_기록_파일이_없으면_입력_오류() {
        good().apply { omit += "timing/timings-payment.json" }.assertInputError()
    }

    @Test
    fun 시각_기록_파일이_비었으면_입력_오류() {
        good().apply { timingsText["payment"] = "" }.assertInputError()
    }

    @Test
    fun 시각_기록_파일이_JSON이_아니면_입력_오류() {
        good().apply { timingsText["payment"] = "not json" }.assertInputError()
    }

    @Test
    fun 시각_기록_파일이_배열이_아니면_입력_오류() {
        good().apply { timingsText["payment"] = "{}" }.assertInputError()
    }

    // ---- 시각 기록 — 원소의 꼴 ----

    @Test
    fun 끝이_시작보다_이르면_입력_오류() {
        // call 칸을 빼 start ≤ callStart 조건과 섞이지 않게 한다
        good(cOrderWith("endUs" to "${us(T + 201) - 1}", "callStartUs" to "null", "callEndUs" to "null", "callOutcome" to "null"))
            .assertInputError()
    }

    @Test
    fun 끝과_시작이_같으면_받는다() {
        val r = good(cOrderWith("endUs" to "${us(T + 201)}", "callStartUs" to "null", "callEndUs" to "null", "callOutcome" to "null"))
        assertEquals(0, r.runTiming())
    }

    @Test
    fun call_칸이_없는_주문_기록도_받는다() {
        val r = good(cOrderWith("callStartUs" to "null", "callEndUs" to "null", "callOutcome" to "null"))
        assertEquals(0, r.runTiming())
    }

    @Test
    fun requestId가_빈_문자열이면_입력_오류() {
        good(cOrderWith("requestId" to "\"\"")).assertInputError()
    }

    @Test
    fun requestId가_없으면_입력_오류() {
        good(cOrderWith("requestId" to null)).assertInputError()
    }

    @Test
    fun startUs가_정수가_아니면_입력_오류() {
        good(cOrderWith("startUs" to "${us(T + 201)}.5")).assertInputError()
    }

    @Test
    fun startUs가_문자열이면_입력_오류() {
        good(cOrderWith("startUs" to "\"${us(T + 201)}\"")).assertInputError()
    }

    @Test
    fun status가_없으면_입력_오류() {
        good(cOrderWith("status" to null)).assertInputError()
    }

    @Test
    fun call_칸_셋_가운데_callEndUs만_없으면_입력_오류() {
        good(cOrderWith("callEndUs" to "null")).assertInputError()
    }

    @Test
    fun call_칸_셋_가운데_callOutcome만_없으면_입력_오류() {
        good(cOrderWith("callOutcome" to "null")).assertInputError()
    }

    @Test
    fun callStart가_start보다_이르면_입력_오류() {
        good(cOrderWith("callStartUs" to "${us(T + 201) - 1}")).assertInputError()
    }

    @Test
    fun callEnd가_end보다_늦으면_입력_오류() {
        good(cOrderWith("callEndUs" to "${us(T + 210) + 1}")).assertInputError()
    }

    @Test
    fun callStart가_callEnd보다_늦으면_입력_오류() {
        good(cOrderWith("callStartUs" to "${us(T + 209)}")).assertInputError()
    }

    @Test
    fun start_callStart_callEnd_end가_모두_같은_경계면_받는다() {
        val r = good(cOrderWith("callStartUs" to "${us(T + 201)}", "callEndUs" to "${us(T + 210)}"))
        assertEquals(0, r.runTiming())
    }

    @Test
    fun callOutcome이_정한_꼴이_아니면_입력_오류() {
        good(cOrderWith("callOutcome" to "\"Timeout\"")).assertInputError()
    }

    @Test
    fun callOutcome의_대소문자가_다르면_입력_오류() {
        good(cOrderWith("callOutcome" to "\"completed\"")).assertInputError()
    }

    @Test
    fun 결제_기록에_call_칸이_있으면_입력_오류() {
        good().timing("payment", "c", us(T + 204), us(T + 206), 500, us(T + 204), us(T + 205), "Completed").assertInputError()
    }

    @Test
    fun DB_행이_없는_결제_기록은_받는다() {
        assertEquals(0, good().timing("payment", "c", us(T + 204), us(T + 206), 500).runTiming())
    }

    // ---- 시각 기록이 빠짐 ----

    @Test
    fun 주문_행은_있는데_주문의_시각_기록이_없으면_입력_오류() {
        good().answer("d", T + 300, 500).row("order", "d", "FAILED").assertInputError()
    }

    @Test
    fun 재고_행은_있는데_재고의_시각_기록이_없으면_입력_오류() {
        // c 에는 재고의 시각 기록이 있으므로 새 요청 d 에 재고 행만 둔다
        good().answer("d", T + 300, 500).row("inventory", "d", "FAILED").assertInputError()
    }

    @Test
    fun 결제_행은_있는데_결제의_시각_기록이_없으면_입력_오류() {
        good().row("payment", "c", "APPROVED").assertInputError()
    }

    @Test
    fun 한_층에_한_requestId의_시각_기록이_둘이면_입력_오류() {
        good().rawTiming("order", cOrderWith()).assertInputError()
    }

    @Test
    fun 정답에_없는_requestId의_시각_기록은_입력_오류() {
        good().timing("inventory", "zzz", us(T + 300), us(T + 301), 201).assertInputError()
    }

    // ---- 표본 ----

    @Test
    fun 표본_파일이_없으면_입력_오류() {
        good().apply { omit += "timing/samples-inventory.json" }.assertInputError()
    }

    @Test
    fun 표본이_넘쳤으면_입력_오류() {
        good().apply { samplesText["order"] = samplesJson(fullGrid, overflow = "true") }.assertInputError()
    }

    @Test
    fun 표본이_비었으면_입력_오류() {
        good().apply { samplesText["order"] = samplesJson(emptyList()) }.assertInputError()
    }

    @Test
    fun intervalMs가_없으면_입력_오류() {
        good().apply { samplesText["order"] = samplesJson(fullGrid, interval = "") }.assertInputError()
    }

    @Test
    fun 표본의_atMs가_앞보다_이르면_입력_오류() {
        val list = fullGrid.toMutableList().also { val x = it[3]; it[3] = it[4]; it[4] = x }
        good().apply { samples["order"] = list }.assertInputError()
    }

    @Test
    fun 표본의_atMs가_앞과_같으면_받는다() {
        // 문서는 "앞보다 이름"만 오류로 적었다 — 같은 시각은 이른 것이 아니다
        val list = fullGrid.toMutableList().also { it.add(4, it[3]) }
        assertEquals(0, good().apply { samples["order"] = list }.runTiming())
    }

    @Test
    fun 표본의_inflight가_음수면_입력_오류() {
        val list = fullGrid.toMutableList().also { it[2] = it[2].copy(inflight = -1) }
        good().apply { samples["order"] = list }.assertInputError()
    }

    @Test
    fun 표본의_inflight가_null이면_입력_오류() {
        val text = samplesJson(fullGrid).replaceFirst("\"inflight\":0", "\"inflight\":null")
        good().apply { samplesText["order"] = text }.assertInputError()
    }

    @Test
    fun 첫_표본이_첫_송신_더하기_간격의_두_배면_받는다() {
        assertEquals(0, good().apply { samples["order"] = grid(T + 200, T + 1110).map { TestSample(it) } }.runTiming())
    }

    @Test
    fun 첫_표본이_첫_송신_더하기_간격의_두_배보다_늦으면_입력_오류() {
        good().apply { samples["order"] = grid(T + 201, T + 1110).map { TestSample(it) } }.assertInputError()
    }

    @Test
    fun 마지막_표본이_정답의_끝보다_이르면_입력_오류() {
        // 부하 구간의 끝 = b 의 T+100 + 1010 = T+1110
        good().apply { samples["order"] = grid(T, T + 1109).map { TestSample(it) } }.assertInputError()
    }

    // ---- 설정 ----

    @Test
    fun 주문의_설정_파일이_없으면_입력_오류() {
        good().apply { omit += "settings-order.json" }.assertInputError()
    }

    @Test
    fun 재고의_설정_파일이_없으면_입력_오류() {
        good().apply { omit += "settings-inventory.json" }.assertInputError()
    }

    @Test
    fun 설정에_requestTimeoutMs가_없으면_입력_오류() {
        good().apply { settingsText["inventory"] = """{"service":"inventory","nextLayer":{}}""" }.assertInputError()
    }

    @Test
    fun 설정의_requestTimeoutMs가_정수가_아니면_입력_오류() {
        good().apply { settingsText["inventory"] = """{"service":"inventory","nextLayer":{"requestTimeoutMs":1000.5}}""" }
            .assertInputError()
    }

    // ---- 느린 구간 ----

    /** 기준 + 느린 요청 s(T+150) + 조회 둘. 느린 구간 [T+150, T+150]. */
    private fun goodSlow() = good().okChain("s", T + 150, SLOW).lookup(T + 50).lookup(T + 400)

    @Test
    fun 기준_느린_구간_기록은_통과한다() {
        assertEquals(0, goodSlow().runTiming("--slow-label", SLOW))
    }

    @Test
    fun 느린_라벨로_보낸_정답이_없으면_입력_오류() {
        good().lookup(T + 50).assertInputError("--slow-label", SLOW)
    }

    @Test
    fun 느린_구간을_주면_조회_파일이_없으면_입력_오류() {
        goodSlow().apply { omit += "lookups.jsonl" }.assertInputError("--slow-label", SLOW)
    }

    @Test
    fun 조회_파일이_0줄이면_입력_오류() {
        goodSlow().apply { lookupsText = "" }.assertInputError("--slow-label", SLOW)
    }

    @Test
    fun 조회_줄에_sentAtMs가_없으면_입력_오류() {
        goodSlow().apply { lookupsText = """{"type":"lookup","status":200,"errorCode":0,"elapsedMs":5}""" + "\n" }
            .assertInputError("--slow-label", SLOW)
    }

    @Test
    fun 느린_구간_앞의_주문_표본이_있으면_받는다() {
        // 첫 표본 T+149 < 시작 T+150 (그리고 첫 송신 T + 200 안)
        val r = goodSlow().apply { samples["order"] = grid(T + 149, T + 1110).map { TestSample(it) } }
        assertEquals(0, r.runTiming("--slow-label", SLOW))
    }

    @Test
    fun 느린_구간_앞의_주문_표본이_없으면_입력_오류() {
        // 첫 표본 T+150 = 시작 — "앞"이 아니다(덮음 조건 첫 표본 ≤ T+200 은 지킨다)
        goodSlow().apply { samples["order"] = grid(T + 150, T + 1110).map { TestSample(it) } }.assertInputError("--slow-label", SLOW)
    }
}
