package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// docs/measurement.md 9-4 — 지표의 식. 값은 기준 기록(goodIdemRecord — IdemRecord.kt 머리의 설명)에서 손으로 셈한 것이다.
// 분위수는 7-3절(nearest-rank, 천분율 (q·n + 999) / 1000 번째).
class IdemMetricsTest {
    @TempDir
    lateinit var root: Path

    private fun good() = goodIdemRecord(root)

    @Test
    fun 기준_기록은_불일치가_없다() {
        val r = good()
        assertEquals(0, r.runIdem(), r.lastOutput)
        assertEquals(emptyList(), r.report().differences())
    }

    // ---- enabled ----

    @Test
    fun enabled는_설정의_불을_1과_0으로_낸다() {
        val rep = good().analyze()
        assertEquals(listOf(1L, 1L, 1L), LAYERS.map { rep.long("enabled", it) })
        val off = offIdemRecord(root.resolve("off")).analyze()
        assertEquals(listOf(0L, 0L, 0L), LAYERS.map { off.long("enabled", it) })
    }

    // ---- clients ----

    @Test
    fun 정답_줄_수와_requestId_수() {
        val rep = good().analyze()
        assertEquals(9L, rep.long("clients", "answers"))
        assertEquals(6L, rep.long("clients", "requestIds"))
    }

    @Test
    fun 판정마다의_정답_줄_수와_응답_없음() {
        // 완료: a, b×2, d copy1, e copy2 = 5 · 실패: d 422, e 409 = 2 · 결과 모름: c 504, f 0 = 2 · status 0: f
        val rep = good().analyze()
        assertEquals(5L, rep.long("clients", "completed"))
        assertEquals(2L, rep.long("clients", "failed"))
        assertEquals(2L, rep.long("clients", "unknown"))
        assertEquals(1L, rep.long("clients", "noResponse"))
    }

    @Test
    fun replayed는_정답_줄만_센다() {
        // b copy2 만(f 의 다시 보낸 줄은 replayed 지만 정답 줄이 아니다)
        assertEquals(1L, good().analyze().long("clients", "replayed"))
    }

    @Test
    fun 상태마다의_정답_줄_수() {
        val rep = good().analyze()
        assertEquals(5L, rep.long("clients", "status", "201"))
        assertEquals(1L, rep.long("clients", "status", "422"))
        assertEquals(1L, rep.long("clients", "status", "409"))
        assertEquals(1L, rep.long("clients", "status", "504"))
        assertEquals(1L, rep.long("clients", "status", "0"))
    }

    @Test
    fun 정답_줄의_경과_분위수() {
        // 3,4,5,10,20,30,40,1000,60000 (n=9): p50 = 5번째 20, p90·p99·max = 9번째 60000. 다시 보낸 줄(42, 7)은 넣지 않는다.
        val rep = good().analyze()
        assertEquals(9L, rep.long("clients", "elapsedMs", "count"))
        assertEquals(20.0, rep.double("clients", "elapsedMs", "p50"))
        assertEquals(60000.0, rep.double("clients", "elapsedMs", "p90"))
        assertEquals(60000.0, rep.double("clients", "elapsedMs", "max"))
    }

    @Test
    fun 닿지_못한_요청은_다시_보낸_것까지_든_보낸_수에서_주문의_시각_기록_수를_뺀다() {
        // c·f 는 정답 1 + 다시 보낸 것 1 = 2, 주문의 시각 기록 2 — 0
        assertEquals(0L, good().analyze().long("clients", "notReachedServer"))
    }

    // ---- 층 ----

    @Test
    fun 층마다_든_수는_시각_기록_수다() {
        // 주문: a1 b2 c2 d2 e2 f2 = 11, 재고·결제: a1 b1 c2 d1 e1 f1 = 7
        assertEquals(listOf(11L, 7L, 7L), good().analyze().perLayer("arrivals"))
    }

    @Test
    fun 행_처리_행_두_번_처리됨_넘친_처리_행() {
        val rep = good().analyze()
        assertEquals(listOf(6L, 6L, 6L), rep.perLayer("rows"))
        assertEquals(listOf(6L, 6L, 6L), rep.perLayer("processedRows"))
        assertEquals(listOf(0L, 0L, 0L), rep.perLayer("doubled"))
        assertEquals(listOf(0L, 0L, 0L), rep.perLayer("extraProcessedRows"))
        assertEquals(0L, rep.long("doubledTotal"))
    }

    @Test
    fun 성공으로_받지_못한_요청의_처리_행은_정답만으로_가른다() {
        // c(504)·f(0)는 다시 보낸 것이 201 이어도 정답에 completed 가 없다 — 층마다 처리 행 1씩, 2
        assertEquals(listOf(2L, 2L, 2L), good().analyze().perLayer("processedNotCompleted"))
    }

    @Test
    fun 키_행의_수와_상태마다의_수() {
        val rep = good().analyze()
        assertEquals(listOf(6L, 6L, 6L), rep.perLayer("keyRows"))
        assertEquals(listOf(6L, 6L, 6L), LAYERS.map { rep.long("layers", it, "keyStates", "COMPLETED") })
        assertEquals(listOf(0L, 0L, 0L), rep.perLayer("rowsWithoutKey"))
        assertEquals(listOf(0L, 0L, 0L), rep.perLayer("keysWithoutRow"))
    }

    @Test
    fun 키_상태는_넷을_모두_가른다() {
        val r = good()
        // g: 처리 중에 남은 키(응답 칸 빔), h: 결과 모름, i: 실패 — 주문에만 키 행과 업무 행
        r.answer("g", T + 600, 0, elapsedMs = 9).timing("order", "g", us(T + 601), us(T + 609), 0)
            .row("order", "g", "PENDING").key("order", "g", "IN_PROGRESS", null)
            .answer("h", T + 700, 504).timing("order", "h", us(T + 701), us(T + 709), 504, us(T + 702), us(T + 708), "Unknown:REQUEST_TIMEOUT")
            .row("order", "h", "UNKNOWN").key("order", "h", "UNKNOWN", 504)
            .answer("i", T + 800, 502).timing("order", "i", us(T + 801), us(T + 809), 502, us(T + 802), us(T + 808), "Failed:HTTP_500")
            .row("order", "i", "FAILED").key("order", "i", "FAILED", 502)
            .counts("order", "new", 9)
        val rep = r.analyzeAny()
        assertEquals(9L, rep.long("layers", "order", "keyRows"))
        assertEquals(6L, rep.long("layers", "order", "keyStates", "COMPLETED"))
        assertEquals(1L, rep.long("layers", "order", "keyStates", "IN_PROGRESS"))
        assertEquals(1L, rep.long("layers", "order", "keyStates", "UNKNOWN"))
        assertEquals(1L, rep.long("layers", "order", "keyStates", "FAILED"))
        assertEquals(0L, rep.long("layers", "order", "rowsWithoutKey"))
        assertEquals(0L, rep.long("layers", "order", "keysWithoutRow"))
    }

    @Test
    fun 센_수는_파일의_값_그대로다() {
        val rep = good().analyze()
        val order = IDEM_KINDS.map { rep.long("layers", "order", "counts", it) }
        assertEquals(listOf(6L, 2L, 1L, 1L, 1L, 0L), order)
        val inv = IDEM_KINDS.map { rep.long("layers", "inventory", "counts", it) }
        assertEquals(listOf(6L, 0L, 0L, 0L, 1L, 0L), inv)
        val pay = IDEM_KINDS.map { rep.long("layers", "payment", "counts", it) }
        assertEquals(listOf(6L, 1L, 0L, 0L, 0L, 0L), pay)
    }

    // ---- 자기 몫의 시간 ----

    @Test
    fun 자기_몫은_end_빼기_start_빼기_호출_시간이고_호출이_없으면_end_빼기_start다() {
        val r = IdemRecord(root)
        // 주문 0~30(호출 5~25): 자기 10, 서비스 30 · 재고 6~24(호출 8~20): 자기 6, 서비스 18 · 결제 9~16: 자기 7, 서비스 7
        // 주문에 저장한 응답으로 끝난 둘째(100~103, 호출 없음): 자기 3, 서비스 3
        r.answer("a", T, 201, copy = 1).answer("a", T + 99, 201, copy = 2, replayed = true)
            .timing("order", "a", us(T), us(T + 30), 201, us(T + 5), us(T + 25), "Completed")
            .timing("inventory", "a", us(T + 6), us(T + 24), 201, us(T + 8), us(T + 20), "Completed")
            .timing("payment", "a", us(T + 9), us(T + 16), 201)
            .timing("order", "a", us(T + 100), us(T + 103), 201)
            .processedRows("a").keys("a")
            .counts("order", "new", 1).counts("order", "replayed", 1).counts("inventory", "new", 1).counts("payment", "new", 1)
        val rep = r.analyze()
        // 주문 자기 몫 [3, 10]: p50 = 1번째 3, max 10 / 서비스 [3, 30]
        assertEquals(2L, rep.long("layers", "order", "selfMs", "count"))
        assertEquals(3.0, rep.double("layers", "order", "selfMs", "p50"))
        assertEquals(10.0, rep.double("layers", "order", "selfMs", "max"))
        assertEquals(30.0, rep.double("layers", "order", "serviceMs", "max"))
        assertEquals(6.0, rep.double("layers", "inventory", "selfMs", "p50"))
        assertEquals(18.0, rep.double("layers", "inventory", "serviceMs", "p50"))
        assertEquals(7.0, rep.double("layers", "payment", "selfMs", "p50"))
        assertEquals(7.0, rep.double("layers", "payment", "serviceMs", "p50"))
    }

    @Test
    fun 시간은_마이크로초를_밀리초로_나눈다() {
        val r = IdemRecord(root)
        // 결제 1500μs → 1.5ms
        r.answer("a", T, 201)
            .timing("order", "a", us(T), us(T + 30), 201, us(T + 5), us(T + 25), "Completed")
            .timing("inventory", "a", us(T + 6), us(T + 24), 201, us(T + 8), us(T + 20), "Completed")
            .timing("payment", "a", us(T + 9), us(T + 9) + 1500, 201)
            .processedRows("a").keys("a")
            .counts("order", "new", 1).counts("inventory", "new", 1).counts("payment", "new", 1)
        assertEquals(1.5, r.analyze().double("layers", "payment", "serviceMs", "p50"))
    }

    // ---- 넣은 장애의 다시 센 수 ----

    @Test
    fun 끊은_수는_서비스가_센_수와_기록에서_다시_센_수를_함께_낸다() {
        val rep = good().analyze()
        assertEquals(1L, rep.long("dropFaults", "order", "counted"))
        assertEquals(1L, rep.long("dropFaults", "order", "expected"))
        assertEquals(0L, rep.long("dropFaults", "inventory", "counted"))
        assertEquals(0L, rep.long("dropFaults", "inventory", "expected"))
        assertEquals(1L, rep.long("dropFaults", "payment", "counted"))
        assertEquals(1L, rep.long("dropFaults", "payment", "expected"))
    }

    @Test
    fun 끊은_수의_다시_센_수는_n과_그_서비스의_시각_기록_수_가운데_작은_것이다() {
        // g: 재고에 n=3, 재고에 두 번 듦(처음 + 맞추기) — min(3, 2) = 2
        val r = good()
        val g = T + 600
        r.answer("g", g, 504, dropFault = "inventory:drop-after-commit=3")
            .chainTimings("g", g, orderStatus = 504, orderOutcome = "Unknown:IO_IOException", invStatus = 0)
            .chainTimings("g", R + 200, orderStatus = 504, orderOutcome = "Unknown:IO_IOException", invStatus = 0, payStatus = 201)
            .resend("g", R + 200, 504, dropFault = "inventory:drop-after-commit=3")
            .processedRows("g").keys("g")
            .counts("order", "new", 7).counts("inventory", "new", 7).counts("payment", "new", 7)
            .drops("inventory", 2)
        val rep = r.analyze()
        assertEquals(2L, rep.long("dropFaults", "inventory", "expected"))
        assertEquals(2L, rep.long("dropFaults", "inventory", "counted"))
    }

    @Test
    fun 넣은_실패는_8_4절의_식으로_다시_센다() {
        // g: payment:slow-first=1@1500 — 결제 시각 기록 1, min(1, 1) = 1
        val r = good()
        val g = T + 600
        r.answer("g", g, 201, attemptFault = "payment:slow-first=1@1500").chainTimings("g", g)
            .processedRows("g").keys("g")
            .counts("order", "new", 7).counts("inventory", "new", 7).counts("payment", "new", 7)
            .attemptCounted("payment", "slow-first", 1)
        val rep = r.analyze()
        assertEquals(1L, rep.long("attemptFaults", "payment", "slow-first", "expected"))
        assertEquals(1L, rep.long("attemptFaults", "payment", "slow-first", "counted"))
        assertEquals(0L, rep.long("attemptFaults", "order", "fail-first", "expected"))
    }

    // ---- 중복 요청 ----

    @Test
    fun 중복_요청의_갈래() {
        // b 201+201, d 201+422, e 201+409
        val rep = good().analyze()
        assertEquals(3L, rep.long("duplicates", "requestIds"))
        assertEquals(1L, rep.long("duplicates", "pairs", "201+201"))
        assertEquals(1L, rep.long("duplicates", "pairs", "201+422"))
        assertEquals(1L, rep.long("duplicates", "pairs", "201+409"))
        assertEquals(0L, rep.long("duplicates", "doubled"))
    }

    @Test
    fun 둘째_줄의_상태와_replayed는_copy_2로_가른다() {
        // copy 2: b 201(replayed), d 422, e 201
        val rep = good().analyze()
        assertEquals(2L, rep.long("duplicates", "secondCopyStatus", "201"))
        assertEquals(1L, rep.long("duplicates", "secondCopyStatus", "422"))
        val second = rep.at("duplicates", "secondCopyStatus") as Map<*, *>
        assertEquals(0L, (second["409"] as Number?)?.toLong() ?: 0L, "copy 2 가운데 409 는 없다(e 의 409 는 copy 1): $second")
        assertEquals(1L, rep.long("duplicates", "secondCopyReplayed"))
    }

    @Test
    fun 나중에_든_것은_주문의_시각_기록_startUs로_가린다() {
        // e: copy2 가 주문에 먼저(201), copy1 이 나중(409) — 나중 = 409. b 나중 201, d 나중 422
        val rep = good().analyze()
        assertEquals(1L, rep.long("duplicates", "laterArrivalStatus", "409"))
        assertEquals(1L, rep.long("duplicates", "laterArrivalStatus", "422"))
        assertEquals(1L, rep.long("duplicates", "laterArrivalStatus", "201"))
    }

    @Test
    fun 정답_줄이_둘_이상인_requestId가_없으면_duplicates가_없다() {
        val r = IdemRecord(root)
        r.answer("a", T).chainTimings("a", T).processedRows("a").keys("a")
            .counts("order", "new", 1).counts("inventory", "new", 1).counts("payment", "new", 1)
        assertFalse("duplicates" in r.analyze())
    }

    // ---- 맞추기 ----

    @Test
    fun 맞추기의_수와_끝_상태() {
        val rep = good().analyze()
        assertEquals(2L, rep.long("reconcile", "resent"))
        assertEquals(2L, rep.long("reconcile", "resentRequestIds"))
        assertEquals(2L, rep.long("reconcile", "completed"))
        assertEquals(1L, rep.long("reconcile", "replayed"))
        assertEquals(2L, rep.long("reconcile", "status", "201"))
        assertEquals(2L, rep.long("reconcile", "finalOnce"))
        assertEquals(0L, rep.long("reconcile", "finalDoubled"))
        assertEquals(0L, rep.long("reconcile", "finalMissing"))
        // 다시 보낸 것의 경과 [7, 42]
        assertEquals(2L, rep.long("reconcile", "elapsedMs", "count"))
        assertEquals(7.0, rep.double("reconcile", "elapsedMs", "p50"))
        assertEquals(42.0, rep.double("reconcile", "elapsedMs", "max"))
    }

    @Test
    fun resends_파일이_없으면_reconcile이_없다() {
        val r = good()
        r.resendLines = null
        // c·f 의 맞추기 시각 기록이 남아 있으면 보낸 것보다 주문에 많이 든 것이다(늘 0 검사는 max(0, …)라 걸리지 않는다)
        val rep = r.analyze()
        assertFalse("reconcile" in rep, "resends.jsonl 이 없으면 reconcile 이 보고에 없다")
    }

    @Test
    fun 빈_resends_파일은_다시_보낸_것이_0인_맞추기다() {
        val r = good()
        r.resendLines = null
        r.resendsText = ""
        // 후보 c·f 를 다시 보내지 않았다 — ⑥ 의 불일치
        val rep = r.analyzeAny()
        assertEquals(
            listOf("reconcile: 다시 보낼 후보 2건 가운데 다시 보내지 못한 것 2건, 후보가 아닌데 다시 보낸 것 0건"),
            rep.differences(),
        )
        assertEquals(2L, rep.long("reconcile", "candidates"))
        assertEquals(2L, rep.long("reconcile", "notResent"))
        assertEquals(0L, rep.long("reconcile", "resent"))
        assertEquals(0L, rep.long("reconcile", "finalOnce"))
    }

    // ---- 일부러 틀어 놓은 기록 ----

    @Test
    fun 키가_꺼진_기록의_두_번_처리됨() {
        val rep = offIdemRecord(root).analyze()
        assertEquals(1L, rep.long("doubledTotal"))
        assertEquals(listOf(1L, 1L, 1L), rep.perLayer("doubled"))
        assertEquals(listOf(1L, 1L, 1L), rep.perLayer("extraProcessedRows"))
        assertEquals(listOf(3L, 3L, 3L), rep.perLayer("processedRows"))
        assertEquals(1L, rep.long("duplicates", "doubled"))
        assertEquals(1L, rep.long("duplicates", "pairs", "201+201"))
        assertEquals(listOf(0L, 0L, 0L), rep.perLayer("keyRows"))
    }

    @Test
    fun 키가_켜져_있어도_결제_행이_둘이면_두_번_처리됨으로_센다() {
        // 9-5 ④: 키 행 하나에 업무 행 둘(진 쪽의 되돌림이 듣지 않은 꼴) — rowsWithoutKey 는 못 잡고 keyRows·keyLinks 가 잡는다
        val r = good().row("payment", "a", "APPROVED")
        val rep = r.analyzeAny()
        assertEquals(
            listOf("layers.payment.keyRows: 업무 행 7과 다르다(6)", "layers.payment.keyLinks.rowsNotLinked: 늘 0, 실제 1"),
            rep.differences(),
        )
        assertEquals(0L, rep.long("layers", "payment", "rowsWithoutKey"))
        assertEquals(1L, rep.long("doubledTotal"))
        assertEquals(listOf(0L, 0L, 1L), rep.perLayer("doubled"))
        assertEquals(listOf(0L, 0L, 1L), rep.perLayer("extraProcessedRows"))
        assertEquals(7L, rep.long("layers", "payment", "processedRows"))
    }

    @Test
    fun 넘친_처리_행은_requestId마다_처리_행_빼기_1의_합이다() {
        // a 결제 행 셋(넘침 2), b 결제 행 둘(넘침 1) — 두 번 처리됨 2, 넘친 행 3
        val r = good().row("payment", "a", "APPROVED").row("payment", "a", "APPROVED").row("payment", "b", "APPROVED")
        val rep = r.analyzeAny()
        assertEquals(3L, rep.long("layers", "payment", "keyLinks", "rowsNotLinked"))
        assertEquals(2L, rep.long("layers", "payment", "doubled"))
        assertEquals(3L, rep.long("layers", "payment", "extraProcessedRows"))
    }

    @Test
    fun 맞추기_뒤에_어느_층이든_처리_행이_없으면_finalMissing이다() {
        // g: 결과 모름(504) → 다시 보내 201 이라 받았는데 결제 행·결제 키가 없다(잃음)
        val r = good()
        val g = T + 600
        r.answer("g", g, 504)
            .chainTimings("g", g, orderStatus = 504, orderOutcome = "Unknown:HTTP_504", invStatus = 504, invOutcome = "Unknown:REQUEST_TIMEOUT", payStatus = 201)
            .chainTimings("g", R + 200)
            .resend("g", R + 200, 201)
            .row("order", "g", "COMPLETED").row("inventory", "g", "RESERVED")
            .key("order", "g", "COMPLETED", 201).key("inventory", "g", "COMPLETED", 201)
            .counts("order", "new", 7).counts("inventory", "new", 7)
        val rep = r.analyze()
        assertEquals(3L, rep.long("reconcile", "resent"))
        assertEquals(2L, rep.long("reconcile", "finalOnce"))
        assertEquals(1L, rep.long("reconcile", "finalMissing"))
        assertEquals(0L, rep.long("reconcile", "finalDoubled"))
    }

    @Test
    fun 맞추기_뒤에_어느_층이든_처리_행이_둘이면_finalDoubled다() {
        // c 의 재고 행이 둘(이어 하기가 아니라 새로 처리한 것처럼)
        val rep = good().row("inventory", "c", "RESERVED").analyzeAny()
        assertEquals(1L, rep.long("reconcile", "finalOnce"))
        assertEquals(1L, rep.long("reconcile", "finalDoubled"))
        assertEquals(0L, rep.long("reconcile", "finalMissing"))
        assertEquals(1L, rep.long("doubledTotal"))
    }

    @Test
    fun 업무_행이_있는데_키_행이_없는_것을_센다() {
        // g: 세 층에 업무 행, 재고에만 키 행이 없다
        val r = good()
        val g = T + 600
        r.answer("g", g).chainTimings("g", g).processedRows("g")
            .key("order", "g", "COMPLETED", 201).key("payment", "g", "COMPLETED", 201)
            .counts("order", "new", 7).counts("payment", "new", 7)
        val rep = r.analyzeAny()
        assertEquals(listOf(0L, 1L, 0L), rep.perLayer("rowsWithoutKey"))
        assertEquals(listOf(0L, 0L, 0L), rep.perLayer("keysWithoutRow"))
    }

    @Test
    fun 키_행이_있는데_업무_행이_없는_것을_센다() {
        // g: 세 층에 키 행, 결제에만 업무 행이 없다
        val r = good()
        val g = T + 600
        r.answer("g", g).chainTimings("g", g).row("order", "g", "COMPLETED").row("inventory", "g", "RESERVED").keys("g")
            .counts("order", "new", 7).counts("inventory", "new", 7).counts("payment", "new", 7)
        val rep = r.analyzeAny()
        assertEquals(listOf(0L, 0L, 1L), rep.perLayer("keysWithoutRow"))
        assertEquals(listOf(0L, 0L, 0L), rep.perLayer("rowsWithoutKey"))
    }

    @Test
    fun 키_행이_없는_업무_행은_처리_행이_아니어도_센다() {
        // 9-4: "L의 업무 행 가운데" — 처리 행만이 아니다. g: 주문 FAILED 행, 키 없음
        val r = good()
        r.answer("g", T + 600, 502)
            .timing("order", "g", us(T + 601), us(T + 609), 502, us(T + 602), us(T + 608), "Failed:HTTP_500")
            .row("order", "g", "FAILED")
        assertEquals(1L, r.analyzeAny().long("layers", "order", "rowsWithoutKey"))
    }

    @Test
    fun 키_행이_없는_업무_행은_requestId가_아니라_행으로_센다() {
        // g: 주문 행 둘(FAILED·COMPLETED), 주문 키 없음 — 2
        val r = good()
        r.answer("g", T + 600, 201)
            .timing("order", "g", us(T + 601), us(T + 609), 201, us(T + 602), us(T + 608), "Completed")
            .row("order", "g", "FAILED").row("order", "g", "COMPLETED")
        assertEquals(2L, r.analyzeAny().long("layers", "order", "rowsWithoutKey"))
    }

    @Test
    fun 키와_requestId가_달라도_키_행의_request_id로_업무_행과_잇는다() {
        // g: 키 "key-g" ≠ requestId "g" — 키 행 key=key-g, request_id=g. 업무 행과 짝이 맞으므로 둘 다 0
        val r = good()
        val g = T + 600
        r.answer("g", g, key = "key-g").chainTimings("g", g).processedRows("g")
        for (l in LAYERS) r.key(l, "key-g", "COMPLETED", 201, requestId = "g")
        r.counts("order", "new", 7).counts("inventory", "new", 7).counts("payment", "new", 7)
        val rep = r.analyze()
        assertEquals(listOf(0L, 0L, 0L), rep.perLayer("rowsWithoutKey"))
        assertEquals(listOf(0L, 0L, 0L), rep.perLayer("keysWithoutRow"))
        assertEquals(listOf(7L, 7L, 7L), rep.perLayer("keyRows"))
    }

    @Test
    fun 같은_requestId를_두_번_다시_보내면_resent는_2이고_resentRequestIds는_1이다() {
        val r = good()
        r.timing("order", "f", us(R + 201), us(R + 203), 201)
            .resend("f", R + 200, 201, dropFault = "order:drop-after-commit=1", replayed = true, elapsedMs = 8)
            .counts("order", "replayed", 3)
        val rep = r.analyze()
        assertEquals(3L, rep.long("reconcile", "resent"))
        assertEquals(2L, rep.long("reconcile", "resentRequestIds"))
        assertEquals(2L, rep.long("reconcile", "replayed"))
        assertEquals(2L, rep.long("reconcile", "finalOnce"))
    }

    // ---- metrics-idem.tsv ----

    @Test
    fun metrics_tsv는_이름_탭_값이다() {
        val r = good()
        r.analyze()
        val m = r.metrics()
        assertEquals("5", m["clients.completed"])
        assertEquals("1", m["duplicates.pairs.201+201"])
        assertEquals("1", m["layers.order.counts.in-progress"])
        assertEquals("6", m["layers.payment.keyStates.COMPLETED"])
        assertEquals("2", m["reconcile.finalOnce"])
        assertEquals("1", m["enabled.inventory"])
        assertFalse(m.keys.any { it == "run" || it == "expect" || it.startsWith("differences") })
    }

    @Test
    fun 보정_기대_파일의_이름은_모두_metrics_tsv에_있다() {
        // load/expectations/cal-idem-{on,off}.idem.json 에 적힌 이름(테스트 이미지에는 load/ 가 없어 여기에 옮겨 적었다).
        // 없는 이름은 기대 파일 대조에서 "실제 없음"이 되어 보정이 조용히 틀린 것을 견주게 된다.
        val names = listOf(
            "clients.completed", "clients.unknown", "clients.failed", "clients.status.422", "doubledTotal",
            "layers.order.keyRows", "layers.inventory.keyRows", "layers.payment.keyRows",
            "layers.order.keyStates.COMPLETED", "layers.inventory.keyStates.COMPLETED", "layers.payment.keyStates.COMPLETED",
            "layers.payment.processedRows",
            "layers.order.counts.new", "layers.order.counts.replayed", "layers.order.counts.in-progress",
            "layers.order.counts.mismatch", "layers.order.counts.resumed",
            "layers.inventory.counts.new", "layers.inventory.counts.replayed", "layers.inventory.counts.resumed",
            "layers.inventory.counts.in-progress",
            "layers.payment.counts.new", "layers.payment.counts.replayed", "layers.payment.counts.in-progress",
            "dropFaults.order.counted", "dropFaults.inventory.counted", "dropFaults.payment.counted",
            "attemptFaults.payment.slow-first.counted",
            "duplicates.requestIds", "duplicates.doubled", "duplicates.pairs.201+201", "duplicates.secondCopyReplayed",
            "reconcile.resent", "reconcile.completed", "reconcile.replayed", "reconcile.finalOnce", "reconcile.finalDoubled",
        )
        val r = good()
        r.analyze()
        val m = r.metrics()
        val missing = names.filter { it !in m }
        assertTrue(missing.isEmpty(), "metrics-idem.tsv 에 없는 이름: $missing")
    }
}
