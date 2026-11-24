package lab.reliability.chain

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong

// task 03: 아래 층 호출의 재시도 — 직접 짠 최소 코드(라이브러리 없음). 규칙의 정본: docs/retry.md.

/** 다시 시도하는 실패의 갈래. */
enum class RetryOn(val label: String) {
    /** 아래 층이 처리하지 않았다고 볼 수 있는 일시적 실패: 5xx 응답(504 밖), 연결 거절, 연결 타임아웃. */
    FAILED("failed"),

    /** 처리했는지 모름: 요청 타임아웃, 504, 연결이 중간에 끊김. */
    UNKNOWN("unknown"),
}

enum class Backoff(val label: String) { NONE("none"), FIXED("fixed"), EXPONENTIAL("exponential"), EXPONENTIAL_JITTER("exponential-jitter") }

/** 시도 하나 뒤의 결정(시도 기록의 `decision`). */
enum class RetryDecision(val label: String) {
    SUCCESS("success"),
    NOT_RETRYABLE("not-retryable"),
    RETRY("retry"),
    MAX_ATTEMPTS("max-attempts"),
    BUDGET_DENIED("budget-denied"),
}

/** `chain.retry.*` — 기본은 모두 꺼짐(chain-link.yml). 꺼져 있으면 감싸개를 만들지 않는다. */
@ConfigurationProperties("chain.retry")
data class RetrySettings(
    val enabled: Boolean,
    /** 처음 시도를 센다(1 = 재시도 없음). */
    val maxAttempts: Int,
    val retryOn: Set<RetryOn>,
    val backoff: Backoff,
    val baseDelay: Duration,
    val maxDelay: Duration,
    val budget: BudgetSettings,
) {
    data class BudgetSettings(val enabled: Boolean, val capacity: BigDecimal, val depositPerCall: BigDecimal)

    fun policy() = RetryPolicy(maxAttempts, retryOn, backoff, baseDelay.toMillis(), maxDelay.toMillis())

    fun budgetOrNull(): RetryBudget? = if (budget.enabled) RetryBudget(budget.capacity, budget.depositPerCall) else null
}

/** 순수한 규칙: 어느 실패에 다시 시도하나, 다음 시도 앞의 간격. */
class RetryPolicy(
    val maxAttempts: Int,
    val retryOn: Set<RetryOn>,
    val backoff: Backoff,
    val baseDelayMs: Long,
    val maxDelayMs: Long,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts $maxAttempts < 1" }
        require(baseDelayMs >= 0 && maxDelayMs >= 0) { "간격이 음수다" }
        if (backoff == Backoff.EXPONENTIAL || backoff == Backoff.EXPONENTIAL_JITTER) {
            require(maxDelayMs >= baseDelayMs) { "maxDelay $maxDelayMs < baseDelay $baseDelayMs" }
        }
    }

    /** 결과의 갈래. 성공이거나 다시 시도할 갈래가 아니면(4xx 등) null. */
    fun classOf(outcome: LayerOutcome): RetryOn? = when (outcome) {
        LayerOutcome.Completed -> null
        is LayerOutcome.Unknown -> RetryOn.UNKNOWN
        is LayerOutcome.Failed -> {
            val status = HTTP_STATUS.matchEntire(outcome.reason)?.groupValues?.get(1)?.toInt()
            when {
                status != null && status in 500..599 -> RetryOn.FAILED
                outcome.reason == "CONNECT_REFUSED" || outcome.reason == "CONNECT_TIMEOUT" -> RetryOn.FAILED
                else -> null
            }
        }
    }

    /** 지수 간격의 위 끝 E(n) = min(maxDelay, baseDelay · 2^(n−2)), n = 시작할 시도의 번호(≥ 2). */
    fun exponentialCeilingMs(nextAttempt: Int): Long {
        require(nextAttempt >= 2) { "nextAttempt $nextAttempt < 2" }
        var e = baseDelayMs
        repeat(nextAttempt - 2) {
            if (e >= maxDelayMs) return maxDelayMs
            e *= 2
        }
        return minOf(e, maxDelayMs)
    }

    /** 시도 [nextAttempt](≥ 2) 앞에서 기다릴 ms. [u]는 [0, 1) — `exponential-jitter`에서만 쓴다. */
    fun delayBeforeMs(nextAttempt: Int, u: Double): Long {
        require(nextAttempt >= 2) { "nextAttempt $nextAttempt < 2" }
        return when (backoff) {
            Backoff.NONE -> 0
            Backoff.FIXED -> baseDelayMs
            Backoff.EXPONENTIAL -> exponentialCeilingMs(nextAttempt)
            Backoff.EXPONENTIAL_JITTER -> {
                require(u >= 0.0 && u < 1.0) { "u $u 는 [0, 1)이 아니다" }
                Math.floor(u * exponentialCeilingMs(nextAttempt)).toLong()
            }
        }
    }

    /** `/settings`의 `retry`(떠 있는 감싸개의 값). */
    fun settings(budget: RetryBudget?): Map<String, Any?> = linkedMapOf(
        "enabled" to true,
        "maxAttempts" to maxAttempts,
        "retryOn" to retryOn.map { it.label }.sorted(),
        "backoff" to backoff.label,
        "baseDelayMs" to baseDelayMs,
        "maxDelayMs" to maxDelayMs,
        "budget" to (budget?.settings() ?: mapOf("enabled" to false)),
    )

    private companion object {
        val HTTP_STATUS = Regex("HTTP_([0-9]{3})")
    }
}

/**
 * 재시도 예산 — 부르는 쪽 프로세스 하나의 토큰 버킷. 처음에 [capacity]개 찬 채로 시작한다.
 * 호출(처음 시도) 하나마다 [deposit]으로 [depositPerCall]개를 넣고(용량을 넘지 않게), 재시도 하나마다 [tryWithdraw]로 1개를 뺀다.
 * 1개 미만이면 빼지 않고 false. 소수는 BigDecimal로 정확히 더한다(0.1을 열 번 넣으면 정확히 1).
 */
class RetryBudget(val capacity: BigDecimal, val depositPerCall: BigDecimal) {
    init {
        require(capacity.signum() >= 0 && depositPerCall.signum() >= 0) { "예산의 용량·넣는 양이 음수다" }
    }

    private var tokens: BigDecimal = capacity

    @Synchronized
    fun tokens(): BigDecimal = tokens

    @Synchronized
    fun deposit() {
        tokens = (tokens + depositPerCall).min(capacity)
    }

    @Synchronized
    fun tryWithdraw(): Boolean {
        if (tokens < BigDecimal.ONE) return false
        tokens -= BigDecimal.ONE
        return true
    }

    fun settings(): Map<String, Any?> = linkedMapOf(
        "enabled" to true,
        "capacity" to capacity.stripTrailingZeros().toPlainString(),
        "depositPerCall" to depositPerCall.stripTrailingZeros().toPlainString(),
    )
}

/** 시도 하나의 기록(`GET /attempts`). 형식: docs/measurement.md 8-2. */
data class AttemptRecord(
    val requestId: String,
    /** 이 프로세스에서 감싼 호출 하나마다 1부터 늘어나는 번호(같은 requestId로 여러 번 불려도 호출마다 다르다). */
    val callId: Long,
    /** 1부터. */
    val attempt: Int,
    val startUs: Long,
    val endUs: Long,
    /** [LayerOutcome.label] 꼴: "Completed" · "Failed:<까닭>" · "Unknown:<까닭>". */
    val outcome: String,
    /** 이 시도 앞에서 기다리기로 한 간격(ms). 첫 시도는 0. */
    val delayBeforeMs: Long,
    val decision: String,
)

/**
 * 아래 층 호출을 감싸 재시도한다. 테스트 입구: [requestId]·[sleeper]·[random]·[clock]·[record]를 바꿔 끼운다(docs/retry.md 6절).
 * - [random]은 `exponential-jitter`일 때 재시도마다 한 번만 부른다(다른 간격에서는 부르지 않는다).
 * - [sleeper]는 재시도마다 한 번, 계획한 간격(0 포함)으로 부른다.
 * - [clock]은 시도마다 시작과 끝에 한 번씩(μs).
 */
class RetryingNextLayerCall(
    val delegate: NextLayerCall,
    val policy: RetryPolicy,
    val budget: RetryBudget?,
    private val record: (AttemptRecord) -> Unit,
    private val requestId: () -> String = { ChainContext.current().requestId },
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val random: () -> Double = { ThreadLocalRandom.current().nextDouble() },
    private val clock: () -> Long = ::epochMicros,
) : NextLayerCall {
    private val callIds = AtomicLong()

    override fun call(amount: Long): LayerOutcome {
        val id = requestId()
        val callId = callIds.incrementAndGet()
        budget?.deposit()
        var attempt = 1
        var delay = 0L
        while (true) {
            val start = clock()
            val outcome = delegate.call(amount)
            val end = clock()
            val decision = decide(outcome, attempt)
            record(AttemptRecord(id, callId, attempt, start, end, outcome.label(), delay, decision.label))
            if (decision != RetryDecision.RETRY) return outcome
            attempt++
            delay = policy.delayBeforeMs(attempt, if (policy.backoff == Backoff.EXPONENTIAL_JITTER) random() else 0.0)
            sleeper(delay)
        }
    }

    /** 차례: 성공 → 다시 시도할 갈래가 아님 → 시도 상한 → 예산(이때만 토큰을 뺀다) → 재시도. */
    private fun decide(outcome: LayerOutcome, attempt: Int): RetryDecision {
        if (outcome == LayerOutcome.Completed) return RetryDecision.SUCCESS
        val cls = policy.classOf(outcome)
        if (cls == null || cls !in policy.retryOn) return RetryDecision.NOT_RETRYABLE
        if (attempt >= policy.maxAttempts) return RetryDecision.MAX_ATTEMPTS
        if (budget != null && !budget.tryWithdraw()) return RetryDecision.BUDGET_DENIED
        return RetryDecision.RETRY
    }
}

/** 단순화: 상한 없이 메모리에 쌓는다 — 실행 하나(수천 건)마다 DELETE /attempts로 비우는 실험용. */
class AttemptLog {
    private val records = ConcurrentLinkedQueue<AttemptRecord>()

    fun add(record: AttemptRecord) {
        records.add(record)
    }

    fun snapshot(): List<AttemptRecord> = records.toList()

    fun reset() = records.clear()
}

/**
 * task 04(ledger R03-7): 떠 있는 감싸개의 예산 토큰 — 실행 스크립트가 부하 직전에 읽어 실행 기록에 남긴다(예산은 실행 사이에 비우지 않는다).
 * 예산이 켜져 있으면 {"enabled":true,"tokens":"10"}(끝의 0을 뺀 10진 문자열), 아니면 {"enabled":false}(재시도가 꺼졌거나 맨 아래 층 포함).
 */
@RestController
class RetryBudgetController(private val nextLayerCall: ObjectProvider<NextLayerCall>) {
    @GetMapping("/retry-budget")
    fun budget(): Map<String, Any?> {
        val budget = (nextLayerCall.ifAvailable as? RetryingNextLayerCall)?.budget ?: return mapOf("enabled" to false)
        return linkedMapOf("enabled" to true, "tokens" to budget.tokens().stripTrailingZeros().toPlainString())
    }
}

@RestController
class AttemptController(private val log: AttemptLog) {
    /** 마지막 DELETE 뒤의 시도 기록(JSON 배열). 재시도가 꺼진 서비스와 맨 아래 층은 늘 []. */
    @GetMapping("/attempts")
    fun attempts() = log.snapshot()

    @DeleteMapping("/attempts")
    fun reset(): Map<String, Int> {
        log.reset()
        return mapOf("attempts" to log.snapshot().size)
    }
}
