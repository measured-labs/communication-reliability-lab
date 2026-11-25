package lab.reliability.chain

import io.grpc.BindableService
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.ForwardingServerCallListener
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.MetadataUtils
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
import lab.reliability.chain.grpc.AmountMessage
import lab.reliability.chain.grpc.ChainLayerGrpc
import lab.reliability.chain.grpc.LayerReply
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerExecutorProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.grpc.client.ChannelBuilderOptions
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.grpc.server.lifecycle.GrpcServerStartedEvent
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// task 05: 서비스 사이의 gRPC 전송 — 설정으로 고르고(기본 HTTP), 기한 전파(G2)와 처리 코드의 취소 보기(G3)를 켜고 끈다.
// 규칙(순수 함수)은 GrpcRules.kt. 정본: docs/experiments/task-05-design.md, docs/measurement.md 11절.

/**
 * `chain.grpc.*`. [serve]는 gRPC 서버를 띄우는 서비스(재고·결제의 application.yml이 true). [deadlinePropagation]·[observeCancellation]은
 * 조건(G2·G3)이고 HTTP 전송에서는 켤 수 없다(기동 실패). [serverThreads]는 gRPC 서버 실행기의 스레드 수 — Tomcat의 최대 스레드와 같게.
 */
@ConfigurationProperties("chain.grpc")
data class GrpcSettings(
    val serve: Boolean = false,
    val deadlinePropagation: Boolean = false,
    val observeCancellation: Boolean = false,
    val serverThreads: Int = 200,
) {
    fun settings(): Map<String, Any?> = linkedMapOf(
        "serve" to serve,
        "deadlinePropagation" to deadlinePropagation,
        "observeCancellation" to observeCancellation,
        "serverThreads" to serverThreads,
    )
}

/**
 * 전송마다 센 호출 수 — 시각 기록과 따로 센다(분석기가 둘을 견준다 — docs/measurement.md 11절).
 * 들어온 HTTP는 [ChainEntryFilter]가 어떤 검사보다 먼저, 들어온 gRPC는 서비스 정의를 감싼 서버 인터셉터가 서비스 구현에 닿기 전에,
 * 나간 HTTP는 [HttpNextLayerCall]이 보내기 직전에, 나간 gRPC는 채널에 건 클라이언트 인터셉터가 센다 — 시각 기록을 남기는 자리와 다른 자리다.
 */
class TransportCounts {
    private val inboundHttp = AtomicLong()
    private val inboundGrpc = AtomicLong()
    private val outboundHttp = AtomicLong()
    private val outboundGrpc = AtomicLong()
    private val grpcCancelledBeforeHandler = AtomicLong()

    internal fun inbound(t: Transport) = (if (t == Transport.HTTP) inboundHttp else inboundGrpc).incrementAndGet()

    internal fun outbound(t: Transport) = (if (t == Transport.HTTP) outboundHttp else outboundGrpc).incrementAndGet()

    /** 들어온 gRPC 호출 가운데 처리 코드에 닿기 전에 취소돼 grpc-java가 처리 코드를 부르지 않은 것(아래 [InboundInterceptor]). */
    internal fun cancelledBeforeHandler() = grpcCancelledBeforeHandler.incrementAndGet()

    /** 예: {"inbound":{"http":0,"grpc":800},"outbound":{"http":0,"grpc":800},"grpcCancelledBeforeHandler":0} */
    fun counts(): Map<String, Any> = linkedMapOf(
        "inbound" to linkedMapOf("http" to inboundHttp.get(), "grpc" to inboundGrpc.get()),
        "outbound" to linkedMapOf("http" to outboundHttp.get(), "grpc" to outboundGrpc.get()),
        "grpcCancelledBeforeHandler" to grpcCancelledBeforeHandler.get(),
    )

    fun reset() = listOf(inboundHttp, inboundGrpc, outboundHttp, outboundGrpc, grpcCancelledBeforeHandler).forEach { it.set(0) }
}

@RestController
class TransportCountController(private val counts: TransportCounts) {
    @GetMapping("/transport-counts")
    fun counts() = counts.counts()

    @DeleteMapping("/transport-counts")
    fun reset(): Map<String, Any> {
        counts.reset()
        return counts.counts()
    }
}

/** 업무 처리 하나(재고의 예약, 결제의 승인) — gRPC 서버가 HTTP 컨트롤러와 같은 코드를 부르려고. 재고·결제가 빈으로 준다. */
fun interface LayerHandler {
    fun handle(amount: Long): ResponseEntity<*>
}

/**
 * 아래 층을 gRPC blocking 스텁으로 부른다. 요청마다 [requestTimeout]을 기한으로 걸고(`withDeadlineAfter` — 채널의 기본 기한은 쓰지 않는다),
 * [GrpcDeadlines.downstreamContext]로 받은 기한·취소를 넘길지 정한다(G1 아니오, G2·G3 예). 끝난 꼴은 [GrpcOutcomes.read]로 읽는다.
 * 시각 기록의 call 칸은 HTTP 경로와 같은 자리에서 잰다(부르기 직전 ~ 돌아온 직후).
 */
class GrpcNextLayerCall(
    private val channel: ManagedChannel,
    private val target: String,
    private val requestTimeout: Duration,
    private val propagateDeadline: Boolean,
) : NextLayerCall {
    private val stub = ChainLayerGrpc.newBlockingStub(channel)

    override fun call(amount: Long): LayerOutcome {
        val ctx = ChainContext.current()
        val md = GrpcHeaders.outbound(ctx.requestId, ctx.rawFault, ctx.rawAttemptFault, ctx.rawDropFault, ctx.idempotencyKey)
        val start = epochMicros()
        val outcome = GrpcDeadlines.downstreamContext(Context.current(), propagateDeadline).call { send(md, amount) }
        ctx.call = CallRecord(start, epochMicros(), outcome.label())
        return outcome
    }

    private fun send(md: Metadata, amount: Long): LayerOutcome =
        try {
            stub.withDeadlineAfter(requestTimeout.toNanos(), TimeUnit.NANOSECONDS)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md))
                .handle(AmountMessage.newBuilder().setAmount(amount).build())
            GrpcOutcomes.read(Status.Code.OK, null)
        } catch (e: StatusRuntimeException) {
            GrpcOutcomes.read(e.status.code, GrpcOutcomes.chainStatusOf(e.trailers))
        }

    /** `/settings`용. `retryEnabled`는 떠 있는 채널 객체의 필드에서 읽는다(grpc-core `ManagedChannelImpl.retryEnabled` — 반사). */
    fun actualSettings(): Map<String, Any?> = linkedMapOf(
        "target" to target,
        "requestTimeoutMs" to requestTimeout.toMillis(),
        "deadlinePropagation" to propagateDeadline,
        "retryEnabled" to channelRetryEnabled(channel),
        "channelClass" to channel.javaClass.name,
        "stub" to "blocking",
    )

    companion object {
        private val log = LoggerFactory.getLogger(GrpcNextLayerCall::class.java)

        /**
         * Spring의 채널 팩토리로 채널을 만든다. 재시도는 채널에서 끈다(`disableRetry()` — 서비스 설정의 재시도 정책과 투명한 재시도 모두.
         * grpc-core 1.84.1 `ManagedChannelImpl.ChannelStreamProvider`: retryEnabled가 false면 `RetriableStream`을 쓰지 않는다).
         * 이 맞춤은 Spring의 전역 맞춤(설정 속성) 뒤에 돈다(`DefaultGrpcChannelFactory.createChannel`). 나간 호출은 [counts]가 센다.
         */
        fun create(factory: GrpcChannelFactory, target: String, requestTimeout: Duration, propagateDeadline: Boolean, counts: TransportCounts): GrpcNextLayerCall {
            val options = ChannelBuilderOptions.defaults()
                .withInterceptors(listOf(OutboundCounter(counts)))
                .withCustomizer(GrpcChannelBuilderCustomizer<NettyChannelBuilder> { _, builder -> builder.disableRetry() })
            val channel = factory.createChannel(target, options)
            log.info("gRPC 아래 층 채널: target={}, requestTimeout={}, deadlinePropagation={}", target, requestTimeout, propagateDeadline)
            return GrpcNextLayerCall(channel, target, requestTimeout, propagateDeadline)
        }

        /** 떠 있는 채널의 `retryEnabled`. `ManagedChannelOrphanWrapper` 같은 감싸개는 `delegate` 필드를 따라 벗긴다. 못 읽으면 "읽지 못함: …". */
        internal fun channelRetryEnabled(channel: Any): Any = try {
            var c: Any = channel
            repeat(4) {
                val delegate = findField(c.javaClass, "delegate") ?: return@repeat
                c = delegate.get(c) ?: return@repeat
            }
            val f = findField(c.javaClass, "retryEnabled") ?: error("${c.javaClass.name}에 retryEnabled 필드가 없다")
            f.getBoolean(c)
        } catch (e: Exception) {
            "읽지 못함: ${e.javaClass.simpleName}: ${e.message}"
        }

        private fun findField(cls: Class<*>, name: String): java.lang.reflect.Field? {
            var k: Class<*>? = cls
            while (k != null) {
                k.declaredFields.firstOrNull { it.name == name }?.let { return it.apply { isAccessible = true } }
                k = k.superclass
            }
            return null
        }
    }
}

/** 나간 gRPC 호출을 센다(채널에 건다). */
private class OutboundCounter(private val counts: TransportCounts) : ClientInterceptor {
    override fun <ReqT, RespT> interceptCall(method: MethodDescriptor<ReqT, RespT>, callOptions: CallOptions, next: Channel): ClientCall<ReqT, RespT> {
        counts.outbound(Transport.GRPC)
        return next.newCall(method, callOptions)
    }
}

/**
 * 받은 메타데이터를 문맥에 실어 서비스 구현이 읽게 하고, 들어온 gRPC 호출을 센다(서비스 정의를 감싼다).
 * grpc-java는 처리 코드에 닿기 전에 취소된 호출(실행기의 큐에서 기다리는 동안 부른 쪽이 끊었거나 기한이 지남)의 처리 코드를 부르지 않는다
 * (grpc-core 1.84.1 `ServerCallImpl.ServerStreamListenerImpl.halfClosed` — `call.cancelled`면 돌아간다. unary의 처리 코드는 halfClose에서 불린다).
 * 그런 호출은 시각 기록이 없으므로 따로 센다([TransportCounts.cancelledBeforeHandler]) — 센 수 = 기록 수 + 이것(docs/measurement.md 11절).
 * 열린 호출(받은 때 ~ 닫힌 때)은 [InFlightRequests.grpcOpened]로 센다 — 가라앉기가 큐에서 기다리는 호출까지 기다리게.
 */
private class InboundInterceptor(private val counts: TransportCounts, private val inFlight: InFlightRequests) : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(call: ServerCall<ReqT, RespT>, headers: Metadata, next: ServerCallHandler<ReqT, RespT>): ServerCall.Listener<ReqT> {
        counts.inbound(Transport.GRPC)
        inFlight.grpcOpened()
        val delegate = Contexts.interceptCall(Context.current().withValue(HEADERS, headers), call, headers, next)
        return object : ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(delegate) {
            @Volatile
            private var halfClosed = false
            private val ended = AtomicBoolean(false)

            override fun onHalfClose() {
                halfClosed = true
                super.onHalfClose()
            }

            override fun onCancel() {
                try {
                    super.onCancel()
                } finally {
                    if (!halfClosed) counts.cancelledBeforeHandler()
                    end()
                }
            }

            override fun onComplete() {
                try {
                    super.onComplete()
                } finally {
                    end()
                }
            }

            private fun end() {
                if (ended.compareAndSet(false, true)) inFlight.grpcClosed()
            }
        }
    }

    companion object {
        val HEADERS: Context.Key<Metadata> = Context.key("chain-inbound-headers")
    }
}

/**
 * gRPC 업무 입구 — HTTP 경로의 [ChainEntryFilter]가 하는 일을 같은 부품으로 한다(`X-Request-Id`·`X-Fault` 검사, 처리 중 수, 넣은 지연·오류,
 * 시각 기록). 처리는 [LayerHandler](HTTP 컨트롤러와 같은 메서드)에 맡기고, 그 응답 상태를 [GrpcOutcomes.close]로 닫는다.
 * 이 경로가 받지 않는 것: `X-Attempt-Fault`·`X-Drop-Fault`가 이 서비스를 가리키면 400(재시도·끊김은 이 task의 범위 밖 — 넘겨주기만 한다).
 *
 * 시각 기록에 더하는 것: `transport`=grpc, `deadlineUs`(받은 기한의 절대 시각), `cancelledUs`(이 호출의 문맥이 취소된 것을 처음 안 시각 —
 * 위가 끊었거나 받은 기한이 지났다. 조건과 상관없이 늘 잰다), `stopped`(G3에서 멈춘 자리 — [StopReason]).
 *
 * [observeCancellation](G3)이 켜져 있으면: 입구에서 이미 취소됐거나 남은 기한이 없으면 아무것도 시작하지 않고, 넣은 지연을 기다리는 중에
 * 취소되면 멈추고, 지연 뒤 처리(첫 DB 트랜잭션) 앞에서 한 번 더 본다. 처리를 시작한 뒤에는 보지 않는다 — 트랜잭션은 끝까지 가서 커밋된다
 * (재고는 아래 호출이 gRPC에 의해 취소되면 그 결과로 행을 마친다). 꺼져 있으면(G1·G2) 넣은 지연은 HTTP 경로와 같은 `Thread.sleep`이다.
 */
class GrpcChainLayerService(
    private val faultInjection: FaultInjection,
    private val inFlight: InFlightRequests,
    private val timings: RequestTimings,
    private val handler: LayerHandler,
    private val observeCancellation: Boolean,
) : ChainLayerGrpc.ChainLayerImplBase() {
    override fun handle(request: AmountMessage, responseObserver: StreamObserver<LayerReply>) {
        // 위가 끊은 뒤 응답을 내도 예외가 나지 않게(unary는 원래 던지지 않지만 명시한다)
        (responseObserver as? ServerCallStreamObserver<LayerReply>)?.setOnCancelHandler {}
        inFlight.track { serve(request.amount, responseObserver) }
    }

    private fun serve(amount: Long, observer: StreamObserver<LayerReply>) {
        val grpcCtx = Context.current()
        val inbound = GrpcHeaders.read(InboundInterceptor.HEADERS.get() ?: Metadata())
        val requestId = inbound.requestId
        if (requestId.isNullOrBlank()) return reply(observer, 400, null)
        val directives = try {
            FaultDirectives.parse(inbound.rawFault)
        } catch (e: IllegalArgumentException) {
            return reply(observer, 400, null)
        }
        val unsupported = try {
            AttemptFaultDirectives.parse(inbound.rawAttemptFault).any { it.service == faultInjection.service } ||
                DropFaultDirectives.parse(inbound.rawDropFault).any { it.service == faultInjection.service }
        } catch (e: IllegalArgumentException) {
            true
        }
        if (unsupported) return reply(observer, 400, null)
        val ctx = ChainContext(
            requestId, inbound.rawFault, directives.filter { it.service == faultInjection.service },
            inbound.rawAttemptFault, inbound.rawDropFault, inbound.idempotencyKey,
        )
        val start = epochMicros()
        val deadline = grpcCtx.deadline
        val deadlineUs = GrpcDeadlines.absoluteDeadlineUs(start, deadline?.timeRemaining(TimeUnit.NANOSECONDS))
        val cancelled = CountDownLatch(1)
        val cancelledUs = AtomicLong(NOT_CANCELLED)
        val listener = Context.CancellationListener {
            cancelledUs.compareAndSet(NOT_CANCELLED, epochMicros())
            cancelled.countDown()
        }
        grpcCtx.addListener(listener, Runnable::run)
        var status = 0
        var stopped: String? = null
        fun mayStart() = GrpcDeadlines.shouldStart(grpcCtx.isCancelled, deadline?.timeRemaining(TimeUnit.NANOSECONDS))
        try {
            ChainContext.with(ctx) {
                if (observeCancellation && !mayStart()) {
                    stopped = StopReason.BEFORE_START
                    return@with
                }
                val delayMs = faultInjection.delayAsked(ctx)
                if (delayMs != null) {
                    if (!observeCancellation) {
                        Thread.sleep(delayMs)
                    } else if (!GrpcDeadlines.awaitDelay(delayMs, cancelled)) {
                        stopped = StopReason.IN_DELAY
                        return@with
                    }
                }
                if (observeCancellation && !mayStart()) {
                    stopped = StopReason.BEFORE_WORK
                    return@with
                }
                if (faultInjection.errorIfAsked(ctx)) {
                    status = 500
                    return@with
                }
                status = try {
                    handler.handle(amount).statusCode.value()
                } catch (e: Exception) {
                    log.warn("처리 코드가 예외로 끝났다 — 500으로 닫는다: {}", e.toString())
                    500
                }
            }
        } finally {
            grpcCtx.removeListener(listener)
            val c = cancelledUs.get().takeIf { it != NOT_CANCELLED }
            timings.add(TimingRecord(requestId, start, epochMicros(), status, ctx.call, Transport.GRPC.label, deadlineUs, c, stopped))
        }
        reply(observer, status, stopped)
    }

    private fun reply(observer: StreamObserver<LayerReply>, status: Int, stopped: String?) {
        if (stopped != null) {
            observer.onError(Status.CANCELLED.withDescription("stopped: $stopped").asRuntimeException())
            return
        }
        val close = GrpcOutcomes.close(status)
        if (close.code == Status.Code.OK) {
            observer.onNext(LayerReply.getDefaultInstance())
            observer.onCompleted()
        } else {
            val trailers = Metadata().apply { put(GrpcOutcomes.CHAIN_STATUS, close.chainStatus.toString()) }
            observer.onError(Status.fromCode(close.code).withDescription("chain-status $status").asRuntimeException(trailers))
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(GrpcChainLayerService::class.java)
        const val NOT_CANCELLED = Long.MIN_VALUE
    }
}

/** 떠 있는 gRPC 서버(시작 이벤트에서 받는다) — `/settings`가 포트·서비스·실행기를 읽는다. */
class GrpcServerHolder {
    @Volatile
    var server: Server? = null
        private set

    @EventListener
    fun started(event: GrpcServerStartedEvent) {
        server = event.server
    }
}

/**
 * 전송 고르기와 gRPC 부품. gRPC 서버는 `chain.transport=grpc`이고 `chain.grpc.serve=true`인 서비스(재고·결제)에서만 뜬다 —
 * Boot의 gRPC 서버 자동 구성은 `BindableService` 빈이 있을 때만 돌아서, HTTP 전송에서는 서버도 헬스·리플렉션 서비스도 생기지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GrpcSettings::class)
class GrpcConfiguration {
    /** 전송과, 이 task가 재지 않는 조합을 막는 검사(조용히 다른 조건으로 재지 않게 — 기동 실패). */
    @Bean
    fun transport(@Value("\${chain.transport}") raw: String, grpc: GrpcSettings, env: Environment): Transport {
        val t = Transport.parse(raw)
        if (t == Transport.HTTP) {
            check(!grpc.deadlinePropagation && !grpc.observeCancellation) {
                "chain.grpc.deadline-propagation·observe-cancellation 은 chain.transport=grpc 에서만 켠다"
            }
        } else {
            for (p in listOf("chain.retry.enabled", "chain.idempotency.enabled", "chain.overload.enabled")) {
                check(env.getProperty(p, Boolean::class.java, false) == false) { "$p 는 gRPC 전송에서 켜지 않는다(task 05의 범위 밖 — 잰 적 없는 조합)" }
            }
            check(grpc.serverThreads >= 1) { "chain.grpc.server-threads ${grpc.serverThreads} < 1" }
        }
        return t
    }

    @Bean
    fun transportCounts() = TransportCounts()

    @Bean
    fun grpcServerHolder() = GrpcServerHolder()

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = ["chain.transport"], havingValue = "grpc")
    @ConditionalOnBooleanProperty("chain.grpc.serve")
    class GrpcServing {
        /**
         * gRPC 서버의 실행기 — grpc-java의 기본(상한 없는 캐시 스레드 풀, `GrpcUtil.SHARED_CHANNEL_EXECUTOR`)을 쓰지 않는다.
         * Tomcat과 같은 크기: 스레드 최대 [GrpcSettings.serverThreads](200), 큐는 상한 없음(Tomcat의 `TaskQueue`도 상한이 없다).
         * 다른 점: Tomcat은 최소 유휴 10개를 두고 그 위로 늘리지만, 이것은 필요할 때 200까지 만들고 60초 쉬면 줄인다.
         */
        @Bean(destroyMethod = "shutdown")
        fun grpcServerExecutor(grpc: GrpcSettings): ThreadPoolExecutor {
            val n = AtomicInteger()
            return ThreadPoolExecutor(grpc.serverThreads, grpc.serverThreads, 60, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
                Thread(r, "grpc-exec-${n.incrementAndGet()}")
            }.apply { allowCoreThreadTimeOut(true) }
        }

        @Bean
        fun grpcServerExecutorProvider(grpcServerExecutor: ThreadPoolExecutor) = GrpcServerExecutorProvider { grpcServerExecutor }

        @Bean
        fun chainLayerService(
            faultInjection: FaultInjection,
            inFlightRequests: InFlightRequests,
            requestTimings: RequestTimings,
            handler: LayerHandler,
            grpc: GrpcSettings,
            counts: TransportCounts,
        ): BindableService {
            val impl = GrpcChainLayerService(faultInjection, inFlightRequests, requestTimings, handler, grpc.observeCancellation)
            val definition = ServerInterceptors.intercept(impl.bindService(), InboundInterceptor(counts, inFlightRequests))
            return BindableService { definition }
        }
    }
}
