package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// docs/measurement.md 9-2·9-3·9-4·9-5 ④ — 업무 행 id(`db/row-ids-<층>.csv`)와 키 행의 row_id(`db/idempotency-row-ids-<층>.csv`),
// 그 짝(keyRows 대 업무 행 수, keyLinks 넷), `--no-row-ids`.
// 기준 기록(goodIdemRecord)의 업무 행 id: 층마다 a=1, b=2, c=3, d=4, e=5, f=6(넣은 차례). 키 행은 같은 requestId 의 행을 가리킨다.
// 입력 오류 테스트마다 기준 기록(종료 0)에서 칸 하나만 바꾼다.
class IdemRowIdsTest {
    @TempDir
    lateinit var root: Path

    private fun good() = goodIdemRecord(root)

    private val ids = listOf("a", "b", "c", "d", "e", "f")

    /** 주문의 row-ids 를 [lines](머리줄 다음)로 쓴다. */
    private fun IdemRecord.orderRowIds(vararg lines: String): IdemRecord {
        rowIdsText["order"] = (listOf("id,request_id") + lines).joinToString("\n", postfix = "\n")
        return this
    }

    /** 주문의 키 행 row_id 를 [lines](머리줄 다음)로 쓴다. */
    private fun IdemRecord.orderKeyRowIds(vararg lines: String): IdemRecord {
        keyRowIdsText["order"] = (listOf("key,row_id") + lines).joinToString("\n", postfix = "\n")
        return this
    }

    private val defaultRowIds = ids.mapIndexed { i, id -> "${i + 1},$id" }.toTypedArray()
    private val defaultKeyRowIds = ids.mapIndexed { i, id -> "$id,${i + 1}" }.toTypedArray()

    private fun assertMentions(out: String, word: String) =
        assertTrue(out.contains(word), "입력 오류 메시지에 '$word' 가 없다: $out")

    // ---- 기준과 받는 것 ----

    @Test
    fun 기준_기록은_통과하고_keyLinks가_모두_0이다() {
        val r = good()
        assertEquals(0, r.runIdem(), r.lastOutput)
        val rep = r.report()
        for (l in LAYERS) {
            for (n in listOf("rowsNotLinked", "rowsMultiLinked", "keysToMissingRow", "requestIdDiffers")) {
                assertEquals(0L, rep.long("layers", l, "keyLinks", n), "$l.$n")
            }
        }
    }

    @Test
    fun 기준_row_id_파일을_텍스트로_다시_써도_통과한다() {
        val r = good().orderRowIds(*defaultRowIds).orderKeyRowIds(*defaultKeyRowIds)
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun id는_이어지지_않아도_된다() {
        // 9-2: 1 이상의 정수, 겹치지 않음, id 차례 — 10, 20, … 도 받는다
        val r = good()
            .orderRowIds(*ids.mapIndexed { i, id -> "${(i + 1) * 10},$id" }.toTypedArray())
            .orderKeyRowIds(*ids.mapIndexed { i, id -> "$id,${(i + 1) * 10}" }.toTypedArray())
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 같은_requestId에_업무_행_둘과_키_행_둘이_하나씩_짝이면_통과한다() {
        // g: 주문에만 업무 행 둘(키 g → 첫째, 키 g-2 → 둘째) — 짝이 맞으면 keyRows·keyLinks 는 늘 0(두 번 처리됨은 결과로 센다)
        val r = good()
        val g = T + 600
        r.answer("g", g, 502)
            .timing("order", "g", us(g + 1), us(g + 9), 502, us(g + 2), us(g + 8), "Failed:HTTP_500")
            .timing("order", "g", us(g + 11), us(g + 19), 502, us(g + 12), us(g + 18), "Failed:HTTP_500")
            .row("order", "g", "COMPLETED").row("order", "g", "COMPLETED")
        r.key("order", "g", "FAILED", 502, rowId = r.rowIdOf("order", "g", 0))
            .key("order", "g-2", "FAILED", 502, requestId = "g", rowId = r.rowIdOf("order", "g", 1))
            .counts("order", "new", 8)
        val rep = r.analyze()
        assertEquals(1L, rep.long("layers", "order", "doubled"))
        assertEquals(8L, rep.long("layers", "order", "keyRows"))
    }

    // ---- keyLinks 넷 ----

    @Test
    fun 업무_행을_두_키_행이_가리키면_rowsMultiLinked다() {
        // 주문: 키 a2(request_id a)도 a 의 행(1)을 가리키고, 새로 넣은 a 의 둘째 행(7)은 아무 키도 가리키지 않는다 — 업무 행 7·키 행 7
        val r = good()
        r.row("order", "a", "COMPLETED")
        r.key("order", "a2", "COMPLETED", 201, requestId = "a", rowId = 1).counts("order", "new", 7)
        assertEquals(
            listOf("layers.order.keyLinks.rowsNotLinked: 늘 0, 실제 1", "layers.order.keyLinks.rowsMultiLinked: 늘 0, 실제 1"),
            r.mismatch(),
        )
        assertEquals(1L, r.report().long("layers", "order", "keyLinks", "rowsMultiLinked"))
    }

    @Test
    fun 키_행이_다른_requestId의_업무_행을_가리키면_requestIdDiffers다() {
        // 주문: 키 a → 행 2(b), 키 b → 행 1(a). 업무 행마다 키 하나씩이라 다른 셋은 0
        val r = good().orderKeyRowIds("a,2", "b,1", "c,3", "d,4", "e,5", "f,6")
        assertEquals(listOf("layers.order.keyLinks.requestIdDiffers: 늘 0, 실제 2"), r.mismatch())
    }

    @Test
    fun 키_행이_없는_id를_가리키면_keysToMissingRow이고_그_행은_rowsNotLinked다() {
        val r = good().orderKeyRowIds("a,77", "b,2", "c,3", "d,4", "e,5", "f,6")
        assertEquals(
            listOf("layers.order.keyLinks.rowsNotLinked: 늘 0, 실제 1", "layers.order.keyLinks.keysToMissingRow: 늘 0, 실제 1"),
            r.mismatch(),
        )
    }

    @Test
    fun 키가_꺼진_층에도_keyLinks를_내지만_견주지_않는다() {
        // offIdemRecord: 층마다 업무 행 3, 키 행 0 — rowsNotLinked = 업무 행 수
        val r = offIdemRecord(root)
        assertEquals(0, r.runIdem(), r.lastOutput)
        assertEquals(listOf(3L, 3L, 3L), LAYERS.map { r.report().long("layers", it, "keyLinks", "rowsNotLinked") })
    }

    // ---- --no-row-ids ----

    @Test
    fun no_row_ids면_두_파일이_없어도_통과하고_keyLinks가_없다() {
        val r = good()
        for (l in LAYERS) {
            r.omit += "db/row-ids-$l.csv"
            r.omit += "db/idempotency-row-ids-$l.csv"
        }
        assertEquals(0, r.runIdem("--no-row-ids"), r.lastOutput)
        for (l in LAYERS) assertFalse("keyLinks" in (r.report().at("layers", l) as Map<*, *>), l)
    }

    @Test
    fun no_row_ids면_키_행_수와_업무_행_수를_견주지_않는다() {
        // 결제에 a 의 업무 행이 하나 더(키 행 6, 업무 행 7) — 짝 대조가 없으면 rowsWithoutKey 로는 잡히지 않는다(9-5 ④의 한계)
        val r = good().row("payment", "a", "APPROVED")
        assertEquals(0, r.runIdem("--no-row-ids"), r.lastOutput)
        assertEquals(2, r.runIdem(), r.lastOutput)
    }

    @Test
    fun no_row_ids를_주어도_두_파일이_있으면_통과한다() {
        val r = good()
        assertEquals(0, r.runIdem("--no-row-ids"), r.lastOutput)
    }

    @Test
    fun no_row_ids가_두_번이면_입력_오류다() {
        good().assertInputError("--no-row-ids", "--no-row-ids")
    }

    @Test
    fun no_row_ids에_값을_주면_입력_오류다() {
        // 값 없는 인자 — 뒤의 낱말은 모르는 인자다
        good().assertInputError("--no-row-ids", "yes")
    }

    // ---- 입력 오류: db/row-ids-<층>.csv ----

    @Test
    fun row_ids_파일이_없으면_입력_오류다() {
        val r = good()
        r.omit += "db/row-ids-inventory.csv"
        assertMentions(r.assertInputError(), "row-ids-inventory.csv")
    }

    @Test
    fun row_ids의_머리줄이_다르면_입력_오류다() {
        val r = good().orderRowIds(*defaultRowIds)
        r.rowIdsText["order"] = r.rowIdsText.getValue("order").replaceFirst("id,request_id", "row_id,request_id")
        r.assertInputError()
    }

    @Test
    fun row_ids의_id가_0이면_입력_오류다() {
        good().orderRowIds("0,a", "2,b", "3,c", "4,d", "5,e", "6,f").assertInputError()
    }

    @Test
    fun row_ids의_id가_정수가_아니면_입력_오류다() {
        good().orderRowIds("x,a", "2,b", "3,c", "4,d", "5,e", "6,f").assertInputError()
    }

    @Test
    fun row_ids의_id가_겹치면_입력_오류다() {
        good().orderRowIds("1,a", "1,b", "3,c", "4,d", "5,e", "6,f").assertInputError()
    }

    @Test
    fun row_ids의_request_id가_비면_입력_오류다() {
        good().orderRowIds("1,", "2,b", "3,c", "4,d", "5,e", "6,f").assertInputError()
    }

    @Test
    fun row_ids의_requestId_차례가_업무_CSV와_다르면_입력_오류다() {
        good().orderRowIds("1,b", "2,a", "3,c", "4,d", "5,e", "6,f").orderKeyRowIds("a,2", "b,1", "c,3", "d,4", "e,5", "f,6").assertInputError()
    }

    @Test
    fun row_ids의_행이_업무_CSV보다_적으면_입력_오류다() {
        good().orderRowIds("1,a", "2,b", "3,c", "4,d", "5,e").orderKeyRowIds("a,1", "b,2", "c,3", "d,4", "e,5", "f,77").assertInputError()
    }

    // ---- 입력 오류: db/idempotency-row-ids-<층>.csv ----

    @Test
    fun 키_row_id_파일이_없으면_입력_오류다() {
        val r = good()
        r.omit += "db/idempotency-row-ids-payment.csv"
        assertMentions(r.assertInputError(), "idempotency-row-ids-payment.csv")
    }

    @Test
    fun 키_row_id의_머리줄이_다르면_입력_오류다() {
        val r = good().orderKeyRowIds(*defaultKeyRowIds)
        r.keyRowIdsText["order"] = r.keyRowIdsText.getValue("order").replaceFirst("key,row_id", "key,id")
        r.assertInputError()
    }

    @Test
    fun 키_row_id가_0이면_입력_오류다() {
        good().orderKeyRowIds("a,0", "b,2", "c,3", "d,4", "e,5", "f,6").assertInputError()
    }

    @Test
    fun 키_row_id가_정수가_아니면_입력_오류다() {
        good().orderKeyRowIds("a,1.0", "b,2", "c,3", "d,4", "e,5", "f,6").assertInputError()
    }

    @Test
    fun 키_row_id의_key가_겹치면_입력_오류다() {
        good().orderKeyRowIds("a,1", "a,2", "c,3", "d,4", "e,5", "f,6").assertInputError()
    }

    @Test
    fun 키_row_id의_key가_비면_입력_오류다() {
        good().orderKeyRowIds(",1", "b,2", "c,3", "d,4", "e,5", "f,6").assertInputError()
    }

    @Test
    fun 키_row_id에_키_표에_없는_key가_있으면_입력_오류다() {
        good().orderKeyRowIds(*defaultKeyRowIds, "zz,1").assertInputError()
    }

    @Test
    fun 키_row_id에_키_표의_key가_빠지면_입력_오류다() {
        good().orderKeyRowIds("a,1", "b,2", "c,3", "d,4", "e,5").assertInputError()
    }

    // ---- 키 표 CSV 의 새 칸 규칙(9-2) ----

    @Test
    fun 키_표의_key가_비면_입력_오류다() {
        // 키 행 a 의 key 만 비운다(키 row_id 파일도 같은 key 로 — 짝 검사가 아니라 빈 key 로 걸리게)
        val r = good()
        r.keyCsvText["order"] = (
            listOf("key,request_id,amount,state,response_status", ",a,${amountFor("a")},COMPLETED,201") +
                listOf("b", "c", "d", "e", "f").map { "$it,$it,${amountFor(it)},COMPLETED,201" }
            ).joinToString("\n", postfix = "\n")
        r.orderKeyRowIds(",1", "b,2", "c,3", "d,4", "e,5", "f,6")
        r.assertInputError()
    }

    @Test
    fun 키_표의_request_id가_비면_입력_오류다() {
        val r = good()
        r.keyCsvText["order"] = (
            listOf("key,request_id,amount,state,response_status", "a,,${amountFor("a")},COMPLETED,201") +
                listOf("b", "c", "d", "e", "f").map { "$it,$it,${amountFor(it)},COMPLETED,201" }
            ).joinToString("\n", postfix = "\n")
        r.assertInputError()
    }

    @Test
    fun 키_표의_amount가_정수가_아니면_입력_오류다() {
        val r = good()
        r.keyCsvText["order"] = (
            listOf("key,request_id,amount,state,response_status", "a,a,1000.5,COMPLETED,201") +
                listOf("b", "c", "d", "e", "f").map { "$it,$it,${amountFor(it)},COMPLETED,201" }
            ).joinToString("\n", postfix = "\n")
        r.assertInputError()
    }

    @Test
    fun 키_표의_request_id가_정답에_없어도_입력_오류가_아니다() {
        // 9-2: 정답에 없는 request_id 는 입력 오류로 하지 않는다 — 업무 행이 없으면 keysWithoutRow·keyLinks 의 불일치
        val r = good().key("payment", "zz", "COMPLETED", 201, amount = 1).counts("payment", "new", 7)
        val d = r.mismatch()
        assertTrue("layers.payment.keysWithoutRow: 늘 0, 실제 1" in d, d.toString())
        assertTrue("layers.payment.keyLinks.keysToMissingRow: 늘 0, 실제 1" in d, d.toString())
    }
}
