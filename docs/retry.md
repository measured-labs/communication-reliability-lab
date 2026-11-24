# 재시도 감싸개 — 규칙 (task 03)

> 이 문서만 보고 재시도 감싸개의 단위 테스트를 쓸 수 있게 적는다. 코드와 이 문서가 다르면 이 문서가 계약이다(코드를 고친다).
> 코드: `chain-link/src/main/kotlin/lab/reliability/chain/Retry.kt`(감싸개·규칙·예산·시도 기록), `AttemptFaultInjection.kt`(시도마다 넣는 실패), `fault-header/…/AttemptFaultDirectives.kt`(그 헤더의 해석). 넣는 실패의 문법과 분석기의 지표는 `docs/measurement.md` 8절.
> 라이브러리(Resilience4j, Spring Retry, `@Retryable`)를 쓰지 않는다 — 명세 2절("직접 짠 최소 코드").

## 1. 어디에 끼우나, 켜고 끄기

- 아래 층 호출 `NextLayerCall`(주문→재고, 재고→결제)을 감싼다. 감싸는 것은 `HttpNextLayerCall`(JDK HttpClient로 한 번 보내는 것 — 그 안에는 재시도가 없다. JDK의 연결 재시도는 꺼 두었다: `docs/dependencies.md`).
- 설정 `chain.retry.*`(`chain-link/src/main/resources/chain-link.yml`). 서비스마다 환경 변수로 준다 — `compose.yaml`이 주문에는 `ORDER_RETRY_*`, 재고에는 `INVENTORY_RETRY_*`를 그 서비스의 `RETRY_*`로 넘긴다.

| 설정(`chain.retry.`) | 환경 변수(`<ORDER|INVENTORY>_RETRY_`) | 형 | 기본 | 뜻 |
|---|---|---|---|---|
| `enabled` | `ENABLED` | 불 | `false` | **꺼져 있으면 감싸개를 만들지 않는다** — 빈(bean)은 `HttpNextLayerCall` 그대로(task 01·02와 같은 객체·같은 동작). 시도 기록도 남지 않는다 |
| `max-attempts` | `MAX_ATTEMPTS` | 정수 ≥ 1 | `1` | 한 호출의 시도 횟수 상한. **처음 시도를 센다**: 1 = 재시도 없음, 3 = 처음 + 재시도 2번 |
| `retry-on` | `ON` | `failed`·`unknown`의 쉼표 목록 | `failed,unknown` | 다시 시도하는 실패의 갈래(2절) |
| `backoff` | `BACKOFF` | `none`·`fixed`·`exponential`·`exponential-jitter` | `none` | 간격의 식(3절) |
| `base-delay` | `BASE_DELAY` | 기간(예 `100ms`) ≥ 0 | `0ms` | 간격의 시작값 B |
| `max-delay` | `MAX_DELAY` | 기간 ≥ 0 | `0ms` | 지수 간격의 상한 C(`exponential`·`exponential-jitter`만 쓴다 — 그때는 C ≥ B여야 한다) |
| `budget.enabled` | `BUDGET_ENABLED` | 불 | `false` | 재시도 예산(4절). 꺼져 있으면 예산 검사가 없다(무제한) |
| `budget.capacity` | `BUDGET_CAPACITY` | 소수 ≥ 0 | `0` | 버킷의 용량 K |
| `budget.deposit-per-call` | `BUDGET_DEPOSIT` | 소수 ≥ 0 | `0` | 호출 하나마다 넣는 토큰 r |

- 실제로 쓰인 값: `GET /settings`의 `retry` — 떠 있는 감싸개의 규칙·예산 객체에서 읽는다. 켜짐: `{"enabled":true,"maxAttempts":3,"retryOn":["failed","unknown"],"backoff":"exponential","baseDelayMs":100,"maxDelayMs":1000,"budget":{"enabled":false}}`(예산을 켜면 `"budget":{"enabled":true,"capacity":"10","depositPerCall":"0.1"}` — 끝의 0을 뺀 10진 문자열). 꺼짐: `{"enabled":false}`. 맨 아래 층(결제): `"없음(맨 아래 층)"`. `scripts/run.sh`가 실행마다 이 값을 조건과 견주고 다르면 실패한다.
- 잘못된 값을 거절하는 곳(코드에서 확인 — 동작은 그대로 적는다):
  - **값의 꼴**(불이 아닌 `enabled`, 정수가 아닌 `max-attempts`, 모르는 `retry-on`·`backoff` 낱말, 기간이 아닌 `base-delay` 등): Spring이 `RetrySettings`를 묶을 때 — 아래 층이 있는 서비스(주문·재고)는 재시도가 꺼져 있어도 묶으므로 **기동이 실패한다**. 결제(아래 층 없음)는 `chain.retry.*`를 묶지 않는다.
  - **값의 범위**(시도 < 1, 음수 간격, 지수·지수+지터인데 C < B): `RetryPolicy`의 `require` — 감싸개를 만들 때만 부른다. 그래서 **`enabled=true`일 때만 기동이 실패하고, 꺼져 있으면 이 값들은 검사되지 않은 채 기동한다**(쓰이지 않는다).
  - **예산의 범위**(음수 용량·넣는 양): `RetryBudget`의 `require` — `enabled=true`이고 `budget.enabled=true`일 때만 기동이 실패한다.

## 2. 어느 실패에 다시 시도하나

시도 하나의 결과(`LayerOutcome`)를 갈래로 나눈다(`RetryPolicy.classOf`):

| 결과 | 갈래 |
|---|---|
| `Completed`(2xx) | 없음 — 성공 |
| `Failed:HTTP_<s>`, s가 500~599 | `failed` (504는 여기에 오지 않는다 — 504는 `Unknown:HTTP_504`) |
| `Failed:CONNECT_REFUSED`, `Failed:CONNECT_TIMEOUT` | `failed` |
| 그 밖의 `Failed:…`(예 `HTTP_400`, `HTTP_404`) | 없음 — 다시 시도하지 않는다 |
| `Unknown:…`(요청 타임아웃 `REQUEST_TIMEOUT`, `HTTP_504`, `IO_<예외>`) | `unknown` |

시도 하나 뒤의 **결정**은 이 차례로 처음 맞는 것이다(`decision`):

1. `success` — 결과가 `Completed`.
2. `not-retryable` — 갈래가 없거나 `retry-on`에 들지 않음.
3. `max-attempts` — 이 시도의 번호 ≥ `max-attempts`.
4. `budget-denied` — 예산이 켜져 있고 토큰을 뺄 수 없음(4절 — 이 검사에서만 토큰을 뺀다).
5. `retry` — 다음 시도로 간다.

`retry`가 아닌 결정이면 **그 시도의 결과를 그대로 돌려준다**(앞 시도들의 결과는 보지 않는다 — 예: 1번째가 타임아웃(불린 쪽은 처리했을 수 있다), 2번째가 500이면 `Failed:HTTP_500`을 돌려준다).

## 3. 간격 — 다음 시도 n(≥ 2) 앞에서 기다리는 ms

E(n) = min(C, B · 2^(n−2)) (지수 간격의 위 끝, `RetryPolicy.exponentialCeilingMs(n)` — 정수 ms. 두 배씩 키우다 C 이상이면 C에서 멈춘다)

| `backoff` | 간격 d(n) |
|---|---|
| `none` | 0 |
| `fixed` | B (C는 쓰지 않는다) |
| `exponential` | E(n) — 예 B=100, C=1000: 2번째 앞 100, 3번째 200, 4번째 400, 5번째 800, 6번째부터 1000 |
| `exponential-jitter` | ⌊u · E(n)⌋, u는 [0, 1)의 균등 난수 — **"full jitter"**: 0 이상 E(n) 미만의 정수(E(n) = 0이면 0). 예 B=100: 2번째 앞 0~99, 3번째 0~199 |

- 지터는 이 하나로 정했다(까닭: `docs/experiments/task-03-design.md` 1절).
- 간격은 앞 시도의 결과가 돌아온 뒤에 잔다. 첫 시도 앞에는 자지 않는다(기록의 `delayBeforeMs` = 0).
- 시도마다의 요청 타임아웃은 그대로다(`chain.next-layer.request-timeout`). 호출 전체의 기한은 없다 — 위 층이 먼저 포기해도 이 감싸개는 모른다(기한 전파는 범위 밖).

## 4. 재시도 예산 — 토큰 버킷 (`RetryBudget`)

- 부르는 쪽 **프로세스 하나에 버킷 하나**(감싸개 하나 — 서비스마다 하나). 모든 요청이 함께 쓴다.
- 처음 토큰 = K(찬 채로 시작).
- 호출(`call` 하나 — 처음 시도 앞)마다: 토큰 ← min(K, 토큰 + r).
- 재시도하려 할 때(2절 결정 4): 토큰 ≥ 1이면 1을 빼고 재시도, 아니면 빼지 않고 `budget-denied`.
- 소수는 정확히 더한다(`BigDecimal` — 0.1을 열 번 넣으면 정확히 1).
- 그래서 한 프로세스의 재시도 수 ≤ K + r × (그 프로세스의 호출 수). K = 0, r = 0이면 재시도는 0번이다.
- `deposit`·`tryWithdraw`·`tokens`는 서로 배타적이다(`@Synchronized`).

## 5. 시도 기록 — `GET /attempts`

감싸개가 시도마다 한 줄을 남긴다(메모리, `DELETE /attempts`로 비운다. 꺼져 있는 서비스와 결제는 늘 `[]`). 형식은 `docs/measurement.md` 8-2: `requestId`, `callId`(감싼 호출마다 1씩 느는 번호 — 같은 requestId로 여러 번 불리면 호출마다 다르다. **범위는 감싸개 하나 = 서비스 프로세스 하나**이고 프로세스가 뜬 뒤 첫 호출이 1이다. `DELETE /attempts`는 기록만 비우고 이 번호를 되돌리지 않으므로, 한 번 띄운 서비스로 여러 실행을 돌리면 뒤의 실행은 1부터 시작하지 않는다 — 워밍업의 호출도 번호를 쓴다. 분석기는 묶는 열쇠로만 쓴다), `attempt`(1부터), `startUs`·`endUs`(그 시도의 앞뒤 시계), `outcome`(2절의 결과 이름), `delayBeforeMs`(이 시도 앞에서 잔 간격 — 첫 시도 0), `decision`(2절).

- 한 호출의 시도는 1, 2, …, k로 빈틈없이 이어지고, 마지막이 아닌 시도의 `decision`은 모두 `retry`, 마지막은 `retry`가 아니다.
- 입구 필터의 시각 기록(`/timings`)의 call 칸(`callStartUs`·`callEndUs`·`callOutcome`)은 **마지막 시도**의 것이다(시도마다 덮어 쓴다).

### 5-1. 기다리는 사이나 시도 중에 예외가 나면 (코드에서 확인한 동작 — 바꾸지 않았다)

- 감싸개는 예외를 잡지 않는다. 차례는 시도 → 결정 → **기록** → (`retry`면) 간격 계산 → `sleeper`(기본 `Thread.sleep`)다.
- **기다리는 사이**(`Thread.sleep`)에 스레드가 인터럽트되면 `InterruptedException`이 `call` 밖으로 그대로 나간다. 그 호출의 마지막 기록은 `decision` = `retry`인 시도로 남고 다음 시도의 기록은 없다. 결과(`LayerOutcome`)를 돌려주지 않으므로 앞 시도의 결과도 돌려주지 않는다.
- **시도 중**(아래 층 호출 `HttpNextLayerCall`): 연결·요청 타임아웃·`IOException`은 결과(`Failed:…`·`Unknown:…`)로 바뀌어 정상 경로를 탄다(2절). 그 밖의 예외 — 보내는 중의 인터럽트(`InterruptedException`은 `IOException`이 아니다) 등 — 는 그 시도의 기록 없이 `call` 밖으로 나간다(앞 시도들의 마지막 `decision`은 `retry`).
- 그 뒤: 주문·재고의 컨트롤러도 잡지 않으므로 그 층의 DB 행은 `PENDING`으로 남고(결과를 쓰는 둘째 커밋을 하지 않는다), 응답은 Spring MVC의 기본 오류 처리가 정한다(이 저장소의 실행에서 이 경로를 일으켜 본 적은 없다).
- 측정에서 드러나는 곳: 대조 분석(`docs/measurement.md` 5-3)의 `pendingRows`가 0이 아니게 되어 불일치, 재시도 분석은 "마지막 시도의 `decision`이 `retry`"(8-3)로 **입력 오류**가 된다 — 조용히 통과하지 않는다. 이번 72+4개 실행에서 이 경로는 일어나지 않았다(모든 실행의 `pendingRows` 0, 재시도 분석 입력 오류 0).

## 6. 테스트 입구 — 시계와 난수를 바꿔 끼우는 자리

`RetryingNextLayerCall`(Spring 없이 만들 수 있다):

```kotlin
class RetryingNextLayerCall(
    val delegate: NextLayerCall,              // 가짜 아래 층: fun interface — 결과를 차례로 돌려주는 람다
    val policy: RetryPolicy,                  // RetryPolicy(maxAttempts, retryOn: Set<RetryOn>, backoff: Backoff, baseDelayMs, maxDelayMs)
    val budget: RetryBudget?,                 // null = 예산 없음. RetryBudget(capacity: BigDecimal, depositPerCall: BigDecimal)
    record: (AttemptRecord) -> Unit,          // 시도 기록을 받는 곳(서비스에서는 AttemptLog::add)
    requestId: () -> String = { ChainContext.current().requestId },  // 테스트에서는 고정 문자열을 준다
    sleeper: (Long) -> Unit = { Thread.sleep(it) },                  // 간격(ms)을 받아 적어 두는 가짜
    random: () -> Double = { ThreadLocalRandom.current().nextDouble() },  // [0, 1)
    clock: () -> Long = ::epochMicros,                               // μs
) : NextLayerCall
```

- `random`은 `exponential-jitter`일 때만, 재시도마다 정확히 한 번 불린다(다른 간격에서는 불리지 않는다).
- `sleeper`는 재시도마다 정확히 한 번, 계획한 간격으로 불린다 — 0이어도 불린다. 첫 시도 앞, 마지막 시도 뒤에는 불리지 않는다.
- `clock`은 시도마다 두 번(앞, 뒤) 불린다.
- `budget.deposit()`은 `call` 하나마다 처음 시도 앞에서 한 번.
- 순수 함수도 따로 시험할 수 있다: `RetryPolicy.classOf(outcome)`, `RetryPolicy.exponentialCeilingMs(n)`, `RetryPolicy.delayBeforeMs(n, u)`(n < 2이거나 jitter에서 u가 [0, 1) 밖이면 `IllegalArgumentException`), `RetryBudget.deposit()`·`tryWithdraw()`·`tokens()`.
- 테스트를 둘 곳: `chain-link/src/test/kotlin/…`(모듈에 JUnit 5와 kotlin-test가 이미 걸려 있다 — `chain-link/build.gradle.kts`). `scripts/test.sh`가 모든 모듈의 테스트를 센다.

## 7. 시도마다 넣는 실패 — 서비스 쪽 규칙 (`AttemptFaultInjection`)

문법은 `docs/measurement.md` 8-1. 입구 필터가 요청 하나마다 한 번 판정한다(시각 기록의 start와 같은 시각으로):

- 헤더가 없거나 이 서비스를 가리키는 지시가 없으면 아무것도 하지 않는다(든 횟수도 세지 않는다).
- `fail-first=n`: 이 requestId로 이 서비스에 든 횟수 k(이번 것을 포함해 1부터)가 k ≤ n이면 처리하지 않고 500.
- `slow-first=n@ms`: k ≤ n이면 ms만큼 잔 뒤 **처리한다**.
- `outage=from-to`: 입구에 든 시각(μs)이 [from·1000, to·1000)이면 처리하지 않고 500. 든 횟수와 무관.
- 넣을 때마다 종류별 카운터 +1(`GET /attempt-faults` → `{"fail-first":n,"slow-first":n,"outage":n}`). `DELETE /attempt-faults`가 카운터와 든 횟수를 모두 비운다.
- 헤더의 문법이 틀리면 400(행 없음 — `X-Fault`와 같다).
