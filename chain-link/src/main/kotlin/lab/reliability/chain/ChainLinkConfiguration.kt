package lab.reliability.chain

import com.zaxxer.hikari.HikariDataSource
import org.apache.coyote.AbstractProtocol
import org.apache.tomcat.util.threads.VirtualThreadExecutor
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.tomcat.TomcatWebServer
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

/** 세 서비스가 같은 규칙으로 쓰는 부품. 각 서비스는 `scanBasePackages`에 `lab.reliability.chain`을 넣는다. */
@Configuration(proxyBeanMethods = false)
class ChainLinkConfiguration {
    @Bean
    fun faultInjection(@Value("\${chain.service}") service: String): FaultInjection {
        require(service in FaultDirectives.SERVICES) { "chain.service '$service' is not one of ${FaultDirectives.SERVICES}" }
        return FaultInjection(service)
    }

    @Bean
    fun inFlightRequests() = InFlightRequests()

    @Bean
    fun requestTimings() = RequestTimings()

    @Bean
    fun loadSampler(context: ApplicationContext, inFlightRequests: InFlightRequests) = LoadSampler(context, inFlightRequests)

    /** 업무 입구 한 경로에만 건다(Actuator·/faults·/settings·/inflight·/timings·/samples·조회 끝점에는 걸지 않는다). */
    @Bean
    fun chainEntryFilter(
        faultInjection: FaultInjection,
        inFlightRequests: InFlightRequests,
        requestTimings: RequestTimings,
        @Value("\${chain.entry-path}") entryPath: String,
    ) = FilterRegistrationBean(ChainEntryFilter(faultInjection, inFlightRequests, requestTimings)).apply {
        addUrlPatterns(entryPath)
        order = Ordered.HIGHEST_PRECEDENCE
    }

    /** 아래 층이 있는 서비스(order, inventory)만 `chain.next-layer.url`을 준다. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "chain.next-layer", name = ["url"])
    @EnableConfigurationProperties(NextLayerSettings::class)
    class NextLayerConfiguration {
        @Bean
        fun nextLayerCall(settings: NextLayerSettings): NextLayerCall = HttpNextLayerCall(settings)
    }
}

/**
 * 측정을 흐리는 설정의 실제 값. 실행 스크립트가 실행마다 받아 기록에 남긴다(`settings-<서비스>.json`).
 * 설정 문자열이 아니라 떠 있는 객체에서 읽는다 — Hikari 풀, Tomcat 커넥터와 프로토콜 핸들러, 요청을 처리하는 스레드,
 * 아래 층 호출이 쓰는 HttpClient와 요청. 설정 키가 적용되지 않았으면 여기서 설정과 다른 값이 보인다.
 * 읽지 못한 값은 지어내지 않고 "읽지 못함: <까닭>"으로 낸다.
 */
@RestController
class SettingsController(
    private val context: ApplicationContext,
    private val dataSource: ObjectProvider<DataSource>,
    private val nextLayerCall: ObjectProvider<NextLayerCall>,
    private val faultInjection: FaultInjection,
) {
    @GetMapping("/settings")
    fun settings(): Map<String, Any?> = linkedMapOf(
        "service" to faultInjection.service,
        "tomcat" to read { tomcat() },
        "hikari" to read { hikari() },
        "requestThread" to read {
            // 이 요청(/settings)을 처리하는 스레드 — 업무 요청과 같은 Tomcat 실행기에서 온다.
            val t = Thread.currentThread()
            mapOf("virtual" to t.isVirtual, "name" to t.name)
        },
        "nextLayer" to read {
            when (val call = nextLayerCall.ifAvailable) {
                null -> "없음(맨 아래 층)"
                is HttpNextLayerCall -> call.actualSettings()
                else -> "읽지 못함: 모르는 구현 ${call.javaClass.name}"
            }
        },
        "jdkSystemProperties" to SYSTEM_PROPERTIES.associateWith { System.getProperty(it) ?: "설정 안 됨(JDK 기본값)" },
        "runtime" to mapOf(
            "java.version" to System.getProperty("java.version"),
            "availableProcessors" to Runtime.getRuntime().availableProcessors(),
            "maxMemoryBytes" to Runtime.getRuntime().maxMemory(),
        ),
    )

    private fun tomcat(): Map<String, Any?> {
        val server = (context as? WebServerApplicationContext)?.webServer
            ?: return mapOf("error" to "읽지 못함: 웹 서버 문맥이 아니다")
        if (server !is TomcatWebServer) return mapOf("error" to "읽지 못함: Tomcat이 아니다 (${server.javaClass.name})")
        val connector = server.tomcat.connector
        val protocol = connector.protocolHandler as? AbstractProtocol<*>
            ?: return mapOf("error" to "읽지 못함: 프로토콜 핸들러 ${connector.protocolHandler.javaClass.name}")
        val executor = protocol.executor
        return linkedMapOf(
            "protocolHandler" to protocol.javaClass.name,
            "executor" to (executor?.javaClass?.name ?: "읽지 못함: 실행기가 없다"),
            "executorVirtualThreads" to (executor is VirtualThreadExecutor),
            // 플랫폼 스레드 실행기일 때만 뜻이 있다(가상 스레드 실행기는 최대 스레드를 쓰지 않는다).
            "maxThreads" to protocol.maxThreads,
            "minSpareThreads" to protocol.minSpareThreads,
            "acceptCount" to protocol.acceptCount,
            "maxConnections" to protocol.maxConnections,
            "connectionTimeoutMs" to protocol.connectionTimeout,
            // Tomcat은 따로 주지 않으면 connectionTimeout을 쓴다 — 이 값은 그것까지 풀어낸 실제 값.
            "keepAliveTimeoutMs" to protocol.keepAliveTimeout,
        )
    }

    private fun hikari(): Map<String, Any?> {
        val ds = dataSource.ifAvailable ?: return mapOf("error" to "읽지 못함: DataSource가 없다")
        val hikari = if (ds is HikariDataSource) ds else runCatching { ds.unwrap(HikariDataSource::class.java) }.getOrNull()
            ?: return mapOf("error" to "읽지 못함: HikariDataSource가 아니다 (${ds.javaClass.name})")
        return linkedMapOf(
            "maximumPoolSize" to hikari.maximumPoolSize,
            "minimumIdle" to hikari.minimumIdle,
            "connectionTimeoutMs" to hikari.connectionTimeout,
        )
    }

    private fun read(block: () -> Any?): Any? = try {
        block()
    } catch (e: Exception) {
        "읽지 못함: ${e.javaClass.simpleName}: ${e.message}"
    }

    private companion object {
        val SYSTEM_PROPERTIES = listOf(
            "jdk.httpclient.disableRetryConnect",
            "jdk.httpclient.enableAllMethodRetry",
            "jdk.httpclient.keepalive.timeout",
            "jdk.httpclient.connectionPoolSize",
        )
    }
}
