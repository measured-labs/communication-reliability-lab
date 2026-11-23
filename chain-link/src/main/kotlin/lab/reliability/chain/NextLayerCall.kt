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
 * 아래 층 호출을 모은 한곳. 지금은 HTTP 구현 하나([HttpNextLayerCall]).
 * task 05에서 gRPC 구현을 이 자리에 끼운다. 재시도는 이 아래에 두지 않는다(task 03이 위에서 감싼다).
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
    /** 요청 타임아웃. 요청마다 준다. */
    val requestTimeout: Duration,
)

/**
 * JDK HttpClient를 직접 쓴다. 숨은 기본값을 코드에서 명시한다:
 * HTTP/1.1 고정, 리다이렉트 NEVER, 연결 타임아웃, 요청마다 요청 타임아웃(안 주면 무한).
 * 연결 실패 자동 재시도는 시스템 속성으로만 끌 수 있어서, 꺼져 있지 않으면 기동을 멈춘다.
 */
class HttpNextLayerCall(private val settings: NextLayerSettings) : NextLayerCall {
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

    private fun request(requestId: String, rawFault: String?, amount: Long): HttpRequest =
        HttpRequest.newBuilder(settings.url)
            .timeout(settings.requestTimeout)
            .header("Content-Type", "application/json")
            .header(ChainHeaders.REQUEST_ID, requestId)
            .apply { rawFault?.let { header(ChainHeaders.FAULT, it) } }
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
        val request = request(ctx.requestId, ctx.rawFault, amount)
        return try {
            val status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
            when (status) {
                in 200..299 -> LayerOutcome.Completed
                504 -> LayerOutcome.Unknown("HTTP_504")
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
}

/** 위 층의 응답 상태: 처리됨 201, 아래 층이 처리 안 함 502, 모름 504. */
fun LayerOutcome.httpStatus(): Int = when (this) {
    LayerOutcome.Completed -> 201
    is LayerOutcome.Failed -> 502
    is LayerOutcome.Unknown -> 504
}
