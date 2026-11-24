package lab.reliability.chain

// task 03: 시도마다 넣는 실패(X-Attempt-Fault)의 해석. 서비스와 분석기가 함께 쓴다(Spring에 기대지 않는다).
// 문법과 동작의 정본: docs/measurement.md 8-1.

sealed interface AttemptFault {
    val service: String

    /** 그 requestId로 그 서비스에 처음 [n]번 든 시도는 처리하지 않고 500. */
    data class FailFirst(override val service: String, val n: Int) : AttemptFault

    /** 처음 [n]번 든 시도는 [delayMs]만큼 잔 뒤 처리한다. */
    data class SlowFirst(override val service: String, val n: Int, val delayMs: Long) : AttemptFault

    /** 입구에 든 시각(벽시계 ms)이 [fromMs, toMs)이면 처리하지 않고 500. */
    data class Outage(override val service: String, val fromMs: Long, val toMs: Long) : AttemptFault

    /** 서비스의 카운터와 분석기의 대조에 쓰는 종류 이름. */
    val kind: String
        get() = when (this) {
            is FailFirst -> "fail-first"
            is SlowFirst -> "slow-first"
            is Outage -> "outage"
        }

    companion object {
        val KINDS = listOf("fail-first", "slow-first", "outage")
    }
}

object AttemptFaultDirectives {
    private val POSITIVE = Regex("[1-9][0-9]*")
    private val SLOW = Regex("([1-9][0-9]*)@([1-9][0-9]*)")
    private val WINDOW = Regex("([0-9]+)-([0-9]+)")

    /**
     * `X-Attempt-Fault: <service>:<kind>=<value>[,<service>:<kind>=<value>...]`
     * - `fail-first=<n>`(n ≥ 1) · `slow-first=<n>@<ms>`(둘 다 ≥ 1) · `outage=<fromMs>-<toMs>`(벽시계 ms, from < to)
     * 빈 값·없음 = 없음. 한 서비스를 가리키는 지시는 하나만. 그 밖의 꼴은 틀린 헤더다.
     */
    fun parse(raw: String?): List<AttemptFault> {
        if (raw.isNullOrBlank()) return emptyList()
        val directives = raw.split(',').map { token ->
            val parts = token.trim().split(':')
            require(parts.size == 2) { "'$token' is not <service>:<kind>=<value>" }
            val service = parts[0].trim()
            require(service in FaultDirectives.SERVICES) { "unknown service '$service'" }
            val kv = parts[1].trim().split('=')
            require(kv.size == 2) { "'${parts[1]}' is not <kind>=<value>" }
            val value = kv[1]
            when (kv[0]) {
                "fail-first" -> {
                    require(POSITIVE.matches(value)) { "fail-first '$value' is not a positive integer" }
                    AttemptFault.FailFirst(service, value.toInt())
                }
                "slow-first" -> {
                    val m = SLOW.matchEntire(value) ?: throw IllegalArgumentException("slow-first '$value' is not <n>@<ms>")
                    AttemptFault.SlowFirst(service, m.groupValues[1].toInt(), m.groupValues[2].toLong())
                }
                "outage" -> {
                    val m = WINDOW.matchEntire(value) ?: throw IllegalArgumentException("outage '$value' is not <fromMs>-<toMs>")
                    val from = m.groupValues[1].toLong()
                    val to = m.groupValues[2].toLong()
                    require(from < to) { "outage '$value': from must be < to" }
                    AttemptFault.Outage(service, from, to)
                }
                else -> throw IllegalArgumentException("unknown kind '${kv[0]}'")
            }
        }
        val repeated = directives.groupBy { it.service }.filterValues { it.size > 1 }.keys
        require(repeated.isEmpty()) { "more than one directive for $repeated" }
        return directives
    }
}
