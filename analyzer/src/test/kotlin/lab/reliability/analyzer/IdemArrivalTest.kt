package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 9-4 duplicates 의 새 칸(arrivalTies·arrivalGapMs·conflict409.*, laterArrivalStatus 에서 같은 μs 를 뺌)과
// layers.<L>.responses.409·422·0·0Repeat, 9-5 ⑤(서비스가 센 in-progress·mismatch 대 시각 기록의 409·422).
// 값은 문서의 식으로 손으로 셈했다.
class IdemArrivalTest {
    @TempDir
    lateinit var root: Path

    /**
     * 동시에 두 번 보낸 쌍 넷(키 켬, 맞추기 없음). 시각은 T 기준 ms, 같은 μs 는 [us]로 같게.
     * - p1(T): copy1 201(주문 T+1, 세 층), copy2 409(주문 T+5~T+6) — 나중에 든 것이 409. 차 4ms.
     * - p2(T+100): copy1 409(주문 T+101~T+103 — 먼저 듦), copy2 201(주문 T+102, 세 층) — 먼저 든 것이 409. 차 1ms.
     * - p3(T+200): copy1 201(주문 T+201, 세 층), copy2 409(주문 T+201~T+202 — 같은 μs). 차 0.
     * - p4(T+300): 잇달아 — copy1 201(주문 T+301, 세 층), copy2 201 replayed(주문 T+331~T+333). 차 30ms.
     * 센 수: 주문 new 4·in-progress 3·replayed 1, 재고·결제 new 4.
     */
    private fun pairs(): IdemRecord {
        val r = IdemRecord(root)
        r.answer("p1", T, 201, copy = 1).answer("p1", T, 409, copy = 2)
            .chainTimings("p1", T).timing("order", "p1", us(T + 5), us(T + 6), 409)
        r.answer("p2", T + 100, 409, copy = 1).answer("p2", T + 100, 201, copy = 2)
            .timing("order", "p2", us(T + 101), us(T + 103), 409).chainTimings("p2", T + 101)
        r.answer("p3", T + 200, 201, copy = 1).answer("p3", T + 200, 409, copy = 2)
            .chainTimings("p3", T + 200).timing("order", "p3", us(T + 201), us(T + 202), 409)
        r.answer("p4", T + 300, 201, copy = 1).answer("p4", T + 330, 201, copy = 2, replayed = true)
            .chainTimings("p4", T + 300).timing("order", "p4", us(T + 331), us(T + 333), 201)
        for (id in listOf("p1", "p2", "p3", "p4")) r.processedRows(id).keys(id)
        return r.counts("order", "new", 4).counts("order", "in-progress", 3).counts("order", "replayed", 1)
            .counts("inventory", "new", 4).counts("payment", "new", 4)
    }

    @Test
    fun 기준_쌍_기록은_통과한다() {
        val r = pairs()
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 같은_μs에_든_쌍은_나중에_든_것에_넣지_않고_arrivalTies로_센다() {
        val rep = pairs().analyze()
        assertEquals(1L, rep.long("duplicates", "arrivalTies"))
        // p1 나중 409, p2 나중 201, p4 나중 201 — p3 은 빠진다
        assertEquals(1L, rep.long("duplicates", "laterArrivalStatus", "409"))
        assertEquals(2L, rep.long("duplicates", "laterArrivalStatus", "201"))
    }

    @Test
    fun 같은_μs가_아니면_arrivalTies는_0이다() {
        // p3 의 409 를 1μs 늦춘다 — 나중 = 409
        val r = IdemRecord(root)
        r.answer("p3", T, 201, copy = 1).answer("p3", T, 409, copy = 2)
            .chainTimings("p3", T).timing("order", "p3", us(T + 1) + 1, us(T + 2), 409)
            .processedRows("p3").keys("p3")
            .counts("order", "new", 1).counts("order", "in-progress", 1).counts("inventory", "new", 1).counts("payment", "new", 1)
        val rep = r.analyze()
        assertEquals(0L, rep.long("duplicates", "arrivalTies"))
        assertEquals(1L, rep.long("duplicates", "laterArrivalStatus", "409"))
        assertEquals(1L, rep.long("duplicates", "conflict409", "laterGot409"))
        assertEquals(0L, rep.long("duplicates", "conflict409", "sameMicros"))
    }

    @Test
    fun 가장_늦은_둘이_같을_때만_같은_μs다() {
        // 주문의 시각 기록 셋: 앞의 둘이 같은 μs, 마지막은 늦다 — 나중을 가릴 수 있다
        val r = IdemRecord(root)
        r.answer("q", T, 201, copy = 1).answer("q", T, 409, copy = 2).answer("q", T, 409, copy = 3)
            .chainTimings("q", T)
            .timing("order", "q", us(T + 1), us(T + 2), 409)
            .timing("order", "q", us(T + 7), us(T + 8), 409)
            .processedRows("q").keys("q")
            .counts("order", "new", 1).counts("order", "in-progress", 2).counts("inventory", "new", 1).counts("payment", "new", 1)
        val rep = r.analyze()
        assertEquals(0L, rep.long("duplicates", "arrivalTies"))
        assertEquals(1L, rep.long("duplicates", "laterArrivalStatus", "409"))
    }

    @Test
    fun 두_도착의_차는_주문의_시각_기록이_정확히_둘인_requestId의_분위수다() {
        // [0, 1, 4, 30] (n=4): p50 = 2번째 1, max 30
        val rep = pairs().analyze()
        assertEquals(4L, rep.long("duplicates", "arrivalGapMs", "count"))
        assertEquals(1.0, rep.double("duplicates", "arrivalGapMs", "p50"))
        assertEquals(30.0, rep.double("duplicates", "arrivalGapMs", "max"))
    }

    @Test
    fun conflict409는_201과_409_쌍을_먼저_나중_같은_μs로_가른다() {
        val rep = pairs().analyze()
        assertEquals(1L, rep.long("duplicates", "conflict409", "earlierGot409"))
        assertEquals(1L, rep.long("duplicates", "conflict409", "laterGot409"))
        assertEquals(1L, rep.long("duplicates", "conflict409", "sameMicros"))
        assertEquals(3L, rep.long("duplicates", "pairs", "201+409"))
        assertEquals(1L, rep.long("duplicates", "pairs", "201+201"))
    }

    @Test
    fun conflict409는_주문의_시각_기록이_정확히_둘인_201과_409_쌍만_센다() {
        // p5: 201+409 인데 주문에 셋 듦 — 넣지 않는다. p6: 201+422 — 201+409 가 아니다
        val r = pairs()
        r.answer("p5", T + 400, 201, copy = 1).answer("p5", T + 400, 409, copy = 2)
            .chainTimings("p5", T + 400)
            .timing("order", "p5", us(T + 405), us(T + 406), 409)
            .timing("order", "p5", us(T + 407), us(T + 408), 409)
            .answer("p6", T + 500, 201, copy = 1).answer("p6", T + 530, 422, copy = 2, amount = amountFor("p6") + 1)
            .chainTimings("p6", T + 500).timing("order", "p6", us(T + 531), us(T + 532), 422)
        for (id in listOf("p5", "p6")) r.processedRows(id).keys(id)
        r.counts("order", "new", 6).counts("order", "in-progress", 5).counts("order", "mismatch", 1)
            .counts("inventory", "new", 6).counts("payment", "new", 6)
        val rep = r.analyze()
        val sum = listOf("earlierGot409", "laterGot409", "sameMicros").sumOf { rep.long("duplicates", "conflict409", it) }
        assertEquals(3L, sum)
        assertEquals(4L, rep.long("duplicates", "pairs", "201+409"))
        // p1~p4 와 p6(주문에 둘) — p5(셋)는 빠진다
        assertEquals(5L, rep.long("duplicates", "arrivalGapMs", "count"))
    }

    // ---- responses 와 ⑤ ----

    @Test
    fun 층마다_낸_409_422_0과_다시_온_키였을_수_있는_0() {
        // goodIdemRecord: 주문 409 1(e), 422 1(d), 0 1(f — 처음 온 키를 끊음: f 의 다른 기록은 그 뒤 R+101 에 들어 0Repeat 아님)
        // 결제 0 1(c), 재고 0 0
        val rep = goodIdemRecord(root).analyze()
        assertEquals(listOf(1L, 1L, 1L, 0L), listOf("409", "422", "0", "0Repeat").map { rep.long("layers", "order", "responses", it) })
        assertEquals(listOf(0L, 0L, 0L, 0L), listOf("409", "422", "0", "0Repeat").map { rep.long("layers", "inventory", "responses", it) })
        assertEquals(listOf(0L, 0L, 1L, 0L), listOf("409", "422", "0", "0Repeat").map { rep.long("layers", "payment", "responses", it) })
    }

    /** 기준 기록의 e 에 주문의 끊긴 기록 하나를 더한다 — e 의 다른 기록(T+401 에 듦)이 그것이 끝나기(T+420) 전에 들었다. */
    private fun withRepeatDrop(): IdemRecord =
        goodIdemRecord(root).timing("order", "e", us(T + 403), us(T + 420), 0)

    @Test
    fun 끝나기_전에_같은_requestId가_든_끊긴_기록은_0Repeat다() {
        val rep = withRepeatDrop().analyze()
        assertEquals(2L, rep.long("layers", "order", "responses", "0"))
        assertEquals(1L, rep.long("layers", "order", "responses", "0Repeat"))
    }

    @Test
    fun 센_in_progress가_시각_기록의_409보다_많으면_불일치다() {
        val d = goodIdemRecord(root).counts("order", "in-progress", 2).mismatch()
        assertEquals(
            listOf("layers.order.counts: in-progress 2·mismatch 1가 시각 기록의 409 1·422 1와 맞지 않는다(409·422였을 수 있는 끊긴 응답 0)"),
            d,
        )
    }

    @Test
    fun 시각_기록의_409가_센_in_progress보다_많으면_불일치다() {
        val d = goodIdemRecord(root).counts("order", "in-progress", 0).mismatch()
        assertEquals(
            listOf("layers.order.counts: in-progress 0·mismatch 1가 시각 기록의 409 1·422 1와 맞지 않는다(409·422였을 수 있는 끊긴 응답 0)"),
            d,
        )
    }

    @Test
    fun 시각_기록의_422가_센_mismatch보다_많으면_불일치다() {
        val d = goodIdemRecord(root).counts("order", "mismatch", 0).mismatch()
        assertEquals(
            listOf("layers.order.counts: in-progress 1·mismatch 0가 시각 기록의 409 1·422 1와 맞지 않는다(409·422였을 수 있는 끊긴 응답 0)"),
            d,
        )
    }

    @Test
    fun 넘친_몫이_다시_온_키였을_수_있는_끊긴_응답만큼이면_받는다() {
        val r = withRepeatDrop().counts("order", "in-progress", 2)
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 넘친_몫이_0Repeat을_넘으면_불일치다() {
        // in-progress +1, mismatch +1 — 넘친 몫 2 > 0Repeat 1
        val d = withRepeatDrop().counts("order", "in-progress", 2).counts("order", "mismatch", 2).mismatch()
        assertEquals(
            listOf("layers.order.counts: in-progress 2·mismatch 2가 시각 기록의 409 1·422 1와 맞지 않는다(409·422였을 수 있는 끊긴 응답 1)"),
            d,
        )
    }

    @Test
    fun 처음_온_키를_끊은_기록은_받아_주지_않는다() {
        // f 의 끊긴 기록(responses.0 = 1)은 0Repeat 이 아니다 — in-progress +1 은 불일치
        val d = goodIdemRecord(root).counts("order", "in-progress", 2).mismatch()
        assertEquals(1, d.size, d.toString())
    }

    @Test
    fun 재고_층의_대조도_같은_꼴이다() {
        val d = goodIdemRecord(root).counts("inventory", "mismatch", 1).mismatch()
        assertEquals(
            listOf("layers.inventory.counts: in-progress 0·mismatch 1가 시각 기록의 409 0·422 0와 맞지 않는다(409·422였을 수 있는 끊긴 응답 0)"),
            d,
        )
    }
}
