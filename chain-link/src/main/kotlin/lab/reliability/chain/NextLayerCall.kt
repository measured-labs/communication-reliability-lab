package lab.reliability.chain

import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

/** 아래 층을 부른 결과. 위 층은 이것으로 자기 행의 상태와 응답을 정한다. */
sealed interface LayerOutcome {
    /** 아래 층이 처리했다고 답했다(2xx). */
    data object Completed : LayerOutcome

    /** 아래 층이 처리하지 않았다고 볼 수 있다: 오류 응답(504 밖), 또는 연결조차 못 했다. */
    data class Failed(val reason: String) : LayerOutcome

    /** 처리했는지 모른다: 요청 타임아웃, 연결이 중간에 끊김, 아래 층도 모른다고 답함(504). */
    data class Unknown(val reason: String) : LayerOutcome
}

/**
 * 아래 층 호출을 모은 한곳. HTTP 구현([HttpNextLayerCall] — 기본)과 task 05의 gRPC 구현([GrpcNextLayerCall]) 가운데 `chain.transport`로 고른다.
 * 재시도는 이 아래에 두지 않는다 — task 03의 [RetryingNextLayerCall]이 위에서 감싼다(gRPC 전송에서는 켜지 않는다 — [GrpcConfiguration.transport]).
 */
fun interface NextLayerCall {
    fun call(amount: Long): LayerOutcome
}

@ConfigurationProperties("chain.next-layer")
data class NextLayerSettings(
    /** 아래 층 입구의 전체 URL. 예: http://inventory:8080/reservations */
    val url: URI,
    /** 연결 타임아웃. JDK HttpClient는 이것을 클라이언트에만 받는다(요청마다 줄 수 없다). */
    val connectTimeout: Duration,
    /** 요청 타임아웃. 요청마다 준다(gRPC 전송에서는 요청마다의 기한 — `withDeadlineAfter`). */
    val requestTimeout: Duration,
    /** task 05: gRPC 전송일 때 아래 층의 주소(예: payment:9090 — Compose 네트워크 안). HTTP 전송에서는 쓰지 않는다. 비면 null. */
    val grpcTarget: String? = null,
)

/**
 * JDK HttpClient를 직접 쓴다. 숨은 기본값을 코드에서 명시한다:
 * HTTP/1.1 고정, 리다이렉트 NEVER, 연결 타임아웃, 요청마다 요청 타임아웃(안 주면 무한).
 * 연결 실패 자동 재시도는 시스템 속성으로만 끌 수 있어서, 꺼져 있지 않으면 기동을 멈춘다.
 * task 04: [inProgressAsUnknown]이 true면 409(아래 층이 같은 키를 처리 중)를 `Unknown:HTTP_409`로 읽는다(기본 false — `Failed:HTTP_409`).
 */
class HttpNextLayerCall(
    private val settings: NextLayerSettings,
    private val inProgressAsUnknown: Boolean = false,
    /** task 05: 나간 HTTP 호출을 센다(전송 대조 — docs/measurement.md 11절). 없으면 세지 않는다. */
    private val counts: TransportCounts? = null,
) : NextLayerCall {
    init {
        check(System.getProperty("jdk.httpclient.disableRetryConnect") == "true") {
            "-Djdk.httpclient.disableRetryConnect=true 가 없다 — JDK HttpClient가 연결 실패를 몰래 재시도한다"
        }
        check(System.getProperty("jdk.httpclient.enableAllMethodRetry", "false") == "false") {
            "jdk.httpclient.enableAllMethodRetry 가 켜져 있다 — POST가 몰래 재시도된다"
        }
    }

    private val client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(settings.connectTimeout)
        .build()

    private fun request(
        requestId: String,
        rawFault: String?,
        amount: Long,
        rawAttemptFault: String? = null,
        rawDropFault: String? = null,
        idempotencyKey: String? = null,
    ): HttpRequest =
        HttpRequest.newBuilder(settings.url)
            .timeout(settings.requestTimeout)
            .header("Content-Type", "application/json")
            .header(ChainHeaders.REQUEST_ID, requestId)
            .apply { rawFault?.let { header(ChainHeaders.FAULT, it) } }
            .apply { rawAttemptFault?.let { header(ChainHeaders.ATTEMPT_FAULT, it) } }
            // task 04: 받은 그대로 넘긴다(없으면 붙이지 않는다 — task 01~03의 요청 그대로)
            .apply { rawDropFault?.let { header(ChainHeaders.DROP_FAULT, it) } }
            .apply { idempotencyKey?.let { header(ChainHeaders.IDEMPOTENCY_KEY, it) } }
            .POST(HttpRequest.BodyPublishers.ofString("""{"amount":$amount}"""))
            .build()

    /** `/settings`용: 클라이언트 객체와, [call]과 같은 방법으로 만든 요청에서 읽은 실제 값. */
    fun actualSettings(): Map<String, Any?> {
        val sample = request("settings-probe", null, 0)
        return linkedMapOf(
            "url" to sample.uri().toString(),
            "connectTimeoutMs" to (client.connectTimeout().map { it.toMillis() }.orElse(null) ?: "없음(무한)"),
            "requestTimeoutMs" to (sample.timeout().map { it.toMillis() }.orElse(null) ?: "없음(무한)"),
            "version" to client.version().name,
            "followRedirects" to client.followRedirects().name,
        )
    }

    override fun call(amount: Long): LayerOutcome {
        val ctx = ChainContext.current()
        val request = request(ctx.requestId, ctx.rawFault, amount, ctx.rawAttemptFault, ctx.rawDropFault, ctx.idempotencyKey)
        val start = epochMicros()
        counts?.outbound(Transport.HTTP)
        return send(request).also { outcome ->
            ctx.call = CallRecord(start, epochMicros(), outcome.label())
        }
    }

    private fun send(request: HttpRequest): LayerOutcome =
        try {
            val status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
            when (status) {
                in 200..299 -> LayerOutcome.Completed
                504 -> LayerOutcome.Unknown("HTTP_504")
                409 -> if (inProgressAsUnknown) LayerOutcome.Unknown("HTTP_409") else LayerOutcome.Failed("HTTP_409")
                else -> LayerOutcome.Failed("HTTP_$status")
            }
        } catch (e: HttpConnectTimeoutException) {
            LayerOutcome.Failed("CONNECT_TIMEOUT") // HttpTimeoutException의 하위 — 먼저 잡는다
        } catch (e: HttpTimeoutException) {
            LayerOutcome.Unknown("REQUEST_TIMEOUT")
        } catch (e: ConnectException) {
            LayerOutcome.Failed("CONNECT_REFUSED")
        } catch (e: IOException) {
            LayerOutcome.Unknown("IO_${e.javaClass.simpleName}")
        }
}

/** 시각 기록에 남기는 결과의 이름: "Completed" · "Failed:<까닭>" · "Unknown:<까닭>". */
fun LayerOutcome.label(): String = when (this) {
    LayerOutcome.Completed -> "Completed"
    is LayerOutcome.Failed -> "Failed:$reason"
    is LayerOutcome.Unknown -> "Unknown:$reason"
}

/** 위 층의 응답 상태: 처리됨 201, 아래 층이 처리 안 함 502, 모름 504. */
fun LayerOutcome.httpStatus(): Int = when (this) {
    LayerOutcome.Completed -> 201
    is LayerOutcome.Failed -> 502
    is LayerOutcome.Unknown -> 504
}
