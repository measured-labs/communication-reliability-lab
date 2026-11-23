package lab.reliability.inventory

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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication(scanBasePackages = ["lab.reliability.chain", "lab.reliability.inventory"])
class InventoryApplication

fun main(args: Array<String>) {
    runApplication<InventoryApplication>(*args)
}

@Repository
class ReservationStore(private val jdbc: JdbcClient) {
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
 */
@RestController
class ReservationController(
    private val reservations: ReservationStore,
    private val payment: NextLayerCall,
    private val faultInjection: FaultInjection,
) {
    @PostMapping("/reservations")
    fun reserve(@RequestBody body: AmountRequest): ResponseEntity<*> {
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
}
