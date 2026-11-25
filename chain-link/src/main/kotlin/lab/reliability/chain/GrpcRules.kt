package lab.reliability.chain

import io.grpc.Context
import io.grpc.Metadata
import io.grpc.Status
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// task 05: gRPC 경로의 규칙 — Spring·네트워크 없이 부를 수 있는 순수 함수(테스트 입구: docs/experiments/task-05-design.md 9절).
// 정본: docs/experiments/task-05-design.md 2·3절, docs/measurement.md 11절.

/** 서비스 사이의 전송(`chain.transport`). 기본 HTTP — task 01~04-b의 동작 그대로. 부하 도구와 주문 사이는 늘 HTTP다. */
enum class Transport(val label: String) {
    HTTP("http"),
    GRPC("grpc"),
    ;

    companion object {
        fun parse(raw: String): Transport =
            entries.firstOrNull { it.label == raw.trim() } ?: throw IllegalArgumentException("chain.transport '$raw' 는 http|grpc")
    }
}

/** gRPC 호출이 끝난 꼴을 기존 갈래(완료 / 실패 / 결과 모름)로 읽는 규칙과, 서버가 처리 코드의 답을 닫는 꼴. */
object GrpcOutcomes {
    /** 서버의 처리 코드가 낸 응답 상태(HTTP 경로와 같은 수)를 싣는 트레일러. */
    val CHAIN_STATUS: Metadata.Key<String> = Metadata.Key.of("chain-status", Metadata.ASCII_STRING_MARSHALLER)

    /** 2xx가 아닌 답을 닫는 코드. gRPC 라이브러리가 스스로 내지 않는 코드를 골랐다 — 라이브러리가 낸 끝과 섞이지 않게. */
    val APPLICATION_ERROR: Status.Code = Status.Code.FAILED_PRECONDITION

    /** 서버가 닫는 꼴: 코드와 트레일러에 실을 응답 상태(OK면 null). */
    data class Close(val code: Status.Code, val chainStatus: Int?)

    /** 서버: 처리 코드의 응답 상태 → 닫는 꼴. 2xx는 OK(트레일러 없음), 그 밖은 [APPLICATION_ERROR] + `chain-status`. */
    fun close(httpStatus: Int): Close =
        if (httpStatus in 200..299) Close(Status.Code.OK, null) else Close(APPLICATION_ERROR, httpStatus)

    /**
     * 부른 쪽: 끝난 꼴 → 결과.
     * 1. 트레일러에 처리 코드의 답(`chain-status`)이 있으면 HTTP 경로와 같은 규칙 — 2xx 완료, 504 결과 모름(`CHAIN_504`), 그 밖 실패(`CHAIN_<n>`).
     * 2. 없고 OK면 완료.
     * 3. 없고 OK가 아니면 결과 모름(`<코드 이름>` — DEADLINE_EXCEEDED, CANCELLED, UNAVAILABLE, UNKNOWN, INTERNAL, …).
     *    처리 코드가 답하지 않은 끝은 아래 층이 처리했는지 모른다. HTTP 경로의 "연결을 맺지 못함 = 실패"(CONNECT_REFUSED·CONNECT_TIMEOUT)에
     *    해당하는 갈래는 두지 않는다 — grpc-java는 연결을 맺지 못한 것과 맺은 뒤 끊긴 것을 같은 UNAVAILABLE로 낸다.
     */
    fun read(code: Status.Code, chainStatus: Int?): LayerOutcome = when {
        chainStatus != null -> when (chainStatus) {
            in 200..299 -> LayerOutcome.Completed
            504 -> LayerOutcome.Unknown("CHAIN_504")
            else -> LayerOutcome.Failed("CHAIN_$chainStatus")
        }
        code == Status.Code.OK -> LayerOutcome.Completed
        else -> LayerOutcome.Unknown(code.name)
    }

    /** 트레일러의 `chain-status`. 없거나 정수가 아니면 null. */
    fun chainStatusOf(trailers: Metadata?): Int? = trailers?.get(CHAIN_STATUS)?.trim()?.toIntOrNull()
}

/** HTTP 헤더와 같은 뜻의 gRPC 메타데이터(키는 HTTP 헤더 이름의 소문자). 값은 받은 그대로 넘긴다. */
object GrpcHeaders {
    val REQUEST_ID: Metadata.Key<String> = key(ChainHeaders.REQUEST_ID)
    val FAULT: Metadata.Key<String> = key(ChainHeaders.FAULT)
    val ATTEMPT_FAULT: Metadata.Key<String> = key(ChainHeaders.ATTEMPT_FAULT)
    val DROP_FAULT: Metadata.Key<String> = key(ChainHeaders.DROP_FAULT)
    val IDEMPOTENCY_KEY: Metadata.Key<String> = key(ChainHeaders.IDEMPOTENCY_KEY)

    private fun key(httpHeader: String): Metadata.Key<String> = Metadata.Key.of(httpHeader.lowercase(), Metadata.ASCII_STRING_MARSHALLER)

    /** 받은 메타데이터에서 읽은 것. 없으면 null. */
    data class Inbound(
        val requestId: String?,
        val rawFault: String?,
        val rawAttemptFault: String?,
        val rawDropFault: String?,
        val idempotencyKey: String?,
    )

    /** 위 층이 보낼 메타데이터. null인 것은 붙이지 않는다(HTTP 경로가 헤더를 붙이지 않는 것과 같다). */
    fun outbound(requestId: String, rawFault: String?, rawAttemptFault: String?, rawDropFault: String?, idempotencyKey: String?): Metadata =
        Metadata().apply {
            put(REQUEST_ID, requestId)
            rawFault?.let { put(FAULT, it) }
            rawAttemptFault?.let { put(ATTEMPT_FAULT, it) }
            rawDropFault?.let { put(DROP_FAULT, it) }
            idempotencyKey?.let { put(IDEMPOTENCY_KEY, it) }
        }

    fun read(md: Metadata): Inbound =
        Inbound(md.get(REQUEST_ID), md.get(FAULT), md.get(ATTEMPT_FAULT), md.get(DROP_FAULT), md.get(IDEMPOTENCY_KEY))
}

/** 기한과 취소 — 조건 G1·G2·G3을 가르는 두 결정과 그 기록. */
object GrpcDeadlines {
    /**
     * 아래 층 호출을 부를 문맥. [propagate]가 true(G2·G3)면 지금 문맥 그대로 — 받은 기한이 아래 호출에도 걸리고(grpc-java가 호출의 기한과
     * 문맥의 기한 가운데 이른 것을 쓴다) 위가 취소하면 아래 호출도 취소된다. false(G1)면 [Context.fork] — 값은 넘기되 취소와 기한은 넘기지 않는다
     * (grpc-api 1.84.1 `Context.fork`: 취소할 수 있는 조상을 두지 않은 새 문맥 → `getDeadline()`이 null, 위의 취소가 닿지 않는다).
     */
    fun downstreamContext(current: Context, propagate: Boolean): Context = if (propagate) current else current.fork()

    /** G3: 일을 시작해도 되나 — 이미 취소됐거나 남은 기한이 0 이하면 아니다. 기한이 없으면(null) 시작한다. */
    fun shouldStart(cancelled: Boolean, remainingNanos: Long?): Boolean = !cancelled && (remainingNanos == null || remainingNanos > 0)

    /** 시각 기록의 `deadlineUs`: 받은 기한의 절대 시각(μs). 기한이 없으면 null. */
    fun absoluteDeadlineUs(nowUs: Long, remainingNanos: Long?): Long? = remainingNanos?.let { nowUs + Math.floorDiv(it, 1000L) }

    /** G3의 넣은 지연: [ms]만큼 기다리되 [cancelled]가 열리면 그때 멈춘다. 다 기다렸으면 true, 취소로 멈췄으면 false. */
    fun awaitDelay(ms: Long, cancelled: CountDownLatch): Boolean = !cancelled.await(ms, TimeUnit.MILLISECONDS)
}

/** 시각 기록의 `stopped`(G3에서 처리 코드가 멈춘 자리). 멈추지 않았으면 null. */
object StopReason {
    /** 입구에서 — 이미 취소됐거나 남은 기한이 없어 넣은 지연도 처리도 시작하지 않았다. */
    const val BEFORE_START = "deadline-before-start"

    /** 넣은 지연을 기다리는 중에 취소됐다. */
    const val IN_DELAY = "cancelled-in-delay"

    /** 넣은 지연 뒤, 처리(첫 DB 트랜잭션) 앞에서 — 남은 기한이 없었다. */
    const val BEFORE_WORK = "deadline-before-work"

    val ALL = setOf(BEFORE_START, IN_DELAY, BEFORE_WORK)
}
