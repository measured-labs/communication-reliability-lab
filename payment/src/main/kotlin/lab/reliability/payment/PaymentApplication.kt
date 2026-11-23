package lab.reliability.payment

import lab.reliability.chain.AmountRequest
import lab.reliability.chain.ChainContext
import lab.reliability.chain.FaultInjection
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication(scanBasePackages = ["lab.reliability.chain", "lab.reliability.payment"])
class PaymentApplication

fun main(args: Array<String>) {
    runApplication<PaymentApplication>(*args)
}

@Repository
class PaymentStore(private val jdbc: JdbcClient) {
    @Transactional
    fun approve(requestId: String, amount: Long): Long =
        jdbc.sql("insert into payments (request_id, amount, status) values (:requestId, :amount, 'APPROVED') returning id")
            .param("requestId", requestId)
            .param("amount", amount)
            .query(Long::class.java)
            .single()
}

/** 사슬의 맨 아래. 한 트랜잭션에 결제 행을 넣는다. */
@RestController
class PaymentController(
    private val payments: PaymentStore,
    private val faultInjection: FaultInjection,
) {
    @PostMapping("/payments")
    fun pay(@RequestBody body: AmountRequest): ResponseEntity<*> {
        val requestId = ChainContext.current().requestId
        payments.approve(requestId, body.amount)
        return faultInjection.respond(
            ResponseEntity.status(201).body(mapOf("requestId" to requestId, "status" to "APPROVED")),
        )
    }
}
