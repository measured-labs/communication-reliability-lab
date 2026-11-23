package lab.reliability.chain

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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

/** 이 서비스에 넣은 장애를 종류마다 센다. 넣은 수(정답)와 대조하려는 것. */
class FaultInjection(val service: String) {
    private val counts = ConcurrentHashMap<FaultKind, AtomicLong>().apply {
        FaultKind.entries.forEach { put(it, AtomicLong()) }
    }

    fun counts(): Map<String, Long> = FaultKind.entries.associate { it.label to counts.getValue(it).get() }

    fun reset() = counts.values.forEach { it.set(0) }

    internal fun delayIfAsked(ctx: ChainContext) {
        val delay = ctx.faults.firstOrNull { it.kind == FaultKind.DELAY } ?: return
        counts.getValue(FaultKind.DELAY).incrementAndGet()
        Thread.sleep(delay.delayMs!!)
    }

    internal fun errorIfAsked(ctx: ChainContext): Boolean {
        if (ctx.faults.none { it.kind == FaultKind.ERROR }) return false
        counts.getValue(FaultKind.ERROR).incrementAndGet()
        return true
    }

    /**
     * 처리 코드가 마지막 커밋을 마친 뒤 응답을 이것으로 낸다.
     * `fail-after-commit`이 이 서비스를 가리키면 응답 대신 500을 낸다(처리 결과와 무관하게 — 커밋은 이미 끝났다).
     */
    fun respond(response: ResponseEntity<*>): ResponseEntity<*> {
        if (ChainContext.current().faults.none { it.kind == FaultKind.FAIL_AFTER_COMMIT }) return response
        counts.getValue(FaultKind.FAIL_AFTER_COMMIT).incrementAndGet()
        return ResponseEntity.status(500).body(mapOf("fault" to FaultKind.FAIL_AFTER_COMMIT.label, "service" to service))
    }
}

@RestController
class FaultCountController(private val faultInjection: FaultInjection) {
    /** 이 프로세스가 마지막 초기화 뒤 넣은 장애의 수. 예: {"delay":3,"error":0,"fail-after-commit":1} */
    @GetMapping("/faults")
    fun counts() = faultInjection.counts()

    /** 실행 앞의 상태 초기화에서 부른다. */
    @DeleteMapping("/faults")
    fun reset(): Map<String, Long> {
        faultInjection.reset()
        return faultInjection.counts()
    }
}
