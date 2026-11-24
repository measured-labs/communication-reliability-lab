package lab.reliability.chain

import java.math.BigDecimal

// docs/retry.md 6절의 테스트 입구(RetryingNextLayerCall — 시계·난수·잠을 바꿔 끼운다)로 감싸개를 돌리는 도구.
// 이 파일은 감싸개의 구현을 보지 않고 docs/retry.md 만으로 쓴다. 문서에 없는 서명(NextLayerCall.call(amount: Long),
// LayerOutcome.Failed/Unknown(reason), AttemptRecord 칸의 형)은 컴파일 오류 메시지로만 확인했다.

val BOTH = setOf(RetryOn.FAILED, RetryOn.UNKNOWN)

val OK: LayerOutcome = LayerOutcome.Completed
fun failed(reason: String): LayerOutcome = LayerOutcome.Failed(reason)
fun unknown(reason: String): LayerOutcome = LayerOutcome.Unknown(reason)
val HTTP_500 = failed("HTTP_500")

fun dec(s: String): BigDecimal = BigDecimal(s)

/** BigDecimal 을 눈금(scale)과 무관하게 값으로 견준다. */
fun sameValue(expected: String, actual: BigDecimal): Boolean = actual.compareTo(BigDecimal(expected)) == 0

/**
 * 가짜 아래 층과 바꿔 끼운 시계·난수·잠으로 감싸개 하나를 만든다.
 * - 아래 층: [outcomes]를 차례로 돌려준다(모자라면 테스트가 틀린 것 — 예외). 받은 금액을 적어 둔다.
 * - 시계: 부를 때마다 1000μs씩 커진다(1000, 2000, …) — 부른 횟수도 센다.
 * - 난수: [randoms]를 차례로 돌려준다. 부른 횟수를 센다.
 * - 잠: 받은 ms 를 적어 둔다.
 */
class Harness(
    policy: RetryPolicy,
    budget: RetryBudget? = null,
    outcomes: List<LayerOutcome>,
    private val randoms: List<Double> = emptyList(),
    requestId: String = "req-1",
) {
    private val queue = ArrayDeque(outcomes)
    val amounts = mutableListOf<Long>()
    val records = mutableListOf<AttemptRecord>()
    val sleeps = mutableListOf<Long>()
    var randomCalls = 0
    var clockCalls = 0
    var delegateCalls = 0

    /** 시계를 부른 차례와 아래 층을 부른 차례를 한 줄로 — "clock", "call", "record", "sleep", "random". */
    val events = mutableListOf<String>()

    val delegate = NextLayerCall { amount ->
        delegateCalls++
        amounts += amount
        events += "call"
        queue.removeFirstOrNull() ?: error("가짜 아래 층에 준 결과가 모자란다 — 테스트가 틀렸다")
    }

    val wrapper = RetryingNextLayerCall(
        delegate,
        policy,
        budget,
        { records += it; events += "record" },
        { requestId },
        { ms -> sleeps += ms; events += "sleep" },
        { events += "random"; randoms[randomCalls++] },
        { clockCalls++; events += "clock"; 1000L * clockCalls },
    )

    fun call(amount: Long = 1234): LayerOutcome = wrapper.call(amount)

    val decisions: List<String> get() = records.map { it.decision }
}

/** 결과의 이름(2절·5절의 꼴 — `Completed`, `Failed:<까닭>`, `Unknown:<까닭>`). 결과 객체의 같음(equals)에 기대지 않으려고 이름으로 견준다. */
fun nameOf(o: LayerOutcome): String = when (o) {
    LayerOutcome.Completed -> "Completed"
    is LayerOutcome.Failed -> "Failed:${o.reason}"
    is LayerOutcome.Unknown -> "Unknown:${o.reason}"
}
