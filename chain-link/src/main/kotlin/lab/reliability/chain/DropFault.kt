package lab.reliability.chain

import jakarta.servlet.ServletResponse
import jakarta.servlet.ServletResponseWrapper
import org.apache.catalina.connector.ResponseFacade
import org.apache.coyote.ActionCode
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.apache.catalina.connector.Response as CatalinaResponse

/**
 * task 04: `X-Drop-Fault`(R01-13) — 처음 n번 든 것은 처리하고 커밋한 뒤 응답 없이 연결을 끊는다(docs/measurement.md 9-1).
 * 든 횟수는 그 헤더가 이 서비스를 가리킬 때만 requestId마다 센다. 끊을지는 입구에서 정하고([decide]),
 * 실제로 끊는 것은 처리 코드가 마지막 커밋 뒤 응답을 낼 때다([FaultInjection.respond] → [ChainContext.dropConnection]). 끊을 때 센다.
 * 단순화: 든 횟수를 상한 없이 메모리에 둔다 — 실행 하나(수천 건)마다 DELETE /drop-faults로 비우는 실험용.
 */
class DropFaultInjection {
    private val arrivals = ConcurrentHashMap<String, AtomicInteger>()
    private val dropped = AtomicLong()

    fun counts(): Map<String, Long> = mapOf(DropFault.KIND to dropped.get())

    fun reset() {
        arrivals.clear()
        dropped.set(0)
    }

    /** 이번에 든 것을 끊어야 하나. 지시가 없으면 false이고 아무것도 세지 않는다. */
    internal fun decide(requestId: String, directive: DropFault?): Boolean {
        if (directive == null) return false
        return arrivals.computeIfAbsent(requestId) { AtomicInteger() }.incrementAndGet() <= directive.n
    }

    internal fun counted() {
        dropped.incrementAndGet()
    }
}

/**
 * 응답을 한 바이트도 보내지 않고 연결을 닫는다. 서블릿 API에는 이것이 없어 Tomcat의 `ResponseFacade`에서 감싼 `Response`를
 * 꺼낸다(보호된 필드 — 반사). 둘을 한다(Tomcat 11.0.27의 소스로 확인 — `connector/OutputBuffer`, `coyote/AbstractProcessor`):
 * ① `setSuspended(true)` — 뒤에 Spring이 쓰는 응답(헤더·본문)이 버퍼에서 소켓으로 나가지 않는다(flush·close가 아무것도 하지 않는다.
 *    본문이 버퍼(8KB)보다 작을 때 — 이 실험의 응답은 100바이트 안팎이다).
 * ② `ActionCode.CLOSE_NOW` — 처리기의 오류 상태를 "닫음"으로 두어 요청이 끝날 때 응답을 마무리하지 않고 연결을 닫는다.
 * `CLOSE_NOW`만으로는 응답이 그대로 나갔다(2026-11-24 확인 — 201과 본문이 왔다).
 * 예외가 나는 것은 꺼낼 수 없을 때뿐이다(Tomcat의 응답이 아님, 반사 실패, 이미 커밋된 응답). 위 ①·②가 듣지 않아 바이트가 나가는 실패는
 * 이 함수가 알 수 없고 예외 없이 지나간다 — 그것은 분석기가 위 층이 본 결과(`Unknown:IO_*` 시도, 주문 층이면 클라이언트의 status 0)와
 * 끊은 수를 늘 견주어 잡는다(docs/measurement.md 9-5 ②).
 * 단순화: Tomcat 11.0.27의 내부에 기댄다 — Tomcat을 올리면 보정(`cal-idem`)으로 다시 확인한다.
 */
internal fun closeWithoutResponse(response: ServletResponse) {
    var r: ServletResponse = response
    while (r is ServletResponseWrapper) r = r.response
    val facade = r as? ResponseFacade ?: error("응답이 Tomcat의 ResponseFacade가 아니다(${r.javaClass.name}) — 연결을 끊지 못한다")
    val inner = RESPONSE_FIELD.get(facade) as CatalinaResponse
    check(!inner.isCommitted) { "응답이 이미 커밋됐다 — 응답 없이 끊을 수 없다" }
    inner.isSuspended = true
    inner.coyoteResponse.action(ActionCode.CLOSE_NOW, null)
}

private val RESPONSE_FIELD = ResponseFacade::class.java.getDeclaredField("response").apply { isAccessible = true }

@RestController
class DropFaultController(private val injection: DropFaultInjection) {
    /** 마지막 DELETE 뒤 끊은 수. 예: {"drop-after-commit":3} */
    @GetMapping("/drop-faults")
    fun counts() = injection.counts()

    /** 끊은 수와 requestId마다 든 횟수를 비운다. */
    @DeleteMapping("/drop-faults")
    fun reset(): Map<String, Long> {
        injection.reset()
        return injection.counts()
    }
}
