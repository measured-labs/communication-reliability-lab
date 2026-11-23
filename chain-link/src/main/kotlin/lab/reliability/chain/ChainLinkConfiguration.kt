package lab.reliability.chain

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 세 서비스가 같은 규칙으로 쓰는 부품. 각 서비스는 `scanBasePackages`에 `lab.reliability.chain`을 넣는다. */
@Configuration(proxyBeanMethods = false)
class ChainLinkConfiguration {
    @Bean
    fun faultInjection(@Value("\${chain.service}") service: String): FaultInjection {
        require(service in FaultDirectives.SERVICES) { "chain.service '$service' is not one of ${FaultDirectives.SERVICES}" }
        return FaultInjection(service)
    }

    /** 업무 입구 한 경로에만 건다(Actuator·/faults·/settings에는 걸지 않는다). */
    @Bean
    fun chainEntryFilter(
        faultInjection: FaultInjection,
        @Value("\${chain.entry-path}") entryPath: String,
    ) = FilterRegistrationBean(ChainEntryFilter(faultInjection)).apply {
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
 * 값은 떠 있는 프로세스의 Environment와 시스템 속성에서 읽는다 — 설정 파일이 아니라 실제로 쓰인 값.
 */
@RestController
class SettingsController(private val env: Environment) {
    private val properties = listOf(
        "chain.service",
        "chain.next-layer.url",
        "chain.next-layer.connect-timeout",
        "chain.next-layer.request-timeout",
        "spring.threads.virtual.enabled",
        "server.tomcat.threads.max",
        "server.tomcat.threads.min-spare",
        "server.tomcat.accept-count",
        "server.tomcat.max-connections",
        "server.tomcat.connection-timeout",
        "spring.datasource.hikari.maximum-pool-size",
        "spring.datasource.hikari.minimum-idle",
        "spring.datasource.hikari.connection-timeout",
        "spring.sql.init.mode",
    )
    private val systemProperties = listOf(
        "java.version",
        "jdk.httpclient.disableRetryConnect",
        "jdk.httpclient.enableAllMethodRetry",
        "jdk.httpclient.keepalive.timeout",
        "jdk.httpclient.connectionPoolSize",
    )

    @GetMapping("/settings")
    fun settings(): Map<String, Any?> =
        properties.associateWith { env.getProperty(it) } +
            systemProperties.associateWith { System.getProperty(it) } +
            mapOf(
                "runtime.availableProcessors" to Runtime.getRuntime().availableProcessors(),
                "runtime.maxMemoryBytes" to Runtime.getRuntime().maxMemory(),
            )
}
