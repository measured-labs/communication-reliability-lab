package lab.reliability.chain

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** 입구에서 시도마다 넣는 실패를 정한 결과. */
sealed interface AttemptAction {
    data object None : AttemptAction
    data object Fail : AttemptAction
    data class Sleep(val ms: Long) : AttemptAction
}

/**
 * task 03: `X-Attempt-Fault`가 이 서비스를 가리킬 때 시도마다 실패를 넣고 종류마다 센다(docs/measurement.md 8-1).
 * 든 횟수는 그 헤더가 이 서비스에 `fail-first`·`slow-first`를 줄 때만 requestId마다 센다(헤더가 없으면 아무것도 쌓지 않는다).
 * 단순화: 든 횟수를 상한 없이 메모리에 둔다 — 실행 하나(수천 건)마다 DELETE /attempt-faults로 비우는 실험용.
 */
class AttemptFaultInjection(val service: String) {
    private val arrivals = ConcurrentHashMap<String, AtomicInteger>()
    private val counts = AttemptFault.KINDS.associateWith { AtomicLong() }

    fun counts(): Map<String, Long> = AttemptFault.KINDS.associateWith { counts.getValue(it).get() }

    fun reset() {
        arrivals.clear()
        counts.values.forEach { it.set(0) }
    }

    /** [startUs] = 시각 기록의 start와 같은 값(구간 장애의 판정을 분석기가 같은 수로 다시 하게). */
    internal fun decide(requestId: String, directive: AttemptFault?, startUs: Long): AttemptAction {
        val action = when (directive) {
            null -> AttemptAction.None
            is AttemptFault.FailFirst ->
                if (arrival(requestId) <= directive.n) AttemptAction.Fail else AttemptAction.None
            is AttemptFault.SlowFirst ->
                if (arrival(requestId) <= directive.n) AttemptAction.Sleep(directive.delayMs) else AttemptAction.None
            is AttemptFault.Outage ->
                if (startUs >= directive.fromMs * 1000 && startUs < directive.toMs * 1000) AttemptAction.Fail else AttemptAction.None
        }
        if (action != AttemptAction.None) counts.getValue(directive!!.kind).incrementAndGet()
        return action
    }

    private fun arrival(requestId: String): Int = arrivals.computeIfAbsent(requestId) { AtomicInteger() }.incrementAndGet()
}

@RestController
class AttemptFaultController(private val injection: AttemptFaultInjection) {
    /** 마지막 DELETE 뒤 넣은 수. 예: {"fail-first":3,"slow-first":0,"outage":0} */
    @GetMapping("/attempt-faults")
    fun counts() = injection.counts()

    /** 넣은 수와 requestId마다 든 횟수를 비운다. */
    @DeleteMapping("/attempt-faults")
    fun reset(): Map<String, Long> {
        injection.reset()
        return injection.counts()
    }
}
