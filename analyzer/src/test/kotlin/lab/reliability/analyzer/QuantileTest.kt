package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

// docs/measurement.md 4-5 — elapsedMs 의 nearest-rank 분위수: 오름차순 n개에서 p 분위수 = ceil(p·n)번째 값
class QuantileTest {
    @TempDir
    lateinit var root: Path

    private fun quantiles(rec: Map<String, Any?>, vararg group: String) =
        listOf("count", "p50", "p95", "p99", "max").map { rec.double(*group, it) }

    @Test
    fun 한_건이면_모든_분위수가_그_값이다() {
        val rec = RunRecord(root).completedChain("a", elapsedMs = 7).analyze()
        assertEquals(listOf(1.0, 7.0, 7.0, 7.0, 7.0), quantiles(rec, "elapsedMs", "all"))
    }

    @Test
    fun 두_건이면_p50은_작은_값이고_p95와_p99는_큰_값이다() {
        // ceil(0.5·2)=1, ceil(0.95·2)=2, ceil(0.99·2)=2 — 보간하면 p50이 15가 된다
        val rec = RunRecord(root).completedChain("b", elapsedMs = 20).completedChain("a", elapsedMs = 10).analyze()
        assertEquals(listOf(2.0, 10.0, 20.0, 20.0, 20.0), quantiles(rec, "elapsedMs", "all"))
    }

    @Test
    fun 백_건이면_p50_p95_p99는_50_95_99번째_값이다() {
        val r = RunRecord(root)
        // 1..100 을 섞인 차례로 넣는다
        (1..100).map { (it * 37) % 100 + 1 }.forEach { r.completedChain("id-$it", elapsedMs = it) }
        val rec = r.analyze()
        assertEquals(listOf(100.0, 50.0, 95.0, 99.0, 100.0), quantiles(rec, "elapsedMs", "all"))
    }

    @Test
    fun 소수인_경과도_그대로_분위수가_된다() {
        val rec = RunRecord(root).completedChain("a", elapsedMs = 12.5).analyze()
        assertEquals(12.5, rec.double("elapsedMs", "all", "p50"))
    }

    @Test
    fun 판정마다_따로_분위수를_낸다() {
        val rec = RunRecord(root).completedChain("a", elapsedMs = 30).answer("b", 500, "", 5).answer("c", 500, "", 9).analyze()
        assertEquals(listOf(1.0, 30.0, 30.0, 30.0, 30.0), quantiles(rec, "elapsedMs", "completed"))
        assertEquals(listOf(2.0, 5.0, 9.0, 9.0, 9.0), quantiles(rec, "elapsedMs", "failed"))
    }

    @Test
    fun 영_건인_묶음은_count_0이고_값은_null이다() {
        val rec = RunRecord(root).completedChain("a").analyze()
        assertEquals(listOf(0.0, null, null, null, null), quantiles(rec, "elapsedMs", "unknown"))
    }

    @Test
    fun 라벨마다_따로_분위수를_낸다() {
        val rec = RunRecord(root).completedChain("a", elapsedMs = 30)
            .answer("b", 201, "payment:delay=300", 300).rows("b", "COMPLETED", "RESERVED", "APPROVED")
            .answer("c", 201, "payment:delay=300", 340).rows("c", "COMPLETED", "RESERVED", "APPROVED").analyze()
        assertEquals(listOf(2.0, 300.0, 340.0, 340.0, 340.0), quantiles(rec, "labels", "payment:delay=300", "elapsedMs"))
    }
}
