package lab.reliability.chain

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** 이 서비스에 넣은 장애를 종류마다 센다. 넣은 수(정답)와 대조하려는 것. */
class FaultInjection(val service: String) {
    private val counts = ConcurrentHashMap<FaultKind, AtomicLong>().apply {
        FaultKind.entries.forEach { put(it, AtomicLong()) }
    }

    fun counts(): Map<String, Long> = FaultKind.entries.associate { it.label to counts.getValue(it).get() }

    fun reset() = counts.values.forEach { it.set(0) }

    internal fun delayIfAsked(ctx: ChainContext) {
        delayAsked(ctx)?.let(Thread::sleep)
    }

    /** `delay`가 이 서비스를 가리키면 세고 그 ms를 돌려준다(아니면 null, 세지 않는다). 자는 것은 부르는 쪽 — task 05의 gRPC 입구가 G3에서 취소를 보며 기다린다. */
    internal fun delayAsked(ctx: ChainContext): Long? {
        val delay = ctx.faults.firstOrNull { it.kind == FaultKind.DELAY } ?: return null
        counts.getValue(FaultKind.DELAY).incrementAndGet()
        return delay.delayMs!!
    }

    internal fun errorIfAsked(ctx: ChainContext): Boolean {
        if (ctx.faults.none { it.kind == FaultKind.ERROR }) return false
        counts.getValue(FaultKind.ERROR).incrementAndGet()
        return true
    }

    /**
     * 처리 코드가 마지막 커밋을 마친 뒤 응답을 이것으로 낸다.
     * `fail-after-commit`이 이 서비스를 가리키면 응답 대신 500을 낸다(처리 결과와 무관하게 — 커밋은 이미 끝났다).
     * task 04: 입구 필터가 이 요청을 끊기로 했으면(X-Drop-Fault) 여기서 연결을 닫는다 — 돌려준 응답은 쓰이지 않고 버려진다.
     */
    fun respond(response: ResponseEntity<*>): ResponseEntity<*> {
        val ctx = ChainContext.current()
        val out = if (ctx.faults.none { it.kind == FaultKind.FAIL_AFTER_COMMIT }) {
            response
        } else {
            counts.getValue(FaultKind.FAIL_AFTER_COMMIT).incrementAndGet()
            ResponseEntity.status(500).body(mapOf("fault" to FaultKind.FAIL_AFTER_COMMIT.label, "service" to service))
        }
        ctx.dropConnection?.invoke()
        return out
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
