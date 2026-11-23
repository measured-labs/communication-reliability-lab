package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 4-3 — 라벨(묶음의 fault)과 서명(응답 | order | inventory | payment)
class LabelSignatureTest {
    @TempDir
    lateinit var root: Path

    @Suppress("UNCHECKED_CAST")
    private fun signatures(rec: Map<String, Any?>, label: String) =
        (rec.at("labels", label, "signatures") as Map<String, Any?>).mapValues { (it.value as Number).toLong() }

    @Test
    fun 정상_요청의_서명은_완료와_세_처리_행이다() {
        val rec = RunRecord(root).completedChain("a").completedChain("b").analyze()
        assertEquals(mapOf("completed | COMPLETED | RESERVED | APPROVED" to 2L), signatures(rec, ""))
    }

    @Test
    fun 행이_없는_층은_서명에서_줄표다() {
        val rec = RunRecord(root).answer("a", 500, "order:error").analyze()
        assertEquals(mapOf("failed | - | - | -" to 1L), signatures(rec, "order:error"))
    }

    @Test
    fun 서명은_처리_행이_아닌_행의_상태도_적는다() {
        val rec = RunRecord(root).answer("a", 504, "payment:delay=1500").rows("a", "UNKNOWN", "UNKNOWN", "APPROVED").analyze()
        assertEquals(mapOf("unknown | UNKNOWN | UNKNOWN | APPROVED" to 1L), signatures(rec, "payment:delay=1500"))
    }

    @Test
    fun 서명의_판정과_상태는_사전순으로_정렬해_더하기로_잇는다() {
        // 입력 차례를 일부러 사전순의 반대로 둔다
        val rec = RunRecord(root).answer("a", 500).answer("a", 201)
            .row("order", "a", "FAILED").row("order", "a", "COMPLETED")
            .row("inventory", "a", "RESERVED").row("payment", "a", "APPROVED").analyze()
        assertEquals(mapOf("completed+failed | COMPLETED+FAILED | RESERVED | APPROVED" to 1L), signatures(rec, ""))
    }

    @Test
    fun 묶음의_fault가_서로_다르면_라벨은_mixed다() {
        val rec = RunRecord(root).answer("a", 201, "").answer("a", 500, "order:error")
            .rows("a", "COMPLETED", "RESERVED", "APPROVED").analyze()
        assertEquals(mapOf("completed+failed | COMPLETED | RESERVED | APPROVED" to 1L), signatures(rec, "(mixed)"))
    }

    @Test
    fun 라벨마다_정답_줄_수와_requestId_수를_센다() {
        val rec = RunRecord(root).completedChain("a").completedChain("a").completedChain("b")
            .answer("c", 500, "order:error").analyze()
        assertEquals(3L to 2L, rec.long("labels", "", "answers") to rec.long("labels", "", "requestIds"))
    }

    @Test
    fun 라벨마다_판정을_줄마다_센다() {
        val rec = RunRecord(root).answer("a", 201, "inventory:delay=300").answer("b", 504, "inventory:delay=300")
            .answer("c", 504, "inventory:delay=300").analyze()
        val v = listOf("completed", "failed", "unknown").map { rec.long("labels", "inventory:delay=300", "verdicts", it) }
        assertEquals(listOf(1L, 0L, 2L), v)
    }
}
