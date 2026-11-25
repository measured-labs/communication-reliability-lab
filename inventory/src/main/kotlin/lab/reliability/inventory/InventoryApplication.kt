package lab.reliability.inventory

import lab.reliability.chain.AmountRequest
import lab.reliability.chain.ChainContext
import lab.reliability.chain.FaultInjection
import lab.reliability.chain.Idempotency
import lab.reliability.chain.IdempotencyCounters
import lab.reliability.chain.IdempotencyKeys
import lab.reliability.chain.KeyState
import lab.reliability.chain.LayerHandler
import lab.reliability.chain.LayerOutcome
import lab.reliability.chain.NextLayerCall
import lab.reliability.chain.Repeat
import lab.reliability.chain.httpStatus
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

@SpringBootApplication(scanBasePackages = ["lab.reliability.chain", "lab.reliability.inventory"])
class InventoryApplication {
    /** task 05: gRPC로 들어온 호출도 HTTP 컨트롤러와 같은 메서드로 처리한다(chain.transport=grpc일 때 gRPC 입구가 부른다). */
    @Bean
    fun layerHandler(controller: ReservationController) = LayerHandler { controller.reserve(AmountRequest(it)) }
}

fun main(args: Array<String>) {
    runApplication<InventoryApplication>(*args)
}

@Repository
class ReservationStore(private val jdbc: JdbcClient, private val keys: IdempotencyKeys) {
    /** task 04: PENDING 행과 키(IN_PROGRESS)를 한 트랜잭션에. 키가 이미 있으면 둘 다 되돌리고 null. */
    @Transactional
    fun insertPendingWithKey(key: String, requestId: String, amount: Long): Long? {
        val id = insertPending(requestId)
        if (keys.insert(key, requestId, id, amount, KeyState.IN_PROGRESS)) return id
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
        return null
    }

    /** task 04: 업무 행의 결과와 키의 상태·응답을 한 트랜잭션에. */
    @Transactional
    fun finishWithKey(id: Long, status: String, key: String, keyState: KeyState, responseStatus: Int, responseBody: String) {
        finish(id, status)
        keys.finish(key, keyState, responseStatus, responseBody)
    }

    @Transactional
    fun insertPending(requestId: String): Long =
        jdbc.sql("insert into reservations (request_id, status) values (:requestId, 'PENDING') returning id")
            .param("requestId", requestId)
            .query(Long::class.java)
            .single()

    @Transactional
    fun finish(id: Long, status: String) {
        val updated = jdbc.sql("update reservations set status = :status, updated_at = now() where id = :id")
            .param("status", status)
            .param("id", id)
            .update()
        check(updated == 1) { "reservation $id not found" }
    }
}

/**
 * 재고 → 결제. 재고가 결제를 부르는 것은 호출 깊이를 셋으로 만들려는 실험용 구조다(README).
 * 커밋을 둘로 나누는 까닭은 order와 같다. 재시도하지 않는다.
 * task 04: 멱등 키가 켜져 있으면 [reserveWithKey](docs/idempotency.md), 꺼져 있으면 task 01~03의 경로 그대로.
 */
@RestController
class ReservationController(
    private val reservations: ReservationStore,
    private val payment: NextLayerCall,
    private val faultInjection: FaultInjection,
    private val idempotency: Idempotency,
) {
    @PostMapping("/reservations")
    fun reserve(@RequestBody body: AmountRequest): ResponseEntity<*> {
        if (idempotency.enabled) return reserveWithKey(body.amount)
        val requestId = ChainContext.current().requestId
        val id = reservations.insertPending(requestId)
        val outcome = payment.call(body.amount)
        val status = when (outcome) {
            LayerOutcome.Completed -> "RESERVED"
            is LayerOutcome.Failed -> "FAILED"
            is LayerOutcome.Unknown -> "UNKNOWN"
        }
        reservations.finish(id, status)
        return faultInjection.respond(
            ResponseEntity.status(outcome.httpStatus()).body(mapOf("requestId" to requestId, "status" to status, "outcome" to outcome.toString())),
        )
    }

    /** 처음 온 키는 처리하고, 다시 온 키는 [Idempotency.repeated]의 갈래대로(이어 하기면 같은 행으로 아래 층을 다시 부른다). */
    private fun reserveWithKey(amount: Long): ResponseEntity<*> {
        val ctx = ChainContext.current()
        val key = checkNotNull(ctx.idempotencyKey) { "키가 켜져 있는데 키가 없다 — 입구 필터가 400으로 막는다" }
        val id = reservations.insertPendingWithKey(key, ctx.requestId, amount)?.also { idempotency.counters.add(IdempotencyCounters.NEW) }
            ?: when (val r = idempotency.repeated(key, amount)) {
                is Repeat.Resume -> r.rowId
                else -> return faultInjection.respond(idempotency.response(r))
            }
        val outcome = payment.call(amount)
        val status = when (outcome) {
            LayerOutcome.Completed -> "RESERVED"
            is LayerOutcome.Failed -> "FAILED"
            is LayerOutcome.Unknown -> "UNKNOWN"
        }
        val responseBody = idempotency.json(mapOf("requestId" to ctx.requestId, "status" to status, "outcome" to outcome.toString()))
        reservations.finishWithKey(id, status, key, KeyState.of(outcome), outcome.httpStatus(), responseBody)
        return faultInjection.respond(idempotency.firstResponse(outcome.httpStatus(), responseBody))
    }
}
