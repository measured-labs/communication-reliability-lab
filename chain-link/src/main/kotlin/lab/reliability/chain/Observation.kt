package lab.reliability.chain

import org.apache.coyote.AbstractProtocol
import org.springframework.boot.tomcat.TomcatWebServer
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.apache.tomcat.util.threads.ThreadPoolExecutor as TomcatThreadPoolExecutor

// task 02의 관측: 층마다 요청의 시작·끝 시각(시각 기록)과, 바쁜 스레드·대기 수를 일정 간격으로 뜬 표본.
// 둘 다 메모리에만 두고 실행 스크립트가 부하 뒤 끝점으로 받아 실행 기록에 남긴다(형식: docs/measurement.md 7절).

/** 벽시계(μs). 세 서비스와 k6가 한 컴퓨터의 같은 시계를 쓴다. */
fun epochMicros(): Long {
    val now = Instant.now()
    return now.epochSecond * 1_000_000 + now.nano / 1_000
}

/** 아래 층을 부른 기록. outcome 예: "Completed", "Failed:HTTP_500", "Unknown:REQUEST_TIMEOUT". */
data class CallRecord(val callStartUs: Long, val callEndUs: Long, val callOutcome: String)

/** 업무 입구에 든 요청 하나. 아래 층을 부르지 않았으면 call* 칸은 null. */
data class TimingRecord(
    val requestId: String,
    val startUs: Long,
    val endUs: Long,
    val status: Int,
    val callStartUs: Long?,
    val callEndUs: Long?,
    val callOutcome: String?,
) {
    constructor(requestId: String, startUs: Long, endUs: Long, status: Int, call: CallRecord?) :
        this(requestId, startUs, endUs, status, call?.callStartUs, call?.callEndUs, call?.callOutcome)
}

/** 단순화: 상한 없이 메모리에 쌓는다 — 실행 하나(수천 건)마다 비우는 실험용. 상주 서비스라면 내보내기로 바꾼다. */
class RequestTimings {
    private val records = ConcurrentLinkedQueue<TimingRecord>()

    fun add(record: TimingRecord) {
        records.add(record)
    }

    fun snapshot(): List<TimingRecord> = records.toList()

    fun reset() = records.clear()
}

@RestController
class TimingController(private val timings: RequestTimings) {
    /** 마지막 DELETE 뒤 업무 입구에 든 요청의 시각 기록(JSON 배열). */
    @GetMapping("/timings")
    fun timings() = timings.snapshot()

    @DeleteMapping("/timings")
    fun reset(): Map<String, Int> {
        timings.reset()
        return mapOf("timings" to timings.snapshot().size)
    }
}

/** 표본 하나. Tomcat 실행기가 플랫폼 스레드 풀이 아니면(가상 스레드) active·poolSize·maxThreads·queue는 null. */
data class LoadSample(
    val atMs: Long,
    val inflight: Int,
    val active: Int?,
    val poolSize: Int?,
    val maxThreads: Int?,
    val queue: Int?,
    val connections: Long?,
)

/**
 * [INTERVAL_MS]마다 표본을 뜬다. 뜨는 스레드는 Tomcat의 것이 아닌 데몬 플랫폼 스레드 하나라서
 * 요청 스레드가 모두 묶여도 계속 뜬다. 표본이 [MAX_SAMPLES]를 넘으면 더 쌓지 않고 overflow를 세운다 —
 * 끝점이 그것을 알리고 분석기가 실패한다(조용히 잘리지 않게).
 */
class LoadSampler(private val context: ApplicationContext, private val inFlight: InFlightRequests) : AutoCloseable {
    private val samples = ConcurrentLinkedQueue<LoadSample>()
    private val size = AtomicInteger()
    private val overflow = AtomicBoolean(false)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "load-sampler").apply { isDaemon = true } }

    init {
        scheduler.scheduleAtFixedRate(::sample, INTERVAL_MS, INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * `tomcat.connector`(Tomcat.getConnector)를 쓰지 않는다 — 서비스에 커넥터가 없으면 기본 커넥터를 새로 만들어 붙인다.
     * Boot는 초기화와 시작 사이에 커넥터를 서비스에서 떼어 두므로, 그 사이에 이 스레드가 부르면 8080을 두 번 열어
     * 기동이 "Port 8080 was already in use"로 실패했다(2026-11-24). 있는 커넥터만 찾고, 없으면 이번 표본은 null.
     */
    private fun protocol(): AbstractProtocol<*>? {
        val server = (context as? WebServerApplicationContext)?.webServer as? TomcatWebServer ?: return null
        return server.tomcat.service.findConnectors().firstOrNull()?.protocolHandler as? AbstractProtocol<*>
    }

    private fun sample() {
        try {
            if (size.get() >= MAX_SAMPLES) {
                overflow.set(true)
                return
            }
            val protocol = protocol()
            val pool = protocol?.executor as? TomcatThreadPoolExecutor
            samples.add(
                LoadSample(
                    atMs = System.currentTimeMillis(),
                    inflight = inFlight.current(),
                    active = pool?.activeCount,
                    poolSize = pool?.poolSize,
                    maxThreads = pool?.maximumPoolSize,
                    queue = pool?.queue?.size,
                    connections = protocol?.connectionCount,
                ),
            )
            size.incrementAndGet()
        } catch (e: Exception) {
            // 예외로 뜨기가 멈추면(scheduleAtFixedRate는 예외 뒤 다시 돌지 않는다) 표본이 조용히 끊긴다 — 삼키고 다음에 다시 뜬다.
            // 빠진 표본은 분석기가 표본 사이의 간격(maxGapMs)으로 드러낸다.
        }
    }

    fun snapshot(): Map<String, Any> = mapOf("intervalMs" to INTERVAL_MS, "overflow" to overflow.get(), "samples" to samples.toList())

    fun reset() {
        samples.clear()
        size.set(0)
        overflow.set(false)
    }

    override fun close() {
        scheduler.shutdownNow()
    }

    companion object {
        const val INTERVAL_MS = 100L
        const val MAX_SAMPLES = 100_000
    }
}

@RestController
class SampleController(private val sampler: LoadSampler) {
    /** {"intervalMs":100,"overflow":false,"samples":[...]} — 마지막 DELETE 뒤의 표본. */
    @GetMapping("/samples")
    fun samples() = sampler.snapshot()

    @DeleteMapping("/samples")
    fun reset(): Map<String, Any> {
        sampler.reset()
        return mapOf("reset" to true)
    }
}
