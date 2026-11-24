package lab.reliability.order

import lab.reliability.chain.AmountRequest
import lab.reliability.chain.ChainContext
import lab.reliability.chain.FaultInjection
import lab.reliability.chain.Idempotency
import lab.reliability.chain.IdempotencyCounters
import lab.reliability.chain.IdempotencyKeys
import lab.reliability.chain.KeyState
import lab.reliability.chain.LayerOutcome
import lab.reliability.chain.NextLayerCall
import lab.reliability.chain.Repeat
import lab.reliability.chain.httpStatus
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication(scanBasePackages = ["lab.reliability.chain", "lab.reliability.order"])
class OrderApplication

fun main(args: Array<String>) {
    runApplication<OrderApplication>(*args)
}

@Repository
class OrderStore(private val jdbc: JdbcClient, private val keys: IdempotencyKeys) {
    /** task 04: PENDING 행과 키(IN_PROGRESS)를 한 트랜잭션에. 키가 이미 있으면 둘 다 되돌리고 null. */
    @Transactional
    fun insertPendingWithKey(key: String, requestId: String, amount: Long): Long? {
        val id = insertPending(requestId, amount)
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
    fun insertPending(requestId: String, amount: Long): Long =
        jdbc.sql("insert into orders (request_id, amount, status) values (:requestId, :amount, 'PENDING') returning id")
            .param("requestId", requestId)
            .param("amount", amount)
            .query(Long::class.java)
            .single()

    @Transactional
    fun finish(id: Long, status: String) {
        val updated = jdbc.sql("update orders set status = :status, updated_at = now() where id = :id")
            .param("status", status)
            .param("id", id)
            .update()
        check(updated == 1) { "order $id not found" }
    }

    fun statuses(requestId: String): List<String> =
        jdbc.sql("select status from orders where request_id = :requestId order by id")
            .param("requestId", requestId)
            .query(String::class.java)
            .list()
            .map { checkNotNull(it) { "status가 null이다" } }
}

/**
 * 가벼운 조회 — 결제(아래 층)를 부르지 않는다. task 02 실험 나에서 느린 결제가 주문 서비스의
 * 관련 없는 요청까지 늦추는지 본다(업무 입구 필터 밖이라 장애 주입·처리 중 요청 수·시각 기록에 들지 않는다).
 */
@RestController
class OrderLookupController(private val orders: OrderStore) {
    @GetMapping("/orders/{requestId}")
    fun lookup(@PathVariable requestId: String) = mapOf("requestId" to requestId, "statuses" to orders.statuses(requestId))
}

/**
 * 주문 → 재고. 아래 층을 부르는 동안 DB 연결을 쥐지 않도록 커밋을 둘로 나눈다(PENDING 기록 / 결과 기록).
 * 재시도하지 않는다(task 03).
 * task 04: 멱등 키가 켜져 있으면 [placeWithKey](docs/idempotency.md), 꺼져 있으면 task 01~03의 경로 그대로.
 */
@RestController
class OrderController(
    private val orders: OrderStore,
    private val inventory: NextLayerCall,
    private val faultInjection: FaultInjection,
    private val idempotency: Idempotency,
) {
    @PostMapping("/orders")
    fun place(@RequestBody body: AmountRequest): ResponseEntity<*> {
        if (idempotency.enabled) return placeWithKey(body.amount)
        val requestId = ChainContext.current().requestId
        val id = orders.insertPending(requestId, body.amount)
        val outcome = inventory.call(body.amount)
        val status = when (outcome) {
            LayerOutcome.Completed -> "COMPLETED"
            is LayerOutcome.Failed -> "FAILED"
            is LayerOutcome.Unknown -> "UNKNOWN"
        }
        orders.finish(id, status)
        return faultInjection.respond(
            ResponseEntity.status(outcome.httpStatus()).body(mapOf("requestId" to requestId, "status" to status, "outcome" to outcome.toString())),
        )
    }

    /** 처음 온 키는 처리하고, 다시 온 키는 [Idempotency.repeated]의 갈래대로(이어 하기면 같은 행으로 아래 층을 다시 부른다). */
    private fun placeWithKey(amount: Long): ResponseEntity<*> {
        val ctx = ChainContext.current()
        val key = checkNotNull(ctx.idempotencyKey) { "키가 켜져 있는데 키가 없다 — 입구 필터가 400으로 막는다" }
        val id = orders.insertPendingWithKey(key, ctx.requestId, amount)?.also { idempotency.counters.add(IdempotencyCounters.NEW) }
            ?: when (val r = idempotency.repeated(key, amount)) {
                is Repeat.Resume -> r.rowId
                else -> return faultInjection.respond(idempotency.response(r))
            }
        val outcome = inventory.call(amount)
        val status = when (outcome) {
            LayerOutcome.Completed -> "COMPLETED"
            is LayerOutcome.Failed -> "FAILED"
            is LayerOutcome.Unknown -> "UNKNOWN"
        }
        val responseBody = idempotency.json(mapOf("requestId" to ctx.requestId, "status" to status, "outcome" to outcome.toString()))
        orders.finishWithKey(id, status, key, KeyState.of(outcome), outcome.httpStatus(), responseBody)
        return faultInjection.respond(idempotency.firstResponse(outcome.httpStatus(), responseBody))
    }
}
