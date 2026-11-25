package lab.reliability.chain

// task 04-b: 부하에 따라 실패하는 장애의 규칙 — 미끄러지는 창의 한도. 서비스(chain-link의 OverloadLimit)와 분석기가 같이 쓴다
// (Spring에 기대지 않는다). 규칙의 정본: docs/measurement.md 10-1.

/**
 * 미끄러지는 창 하나. 시각 t(μs)에 든 호출은, (t − [windowUs], t] 동안 든 호출의 수(이 호출 포함, **거절한 호출 포함**)가
 * [limit]보다 크면 거절이다. 든 시각은 차례대로(앞 호출보다 이르지 않게) 넣는다.
 */
class OverloadWindow(val limit: Int, val windowUs: Long) {
    init {
        require(limit >= 1) { "limit $limit < 1" }
        require(windowUs >= 1) { "windowUs $windowUs < 1" }
    }

    private val arrivals = ArrayDeque<Long>()
    private var last = Long.MIN_VALUE

    /** 시각 [atUs]에 든 호출 하나를 넣고, 거절이면 true. [atUs]가 앞 호출보다 이르면 IllegalArgumentException. */
    fun arrive(atUs: Long): Boolean {
        require(atUs >= last) { "든 시각 $atUs 가 앞 호출($last)보다 이르다" }
        last = atUs
        while (arrivals.isNotEmpty() && arrivals.first() <= atUs - windowUs) arrivals.removeFirst()
        arrivals.addLast(atUs)
        return arrivals.size > limit
    }

    /** 마지막으로 넣은 호출의 창 (t − windowUs, t] 안의 호출 수(넣은 것이 없으면 0). */
    fun size(): Int = arrivals.size
}

object OverloadRule {
    /**
     * 오름차순의 든 시각들 → 같은 차례의 거절 여부. 빈 창에서 시작한다.
     * 오름차순이 아니거나 [limit] < 1, [windowUs] < 1이면 IllegalArgumentException.
     * 같은 시각의 호출 여럿은 차례를 바꿔도 그 묶음 안의 거절 **수**가 같다(어느 것이 거절인지는 차례에 따른다).
     */
    fun rejections(arrivalsUs: List<Long>, limit: Int, windowUs: Long): List<Boolean> {
        val window = OverloadWindow(limit, windowUs)
        return arrivalsUs.map { window.arrive(it) }
    }
}
