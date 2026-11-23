package lab.reliability.order

import lab.reliability.chain.AmountRequest
import lab.reliability.chain.ChainContext
import lab.reliability.chain.FaultInjection
import lab.reliability.chain.LayerOutcome
import lab.reliability.chain.NextLayerCall
import lab.reliability.chain.httpStatus
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
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
class OrderStore(private val jdbc: JdbcClient) {
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
 */
@RestController
class OrderController(
    private val orders: OrderStore,
    private val inventory: NextLayerCall,
    private val faultInjection: FaultInjection,
) {
    @PostMapping("/orders")
    fun place(@RequestBody body: AmountRequest): ResponseEntity<*> {
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
}
