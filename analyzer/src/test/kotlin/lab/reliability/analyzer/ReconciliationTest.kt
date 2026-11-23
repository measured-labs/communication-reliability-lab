package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 4-1, 4-2 — 응답 판정과 DB 대조(잃음, 두 번 처리됨, 결과 모름, 행 수 지표)
class ReconciliationTest {
    @TempDir
    lateinit var root: Path

    private fun record() = RunRecord(root)

    // ---- 1. 모두 맞는 기록 ----

    @Test
    fun 모두_맞는_기록은_잃음과_두_번_처리됨과_층_불일치가_0이다() {
        val rec = record().completedChain("a").completedChain("b").completedChain("c").analyze()
        assertEquals(listOf(0L, 0L, 0L), listOf(rec.long("lostTotal"), rec.long("doubledTotal"), rec.long("layerMismatch")))
    }

    @Test
    fun 모두_맞는_기록의_층마다_행과_처리_행은_요청_수와_같다() {
        val rec = record().completedChain("a").completedChain("b").completedChain("c").analyze()
        assertEquals(listOf(3L, 3L, 3L), rec.perLayer("rows"))
        assertEquals(listOf(3L, 3L, 3L), rec.perLayer("processedRows"))
    }

    @Test
    fun 정답_줄_수와_requestId_수를_센다() {
        val rec = record().completedChain("a").answer("a").row("order", "a", "COMPLETED")
            .row("inventory", "a", "RESERVED").row("payment", "a", "APPROVED")
            .completedChain("b").analyze()
        assertEquals(3L to 2L, rec.long("answers") to rec.long("requestIds"))
    }

    // ---- 2. 잃음 ----

    private fun lostIn(layer: String): Map<String, Any?> {
        val r = record().completedChain("a").completedChain("c").answer("b")
        LAYERS.filter { it != layer }.forEach { l -> r.row(l, "b", processedStatus(l)) }
        return r.analyze()
    }

    @Test
    fun 완료_응답인데_order_처리_행이_없으면_order만_잃음_1() {
        val rec = lostIn("order")
        assertEquals(listOf(1L, 0L, 0L), rec.perLayer("lost"))
        assertEquals(1L, rec.long("lostTotal"))
    }

    @Test
    fun 완료_응답인데_inventory_처리_행이_없으면_inventory만_잃음_1() {
        val rec = lostIn("inventory")
        assertEquals(listOf(0L, 1L, 0L), rec.perLayer("lost"))
        assertEquals(1L, rec.long("lostTotal"))
    }

    @Test
    fun 완료_응답인데_payment_처리_행이_없으면_payment만_잃음_1() {
        val rec = lostIn("payment")
        assertEquals(listOf(0L, 0L, 1L), rec.perLayer("lost"))
        assertEquals(1L, rec.long("lostTotal"))
    }

    @Test
    fun 세_층_모두_행이_없어도_잃음_전체는_requestId_하나로_센다() {
        val rec = record().completedChain("a").answer("b").analyze()
        assertEquals(listOf(1L, 1L, 1L), rec.perLayer("lost"))
        assertEquals(1L, rec.long("lostTotal"))
    }

    @Test
    fun 완료_응답인데_처리_행이_아닌_행만_있으면_잃음이다() {
        // order 에 UNKNOWN 행은 있지만 처리 행(COMPLETED)이 아니다
        val rec = record().answer("a", 201).rows("a", "UNKNOWN", "RESERVED", "APPROVED").analyze()
        assertEquals(listOf(1L, 0L, 0L), rec.perLayer("lost"))
    }

    @Test
    fun 실패_응답은_행이_없어도_잃음이_아니다() {
        val rec = record().answer("a", 500, "order:error").analyze()
        assertEquals(0L, rec.long("lostTotal"))
    }

    @Test
    fun 묶음에_완료가_하나라도_있으면_처리_행이_없을_때_잃음이다() {
        val rec = record().answer("a", 500).answer("a", 201).analyze()
        assertEquals(1L, rec.long("lostTotal"))
    }

    @Test
    fun 같은_id를_두_번_보내_모두_완료여도_처리_행이_하나면_잃음이_아니다() {
        val rec = record().completedChain("a").answer("a", 201).analyze()
        assertEquals(0L, rec.long("lostTotal"))
    }

    @Test
    fun 같은_id를_두_번_보내_모두_완료여도_처리_행이_하나면_두_번_처리됨이_아니다() {
        val rec = record().completedChain("a").answer("a", 201).analyze()
        assertEquals(0L, rec.long("doubledTotal"))
    }

    // ---- 3. 두 번 처리됨 ----

    private fun doubledIn(layer: String): Map<String, Any?> =
        record().completedChain("a").completedChain("b").row(layer, "b", processedStatus(layer)).analyze()

    @Test
    fun 정답_한_줄인데_order_처리_행이_둘이면_order만_두_번_처리됨_1() {
        val rec = doubledIn("order")
        assertEquals(listOf(1L, 0L, 0L), rec.perLayer("doubled"))
        assertEquals(1L, rec.long("doubledTotal"))
    }

    @Test
    fun 정답_한_줄인데_inventory_처리_행이_둘이면_inventory만_두_번_처리됨_1() {
        val rec = doubledIn("inventory")
        assertEquals(listOf(0L, 1L, 0L), rec.perLayer("doubled"))
        assertEquals(1L, rec.long("doubledTotal"))
    }

    @Test
    fun 정답_한_줄인데_payment_처리_행이_둘이면_payment만_두_번_처리됨_1() {
        val rec = doubledIn("payment")
        assertEquals(listOf(0L, 0L, 1L), rec.perLayer("doubled"))
        assertEquals(1L, rec.long("doubledTotal"))
    }

    @Test
    fun 같은_id를_두_번_보내_세_층에_행이_둘씩이면_층마다_두_번_처리됨_1이고_전체도_1이다() {
        val rec = record().completedChain("a").completedChain("a").completedChain("b").analyze()
        assertEquals(listOf(1L, 1L, 1L), rec.perLayer("doubled"))
        assertEquals(1L, rec.long("doubledTotal"))
    }

    @Test
    fun 처리_행이_셋이어도_두_번_처리됨은_requestId_하나로_센다() {
        val rec = record().completedChain("a").row("order", "a", "COMPLETED").row("order", "a", "COMPLETED").analyze()
        assertEquals(1L, rec.long("layers", "order", "doubled"))
    }

    @Test
    fun 처리_행_하나와_처리_행이_아닌_행_하나는_두_번_처리됨이_아니다() {
        val rec = record().completedChain("a").row("order", "a", "FAILED").analyze()
        assertEquals(0L, rec.long("doubledTotal"))
    }

    // ---- 4. 결과 모름 ----

    @Test
    fun 상태_200과_299는_완료이고_199와_300은_실패다() {
        val rec = record().answer("a", 199).answer("b", 200).answer("c", 299).answer("d", 300).analyze()
        assertEquals(listOf(2L, 2L, 0L), verdicts(rec))
    }

    @Test
    fun 상태_0과_504는_결과_모름이고_503과_505는_실패다() {
        val rec = record().answer("a", 0).answer("b", 503).answer("c", 504).answer("d", 505).analyze()
        assertEquals(listOf(0L, 2L, 2L), verdicts(rec))
    }

    @Test
    fun 판정_건수는_requestId가_아니라_정답_줄마다_센다() {
        val rec = record().answer("a", 504).answer("a", 504).answer("a", 201).analyze()
        assertEquals(listOf(1L, 0L, 2L), verdicts(rec))
    }

    @Test
    fun 결과_모름인데_세_층에_처리_행이_있으면_층마다_결과_모름_처리됨() {
        val rec = record().answer("a", 0, "order:delay=6000").rows("a", "COMPLETED", "RESERVED", "APPROVED").analyze()
        assertEquals(listOf(1L, 1L, 1L), rec.perLayer("unknownProcessed"))
        assertEquals(listOf(0L, 0L, 0L), rec.perLayer("unknownNotProcessed"))
    }

    @Test
    fun 결과_모름인데_행이_없으면_층마다_결과_모름_처리_안_됨() {
        val rec = record().answer("a", 504).analyze()
        assertEquals(listOf(0L, 0L, 0L), rec.perLayer("unknownProcessed"))
        assertEquals(listOf(1L, 1L, 1L), rec.perLayer("unknownNotProcessed"))
    }

    @Test
    fun 결과_모름은_층마다_처리_여부가_갈린다() {
        // inventory:delay=2500 꼴 — order 는 UNKNOWN, 아래 둘은 처리함
        val rec = record().answer("a", 504, "inventory:delay=2500").rows("a", "UNKNOWN", "RESERVED", "APPROVED").analyze()
        assertEquals(listOf(0L, 1L, 1L), rec.perLayer("unknownProcessed"))
        assertEquals(listOf(1L, 0L, 0L), rec.perLayer("unknownNotProcessed"))
    }

    @Test
    fun 결과_모름은_행이_없어도_잃음이_아니다() {
        val rec = record().answer("a", 0).analyze()
        assertEquals(0L, rec.long("lostTotal"))
    }

    @Test
    fun 결과_모름은_처리_행이_있어도_실패인데_처리됨에_섞이지_않는다() {
        val rec = record().answer("a", 504).rows("a", "COMPLETED", "RESERVED", "APPROVED").analyze()
        assertEquals(listOf(0L, 0L, 0L), rec.perLayer("failedButProcessed"))
    }

    @Test
    fun 완료와_결과_모름이_섞인_묶음은_결과_모름_지표에_들지_않는다() {
        val rec = record().answer("a", 504).answer("a", 201).rows("a", "COMPLETED", "RESERVED", "APPROVED").analyze()
        assertEquals(listOf(0L, 0L, 0L), rec.perLayer("unknownProcessed"))
    }

    // ---- 5. 실패 응답인데 처리됨 ----

    @Test
    fun 실패_응답인데_세_층에_처리_행이_있으면_층마다_실패인데_처리됨() {
        val rec = record().answer("a", 500, "order:fail-after-commit").rows("a", "COMPLETED", "RESERVED", "APPROVED").analyze()
        assertEquals(listOf(1L, 1L, 1L), rec.perLayer("failedButProcessed"))
    }

    @Test
    fun 실패_응답이고_처리_행이_아래_층에만_있으면_그_층만_센다() {
        // inventory:fail-after-commit 꼴
        val rec = record().answer("a", 502, "inventory:fail-after-commit").rows("a", "FAILED", "RESERVED", "APPROVED").analyze()
        assertEquals(listOf(0L, 1L, 1L), rec.perLayer("failedButProcessed"))
    }

    @Test
    fun 실패와_결과_모름이_섞인_묶음은_모두_실패가_아니다() {
        val rec = record().answer("a", 500).answer("a", 0).rows("a", "COMPLETED", "RESERVED", "APPROVED").analyze()
        assertEquals(listOf(0L, 0L, 0L), rec.perLayer("failedButProcessed"))
        assertEquals(listOf(1L, 1L, 1L), rec.perLayer("unknownProcessed"))
    }

    // ---- 6. 정답에 없는 행, PENDING 행 ----

    @Test
    fun 정답에_없는_requestId의_행은_그_층의_orphanRows로_센다() {
        val rec = record().completedChain("a").row("inventory", "ghost", "RESERVED").analyze()
        assertEquals(listOf(0L, 1L, 0L), rec.perLayer("orphanRows"))
    }

    @Test
    fun 정답에_없는_행은_rows에는_든다() {
        val rec = record().completedChain("a").row("inventory", "ghost", "RESERVED").analyze()
        assertEquals(listOf(1L, 2L, 1L), rec.perLayer("rows"))
    }

    @Test
    fun 정답에_없는_행은_두_번_처리됨과_층_불일치에_들지_않는다() {
        val rec = record().completedChain("a")
            .row("order", "ghost", "COMPLETED").row("order", "ghost", "COMPLETED").analyze()
        assertEquals(0L to 0L, rec.long("doubledTotal") to rec.long("layerMismatch"))
    }

    @Test
    fun PENDING_행은_pendingRows로_세고_처리_행이_아니다() {
        val rec = record().answer("a", 0).rows("a", "PENDING", null, null).analyze()
        assertEquals(listOf(1L, 0L, 0L), rec.perLayer("pendingRows"))
        assertEquals(listOf(0L, 0L, 0L), rec.perLayer("processedRows"))
        assertEquals(listOf(1L, 0L, 0L), rec.perLayer("rows"))
    }

    @Test
    fun 완료_응답인데_order_행이_PENDING이면_order에서_잃음이다() {
        val rec = record().answer("a", 201).rows("a", "PENDING", "RESERVED", "APPROVED").analyze()
        assertEquals(listOf(1L, 0L, 0L), rec.perLayer("lost"))
    }

    // ---- 7. 층 사이의 건수 ----

    @Test
    fun 세_층의_처리_수가_다른_requestId를_층_불일치로_센다() {
        // payment:fail-after-commit 꼴 (0,0,1) 과 inventory:delay=2500 꼴 (0,1,1)
        val rec = record()
            .answer("a", 500).rows("a", "FAILED", "FAILED", "APPROVED")
            .answer("b", 504).rows("b", "UNKNOWN", "RESERVED", "APPROVED")
            .completedChain("c").analyze()
        assertEquals(2L, rec.long("layerMismatch"))
    }

    @Test
    fun 세_층_모두_처리하지_않은_requestId는_층_불일치가_아니다() {
        // payment:error 꼴 — 처리 수 (0,0,0)
        val rec = record().answer("a", 502, "payment:error").rows("a", "FAILED", "FAILED", null).analyze()
        assertEquals(0L, rec.long("layerMismatch"))
    }

    @Test
    fun 세_층이_모두_두_번_처리했으면_층_불일치가_아니다() {
        val rec = record().completedChain("a").completedChain("a").analyze()
        assertEquals(0L, rec.long("layerMismatch"))
    }

    @Test
    fun 한_층만_두_번_처리했으면_층_불일치다() {
        val rec = record().completedChain("a").row("order", "a", "COMPLETED").analyze()
        assertEquals(1L, rec.long("layerMismatch"))
    }

    private fun verdicts(rec: Map<String, Any?>) =
        listOf(rec.long("verdicts", "completed"), rec.long("verdicts", "failed"), rec.long("verdicts", "unknown"))
}

fun processedStatus(layer: String) = when (layer) {
    "order" -> "COMPLETED"
    "inventory" -> "RESERVED"
    else -> "APPROVED"
}
