package lab.reliability.chain

// 서비스와 분석기가 함께 쓰는 장애 헤더의 해석. Spring에 기대지 않는다(분석기가 그대로 쓴다).

enum class FaultKind(val label: String) {
    /** 처리 전에 정한 시간(ms)만큼 늦춘다. */
    DELAY("delay"),

    /** 처리하지 않고 500을 낸다(이 서비스에 행이 생기지 않는다). */
    ERROR("error"),

    /** 처리하고 커밋한 뒤, 응답 대신 500을 낸다(행은 남는다). */
    FAIL_AFTER_COMMIT("fail-after-commit"),
    ;

    companion object {
        fun of(label: String): FaultKind =
            entries.firstOrNull { it.label == label } ?: throw IllegalArgumentException("unknown kind '$label'")
    }
}

data class FaultDirective(val service: String, val kind: FaultKind, val delayMs: Long? = null)

object FaultDirectives {
    val SERVICES = setOf("order", "inventory", "payment")

    /**
     * `X-Fault: <service>:<kind>[=<ms>][,<service>:<kind>[=<ms>]...]`
     * - service: order | inventory | payment
     * - kind: `delay=<ms>`(양의 정수) | `error` | `fail-after-commit`
     * 빈 값·없음 = 장애 없음. 그 밖의 꼴, 같은 서비스에 같은 kind 두 번은 틀린 헤더다.
     */
    fun parse(raw: String?): List<FaultDirective> {
        if (raw.isNullOrBlank()) return emptyList()
        val directives = raw.split(',').map { token ->
            val parts = token.trim().split(':')
            require(parts.size == 2) { "'$token' is not <service>:<kind>" }
            val service = parts[0].trim()
            require(service in SERVICES) { "unknown service '$service'" }
            val kv = parts[1].trim().split('=')
            val kind = FaultKind.of(kv[0])
            when (kind) {
                FaultKind.DELAY -> {
                    require(kv.size == 2) { "delay needs =<ms>" }
                    val ms = kv[1].toLongOrNull()
                    require(ms != null && ms > 0) { "delay '${kv[1]}' is not a positive integer" }
                    FaultDirective(service, kind, ms)
                }
                else -> {
                    require(kv.size == 1) { "${kind.label} takes no value" }
                    FaultDirective(service, kind)
                }
            }
        }
        val repeated = directives.groupBy { it.service to it.kind }.filterValues { it.size > 1 }.keys
        require(repeated.isEmpty()) { "repeated directive $repeated" }
        return directives
    }
}
