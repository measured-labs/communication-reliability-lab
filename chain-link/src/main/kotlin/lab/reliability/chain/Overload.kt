package lab.reliability.chain

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

// task 04-b: 부하에 따라 실패하는 장애 — 이 서비스가 창 안에 받은 호출이 한도를 넘으면 처리하지 않고 503.
// 규칙(OverloadWindow)은 fault-header에 있다(분석기가 같은 규칙을 다시 적용한다). 정본: docs/measurement.md 10절.

/** `chain.overload.*` — 기본은 꺼짐(chain-link.yml). 꺼져 있으면 limit·window는 검사하지 않고 쓰지 않는다. */
@ConfigurationProperties("chain.overload")
data class OverloadSettings(val enabled: Boolean, val limit: Int, val window: Duration)

/**
 * 서비스 쪽의 한도. [arrive]는 잠금 안에서 시계를 읽어(앞 값보다 이르면 앞 값 — 거꾸로 가지 않게) 그 시각으로 판정하고
 * 그 시각을 돌려준다 — 입구 필터가 같은 값을 시각 기록의 startUs로 남긴다. 그래서 판정의 차례 = startUs의 오름차순이다.
 * 꺼져 있으면([enabled] false) 입구 필터가 부르지 않는다.
 */
class OverloadLimit(
    val enabled: Boolean,
    val limit: Int,
    val windowMs: Long,
    private val clock: () -> Long = ::epochMicros,
) {
    /** 든 시각(μs)과 거절 여부. */
    data class Admission(val atUs: Long, val rejected: Boolean)

    init {
        if (enabled) {
            require(limit >= 1) { "chain.overload.limit $limit < 1" }
            require(windowMs >= 1) { "chain.overload.window ${windowMs}ms < 1ms" }
        }
    }

    private var window = if (enabled) OverloadWindow(limit, windowMs * 1000) else null
    private var last = Long.MIN_VALUE
    private var arrivals = 0L
    private var rejected = 0L

    @Synchronized
    fun arrive(): Admission {
        val w = checkNotNull(window) { "한도가 꺼져 있다 — 입구 필터가 부르지 않는다" }
        val now = maxOf(clock(), last)
        last = now
        val r = w.arrive(now)
        arrivals++
        if (r) rejected++
        return Admission(now, r)
    }

    /** 마지막 [reset] 뒤 든 호출 수와 거절 수. 예: {"arrivals":1250,"rejected":484} — 꺼져 있으면 늘 0. */
    @Synchronized
    fun counts(): Map<String, Long> = linkedMapOf("arrivals" to arrivals, "rejected" to rejected)

    /** 센 수와 창을 비운다(실행 앞의 상태 초기화). 시계의 앞 값은 남긴다(거꾸로 가지 않게). */
    @Synchronized
    fun reset() {
        arrivals = 0
        rejected = 0
        if (enabled) window = OverloadWindow(limit, windowMs * 1000)
    }

    /** `/settings`의 `overload`. 꺼짐: {"enabled":false}. */
    fun settings(): Map<String, Any?> =
        if (!enabled) mapOf("enabled" to false) else linkedMapOf("enabled" to true, "limit" to limit, "windowMs" to windowMs, "rule" to "sliding-window")

    companion object {
        fun of(settings: OverloadSettings) = OverloadLimit(settings.enabled, settings.limit, settings.window.toMillis())
    }
}

@RestController
class OverloadController(private val limit: OverloadLimit) {
    /** 마지막 DELETE 뒤 든 호출 수와 거절 수. */
    @GetMapping("/overload")
    fun counts() = limit.counts()

    @DeleteMapping("/overload")
    fun reset(): Map<String, Long> {
        limit.reset()
        return limit.counts()
    }
}
