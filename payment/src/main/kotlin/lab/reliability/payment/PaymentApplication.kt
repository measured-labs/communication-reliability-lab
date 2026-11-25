package lab.reliability.payment

import lab.reliability.chain.AmountRequest
import lab.reliability.chain.ChainContext
import lab.reliability.chain.FaultInjection
import lab.reliability.chain.Idempotency
import lab.reliability.chain.IdempotencyCounters
import lab.reliability.chain.IdempotencyKeys
import lab.reliability.chain.KeyState
import lab.reliability.chain.LayerHandler
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication(scanBasePackages = ["lab.reliability.chain", "lab.reliability.payment"])
class PaymentApplication {
    /** task 05: gRPC로 들어온 호출도 HTTP 컨트롤러와 같은 메서드로 처리한다(chain.transport=grpc일 때 gRPC 입구가 부른다). */
    @Bean
    fun layerHandler(controller: PaymentController) = LayerHandler { controller.pay(AmountRequest(it)) }
}

fun main(args: Array<String>) {
    runApplication<PaymentApplication>(*args)
}

@Repository
class PaymentStore(private val jdbc: JdbcClient, private val keys: IdempotencyKeys) {
    /** task 04: 결제 행과 키(COMPLETED, 응답 201)를 한 트랜잭션에. 키가 이미 있으면 둘 다 되돌리고 false. */
    @Transactional
    fun approveWithKey(key: String, requestId: String, amount: Long, responseBody: String): Boolean {
        val id = approve(requestId, amount)
        if (keys.insert(key, requestId, id, amount, KeyState.COMPLETED, 201, responseBody)) return true
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
        return false
    }

    @Transactional
    fun approve(requestId: String, amount: Long): Long =
        jdbc.sql("insert into payments (request_id, amount, status) values (:requestId, :amount, 'APPROVED') returning id")
            .param("requestId", requestId)
            .param("amount", amount)
            .query(Long::class.java)
            .single()
}

/**
 * 사슬의 맨 아래. 한 트랜잭션에 결제 행을 넣는다.
 * task 04: 멱등 키가 켜져 있으면 결제 행과 키를 한 트랜잭션에 넣고, 다시 온 키는 [Idempotency.repeated]의 갈래대로
 * (결제의 키는 늘 COMPLETED로 들어가므로 이어 하기가 없다 — 동시에 온 둘은 DB가 앞의 커밋까지 기다리게 한 뒤 저장한 응답).
 */
@RestController
class PaymentController(
    private val payments: PaymentStore,
    private val faultInjection: FaultInjection,
    private val idempotency: Idempotency,
) {
    @PostMapping("/payments")
    fun pay(@RequestBody body: AmountRequest): ResponseEntity<*> {
        if (idempotency.enabled) return payWithKey(body.amount)
        val requestId = ChainContext.current().requestId
        payments.approve(requestId, body.amount)
        return faultInjection.respond(
            ResponseEntity.status(201).body(mapOf("requestId" to requestId, "status" to "APPROVED")),
        )
    }

    private fun payWithKey(amount: Long): ResponseEntity<*> {
        val ctx = ChainContext.current()
        val key = checkNotNull(ctx.idempotencyKey) { "키가 켜져 있는데 키가 없다 — 입구 필터가 400으로 막는다" }
        val responseBody = idempotency.json(mapOf("requestId" to ctx.requestId, "status" to "APPROVED"))
        if (payments.approveWithKey(key, ctx.requestId, amount, responseBody)) {
            idempotency.counters.add(IdempotencyCounters.NEW)
            return faultInjection.respond(idempotency.firstResponse(201, responseBody))
        }
        return faultInjection.respond(idempotency.response(idempotency.repeated(key, amount)))
    }
}
