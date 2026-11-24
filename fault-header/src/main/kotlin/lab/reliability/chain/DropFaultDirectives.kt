package lab.reliability.chain

// task 04: 커밋한 뒤 응답 없이 연결을 끊는 장애(X-Drop-Fault)의 해석. 서비스와 분석기가 함께 쓴다(Spring에 기대지 않는다).
// 문법과 동작의 정본: docs/measurement.md 9-1.

/** 그 requestId로 그 서비스에 처음 [n]번 든 것은 처리하고 커밋한 뒤 응답 없이 연결을 끊는다. */
data class DropFault(val service: String, val n: Int) {
    companion object {
        /** 서비스의 카운터(`GET /drop-faults`)와 분석기의 대조에 쓰는 종류 이름 — 종류는 이것 하나다. */
        const val KIND = "drop-after-commit"
    }
}

object DropFaultDirectives {
    private val POSITIVE = Regex("[1-9][0-9]*")

    /**
     * `X-Drop-Fault: <service>:drop-after-commit=<n>[,<service>:drop-after-commit=<n>...]`(n ≥ 1).
     * 빈 값·공백뿐·없음 = 없음. 한 서비스를 가리키는 지시는 하나만. 공백은 쉼표로 나눈 지시의 앞뒤와 `:`의 앞뒤에만 받는다
     * (`X-Attempt-Fault`와 같은 규칙). 그 밖의 꼴은 틀린 헤더다(IllegalArgumentException).
     */
    fun parse(raw: String?): List<DropFault> {
        if (raw.isNullOrBlank()) return emptyList()
        val directives = raw.split(',').map { token ->
            val parts = token.trim().split(':')
            require(parts.size == 2) { "'$token' is not <service>:${DropFault.KIND}=<n>" }
            val service = parts[0].trim()
            require(service in FaultDirectives.SERVICES) { "unknown service '$service'" }
            val kv = parts[1].trim().split('=')
            require(kv.size == 2) { "'${parts[1]}' is not ${DropFault.KIND}=<n>" }
            require(kv[0] == DropFault.KIND) { "unknown kind '${kv[0]}'" }
            require(POSITIVE.matches(kv[1])) { "${DropFault.KIND} '${kv[1]}' is not a positive integer" }
            DropFault(service, kv[1].toInt())
        }
        val repeated = directives.groupBy { it.service }.filterValues { it.size > 1 }.keys
        require(repeated.isEmpty()) { "more than one directive for $repeated" }
        return directives
    }
}
