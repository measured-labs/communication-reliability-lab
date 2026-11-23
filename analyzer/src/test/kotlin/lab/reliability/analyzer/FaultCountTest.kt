package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 4-4 — 넣은 장애 수(정답의 fault)와 서비스가 센 수(fault-counts.json)의 대조
class FaultCountTest {
    @TempDir
    lateinit var root: Path

    private fun cell(rec: Map<String, Any?>, service: String, kind: String) = rec.faultCell(service, kind).let {
        Triple((it["injected"] as Number).toLong(), (it["counted"] as Number).toLong(), it["match"])
    }

    private fun twoDelaysAndOneError() = RunRecord(root)
        .answer("a", 201, "inventory:delay=300")
        .answer("b", 504, "inventory:delay=2500")
        .answer("c", 502, "payment:error")
        .answer("d", 201, "")

    @Test
    fun 넣은_수와_센_수가_아홉_칸_모두_같으면_장애_수_일치() {
        val rec = twoDelaysAndOneError().counted("inventory", "delay", 2).counted("payment", "error", 1).analyze()
        assertEquals(true, rec["faultCountsMatch"])
    }

    @Test
    fun 장애_수_대조는_아홉_칸이다() {
        val rec = twoDelaysAndOneError().counted("inventory", "delay", 2).counted("payment", "error", 1).analyze()
        @Suppress("UNCHECKED_CAST")
        val cells = (rec["faultCounts"] as List<Map<String, Any?>>).map { it["service"] to it["kind"] }.toSet()
        assertEquals(LAYERS.flatMap { s -> KINDS.map { k -> s to k } }.toSet(), cells)
    }

    @Test
    fun delay는_값과_상관없이_줄마다_1로_센다() {
        val rec = twoDelaysAndOneError().counted("inventory", "delay", 2).counted("payment", "error", 1).analyze()
        assertEquals(Triple(2L, 2L, true), cell(rec, "inventory", "delay"))
    }

    @Test
    fun 한_칸이라도_다르면_장애_수_불일치() {
        val rec = twoDelaysAndOneError().counted("inventory", "delay", 2).analyze()
        assertEquals(false, rec["faultCountsMatch"])
    }

    @Test
    fun 다른_칸은_그_칸의_match가_거짓이다() {
        val rec = twoDelaysAndOneError().counted("inventory", "delay", 2).analyze()
        assertEquals(Triple(1L, 0L, false), cell(rec, "payment", "error"))
    }

    @Test
    fun 센_수가_넣은_수보다_많아도_불일치다() {
        val rec = twoDelaysAndOneError().counted("inventory", "delay", 2).counted("payment", "error", 1)
            .counted("order", "fail-after-commit", 1).analyze()
        assertEquals(Triple(0L, 1L, false), cell(rec, "order", "fail-after-commit"))
    }

    @Test
    fun 한_줄에_여러_지시가_있으면_서비스와_종류마다_하나씩_센다() {
        val rec = RunRecord(root).answer("a", 502, "inventory:error,payment:delay=100").analyze()
        assertEquals(1L to 1L, cell(rec, "inventory", "error").first to cell(rec, "payment", "delay").first)
    }
}
