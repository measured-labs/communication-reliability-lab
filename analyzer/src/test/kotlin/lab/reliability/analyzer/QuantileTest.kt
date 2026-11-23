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

    // ---- 순위는 정수 산술(4-5): rank = (q·n + 99) / 100 ----
    // n=20: p50 → (1000+99)/100 = 10, p95 → (1900+99)/100 = 19, p99 → (1980+99)/100 = 20
    // n=21: p50 → (1050+99)/100 = 11, p95 → (1995+99)/100 = 20, p99 → (2079+99)/100 = 21
    // (q·n 이 100의 배수인 경계와 아닌 경우. 50·95·99 에서 double 의 ceil(p·n) 과 갈리는 n 은 1..200000 에 없었다 — 손계산 값으로 단정한다.)

    private fun ofSize(n: Int): Map<String, Any?> {
        val r = RunRecord(root)
        (1..n).reversed().forEach { r.completedChain("id-$it", elapsedMs = it * 10) }
        return r.analyze()
    }

    @Test
    fun 스무_건이면_p50_p95_p99는_10_19_20번째_값이다() {
        assertEquals(listOf(20.0, 100.0, 190.0, 200.0, 200.0), quantiles(ofSize(20), "elapsedMs", "all"))
    }

    @Test
    fun 스물한_건이면_p50_p95_p99는_11_20_21번째_값이다() {
        assertEquals(listOf(21.0, 110.0, 200.0, 210.0, 210.0), quantiles(ofSize(21), "elapsedMs", "all"))
    }
}
