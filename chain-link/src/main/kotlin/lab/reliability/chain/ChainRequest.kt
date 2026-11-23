package lab.reliability.chain

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

object ChainHeaders {
    /** 부하 도구가 요청마다 만든 id. 세 서비스의 행에 그대로 남는다. */
    const val REQUEST_ID = "X-Request-Id"

    /** 장애 주입 지시. 문법은 [FaultDirectives.parse]. 사슬을 따라 그대로 넘어간다. */
    const val FAULT = "X-Fault"
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
) {
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
 * 업무 입구(`chain.entry-path`)에만 걸린다. 하는 일:
 * 1. X-Request-Id가 없거나 X-Fault의 문법이 틀리면 400 — 측정이 조용히 틀어지지 않게.
 * 2. 이 서비스를 가리키는 `delay`는 처리 전에 잔다, `error`는 처리하지 않고 500을 낸다(둘 다 센다).
 * `fail-after-commit`은 처리 코드가 커밋한 뒤 [FaultInjection.respond]에서 낸다.
 */
class ChainEntryFilter(private val faultInjection: FaultInjection) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
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
        val ctx = ChainContext(requestId, rawFault, directives.filter { it.service == faultInjection.service })
        ChainContext.with(ctx) {
            faultInjection.delayIfAsked(ctx)
            if (faultInjection.errorIfAsked(ctx)) {
                reject(response, 500, """{"fault":"error","service":"${faultInjection.service}"}""")
            } else {
                chain.doFilter(request, response)
            }
        }
    }

    private fun reject(response: HttpServletResponse, status: Int, json: String) {
        response.status = status
        response.contentType = "application/json"
        response.writer.write(json)
    }
}
