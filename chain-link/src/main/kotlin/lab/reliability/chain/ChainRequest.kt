package lab.reliability.chain

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.atomic.AtomicInteger

object ChainHeaders {
    /** 부하 도구가 요청마다 만든 id. 세 서비스의 행에 그대로 남는다. */
    const val REQUEST_ID = "X-Request-Id"

    /** 장애 주입 지시. 문법은 [FaultDirectives.parse]. 사슬을 따라 그대로 넘어간다. */
    const val FAULT = "X-Fault"

    /** task 03: 시도마다 넣는 실패. 문법은 [AttemptFaultDirectives.parse]. 사슬을 따라 그대로 넘어간다. */
    const val ATTEMPT_FAULT = "X-Attempt-Fault"
}

/** 세 서비스가 받는 요청 본문(주문 → 재고 → 결제 모두 금액 하나). */
data class AmountRequest(val amount: Long)

/**
 * 요청 하나의 문맥. 필터가 만들고, 처리 코드와 아래 층 호출([HttpNextLayerCall])이 읽는다.
 * 요청 하나 = 스레드 하나(플랫폼이든 가상이든)라서 ThreadLocal로 둔다.
 */
class ChainContext(
    val requestId: String,
    /** 받은 X-Fault 헤더 그대로 — 아래 층에 그대로 넘긴다. */
    val rawFault: String?,
    /** 이 서비스를 가리키는 지시만. */
    val faults: List<FaultDirective>,
    /** 받은 X-Attempt-Fault 헤더 그대로(없으면 null) — 아래 층에 그대로 넘긴다. */
    val rawAttemptFault: String? = null,
) {
    /** 아래 층을 부른 기록(부르지 않았으면 null). [HttpNextLayerCall]이 채우고 입구 필터가 시각 기록에 옮긴다. */
    var call: CallRecord? = null

    companion object {
        private val holder = ThreadLocal<ChainContext>()

        fun current(): ChainContext =
            holder.get() ?: error("요청 문맥이 없다 — ChainEntryFilter를 지나지 않은 경로에서 불렸다")

        internal fun <T> with(ctx: ChainContext, block: () -> T): T {
            holder.set(ctx)
            try {
                return block()
            } finally {
                holder.remove()
            }
        }
    }
}

/**
 * 처리 중인 요청 수 — 업무 입구에 들어와 아직 끝나지 않은 것(지연 장애로 자고 있는 것 포함).
 * 실행 스크립트가 부하 뒤 세 서비스의 이 값이 모두 0이 된 것을 보고 DB를 내보낸다(고정된 기다림에 기대지 않으려고).
 */
class InFlightRequests {
    private val count = AtomicInteger()

    fun current(): Int = count.get()

    internal fun <T> track(block: () -> T): T {
        count.incrementAndGet()
        try {
            return block()
        } finally {
            count.decrementAndGet()
        }
    }
}

@RestController
class InFlightController(private val inFlight: InFlightRequests) {
    /** 예: {"inflight":0} */
    @GetMapping("/inflight")
    fun inflight() = mapOf("inflight" to inFlight.current())
}

/**
 * 업무 입구(`chain.entry-path`)에만 걸린다. 하는 일:
 * 1. X-Request-Id가 없거나 X-Fault의 문법이 틀리면 400 — 측정이 조용히 틀어지지 않게.
 * 2. 이 서비스를 가리키는 `delay`는 처리 전에 잔다, `error`는 처리하지 않고 500을 낸다(둘 다 센다).
 * `fail-after-commit`은 처리 코드가 커밋한 뒤 [FaultInjection.respond]에서 낸다.
 */
class ChainEntryFilter(
    private val faultInjection: FaultInjection,
    private val inFlight: InFlightRequests,
    private val timings: RequestTimings,
    private val attemptFaults: AttemptFaultInjection,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) =
        inFlight.track { handle(request, response, chain) }

    private fun handle(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val requestId = request.getHeader(ChainHeaders.REQUEST_ID)
        if (requestId.isNullOrBlank()) {
            reject(response, 400, """{"error":"missing ${ChainHeaders.REQUEST_ID}"}""")
            return
        }
        val rawFault = request.getHeader(ChainHeaders.FAULT)
        val directives = try {
            FaultDirectives.parse(rawFault)
        } catch (e: IllegalArgumentException) {
            reject(response, 400, """{"error":"bad ${ChainHeaders.FAULT}: ${e.message?.replace("\"", "'")}"}""")
            return
        }
        val rawAttemptFault = request.getHeader(ChainHeaders.ATTEMPT_FAULT)
        val attemptFault = try {
            AttemptFaultDirectives.parse(rawAttemptFault).firstOrNull { it.service == faultInjection.service }
        } catch (e: IllegalArgumentException) {
            reject(response, 400, """{"error":"bad ${ChainHeaders.ATTEMPT_FAULT}: ${e.message?.replace("\"", "'")}"}""")
            return
        }
        val ctx = ChainContext(requestId, rawFault, directives.filter { it.service == faultInjection.service }, rawAttemptFault)
        // 시각 기록: 입구에 든 때(지연 장애로 자기 전)부터 나갈 때까지. 예외로 끝나도 남긴다(finally).
        val start = epochMicros()
        try {
            ChainContext.with(ctx) {
                // task 03: 시도마다 넣는 실패(헤더가 없으면 None — task 01·02의 경로 그대로)
                when (val action = attemptFaults.decide(requestId, attemptFault, start)) {
                    AttemptAction.Fail -> {
                        reject(response, 500, """{"fault":"${attemptFault!!.kind}","service":"${faultInjection.service}"}""")
                        return@with
                    }
                    is AttemptAction.Sleep -> Thread.sleep(action.ms)
                    AttemptAction.None -> Unit
                }
                faultInjection.delayIfAsked(ctx)
                if (faultInjection.errorIfAsked(ctx)) {
                    reject(response, 500, """{"fault":"error","service":"${faultInjection.service}"}""")
                } else {
                    chain.doFilter(request, response)
                }
            }
        } finally {
            timings.add(TimingRecord(requestId, start, epochMicros(), response.status, ctx.call))
        }
    }

    private fun reject(response: HttpServletResponse, status: Int, json: String) {
        response.status = status
        response.contentType = "application/json"
        response.writer.write(json)
    }
}
