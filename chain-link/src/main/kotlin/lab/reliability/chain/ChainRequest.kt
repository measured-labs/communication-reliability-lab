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

    /** task 04: 커밋한 뒤 응답 없이 끊는 장애. 문법은 [DropFaultDirectives.parse]. 사슬을 따라 그대로 넘어간다. */
    const val DROP_FAULT = "X-Drop-Fault"

    /** task 04: 멱등 키(docs/idempotency.md). 사슬을 따라 그대로 넘어간다. 키가 꺼진 서비스는 읽지 않는다. */
    const val IDEMPOTENCY_KEY = "Idempotency-Key"

    /** task 04: 저장한 응답을 돌려줄 때 붙이는 응답 헤더(값 "true"). */
    const val REPLAYED = "Idempotent-Replayed"
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
    /** task 04: 받은 X-Drop-Fault 헤더 그대로(없으면 null) — 아래 층에 그대로 넘긴다. */
    val rawDropFault: String? = null,
    /** task 04: 받은 Idempotency-Key 그대로(없으면 null) — 아래 층에 그대로 넘긴다. 키가 켜진 서비스만 키로 쓴다. */
    val idempotencyKey: String? = null,
) {
    /** 아래 층을 부른 기록(부르지 않았으면 null). [HttpNextLayerCall]이 채우고 입구 필터가 시각 기록에 옮긴다. */
    var call: CallRecord? = null

    /** task 04: 이 요청을 끊어야 하면 입구 필터가 넣는 함수 — [FaultInjection.respond]가 응답을 낼 때 부른다(아니면 null). */
    internal var dropConnection: (() -> Unit)? = null

    /** task 04: 연결을 끊었나(시각 기록의 status를 0으로 남긴다). */
    internal var dropped = false

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
 * 3. (task 04) X-Drop-Fault의 문법이 틀리면 400. 멱등 키가 켜져 있는데 Idempotency-Key가 없거나 비면 400.
 *    이번에 든 것을 끊어야 하면 [ChainContext.dropConnection]을 넣는다 — 끊는 것은 [FaultInjection.respond].
 * 4. (task 04-b) 한도가 켜져 있으면([OverloadLimit]) 1~3의 헤더 검사를 지난 호출마다 3의 끊김 판정보다 먼저 창에 넣고, 한도를
 *    넘으면 처리하지 않고 503 — 거절한 호출에는 다른 장애(끊김 포함)를 판정하지도 세지도 않는다.
 *    거절한 호출도 시각 기록에 남는다(status 503). 꺼져 있으면 한도를 부르지 않는다(task 01~04의 경로 그대로).
 */
class ChainEntryFilter(
    private val faultInjection: FaultInjection,
    private val inFlight: InFlightRequests,
    private val timings: RequestTimings,
    private val attemptFaults: AttemptFaultInjection,
    private val dropFaults: DropFaultInjection = DropFaultInjection(),
    private val idempotency: Idempotency? = null,
    private val overload: OverloadLimit = OverloadLimit(false, 0, 0),
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
        val rawDropFault = request.getHeader(ChainHeaders.DROP_FAULT)
        val dropFault = try {
            DropFaultDirectives.parse(rawDropFault).firstOrNull { it.service == faultInjection.service }
        } catch (e: IllegalArgumentException) {
            reject(response, 400, """{"error":"bad ${ChainHeaders.DROP_FAULT}: ${e.message?.replace("\"", "'")}"}""")
            return
        }
        val idempotencyKey = request.getHeader(ChainHeaders.IDEMPOTENCY_KEY)
        if (idempotency?.enabled == true && idempotencyKey.isNullOrBlank()) {
            idempotency.counters.add(IdempotencyCounters.MISSING_KEY)
            reject(response, 400, """{"error":"missing ${ChainHeaders.IDEMPOTENCY_KEY}"}""")
            return
        }
        val ctx = ChainContext(
            requestId, rawFault, directives.filter { it.service == faultInjection.service }, rawAttemptFault, rawDropFault, idempotencyKey,
        )
        // task 04-b: 한도를 끊김 장애보다 먼저 판정한다 — 한도에서 거절한 호출은 끊김의 든 횟수를 쓰지 않는다(docs/measurement.md 10-1).
        // 꺼져 있으면 한도를 부르지 않고, 끊김을 판정한 뒤 시계를 읽는다(task 01~04의 차례 그대로).
        val admission = if (overload.enabled) overload.arrive() else null
        if (admission?.rejected != true && dropFaults.decide(requestId, dropFault)) {
            ctx.dropConnection = {
                closeWithoutResponse(response)
                dropFaults.counted()
                ctx.dropped = true
            }
        }
        // 시각 기록: 입구에 든 때(지연 장애로 자기 전)부터 나갈 때까지. 예외로 끝나도 남긴다(finally).
        // task 04-b: 한도가 켜져 있으면 든 시각은 한도가 잠금 안에서 읽어 판정에 쓴 그 값이다(docs/measurement.md 10-1).
        val start = admission?.atUs ?: epochMicros()
        try {
            ChainContext.with(ctx) {
                // task 04-b: 창 안에 받은 호출이 한도를 넘었다 — 처리하지 않고 503(다른 장애는 판정하지 않는다)
                if (admission?.rejected == true) {
                    reject(response, 503, """{"fault":"overload","service":"${faultInjection.service}"}""")
                    return@with
                }
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
            // 끊은 요청은 응답을 보내지 않았으므로 status 0(task 04 — docs/measurement.md 9-1)
            timings.add(TimingRecord(requestId, start, epochMicros(), if (ctx.dropped) 0 else response.status, ctx.call))
        }
    }

    private fun reject(response: HttpServletResponse, status: Int, json: String) {
        response.status = status
        response.contentType = "application/json"
        response.writer.write(json)
    }
}
