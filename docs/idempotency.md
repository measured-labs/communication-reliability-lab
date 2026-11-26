# 멱등 키와 커밋 뒤 끊김 — 규칙 (task 04)

> 이 문서만 보고 멱등 키·끊김 장애·`analyzer idem`의 단위 테스트를 쓸 수 있게 적는다. 코드와 이 문서가 다르면 이 문서가 계약이다(코드를 고친다).
> 코드: `chain-link/src/main/kotlin/lab/reliability/chain/Idempotency.kt`(규칙·저장·센 수), `DropFault.kt`(끊김), `ChainRequest.kt`(입구 필터), `Fault.kt`(`respond`), 세 서비스의 `*Application.kt`(업무 행과 키를 한 트랜잭션에), `fault-header/…/DropFaultDirectives.kt`(끊김 헤더의 해석). 분석기: `analyzer/…/Idempotency.kt`(`docs/measurement.md` 9절).
> 라이브러리를 쓰지 않는다 — 명세 2절("직접 짠 최소 코드"). 실험의 설계와 예상: `docs/experiments/task-04-design.md`.

## 1. 켜고 끄기

| 설정 | 환경 변수 | 형 | 기본 | 뜻 |
|---|---|---|---|---|
| `chain.idempotency.enabled` | `IDEMPOTENCY_ENABLED`(`compose.yaml`이 세 서비스에 같은 값) | 불 | `false` | 꺼져 있으면 처리 코드는 task 01~03의 경로를 그대로 탄다 — 키 헤더를 읽지 않고(400도 내지 않는다), `idempotency_keys`를 쓰지 않는다 |
| `chain.idempotency.in-progress-as-unknown` | `IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN`(세 서비스에 같은 값) | 불 | `false` | 위 층이 아래 층의 409(같은 키 처리 중)를 어떻게 읽나 — 3-1절. `enabled`가 false인데 true면 **기동이 실패한다**(아래 층이 409를 내지 않아 뜻이 없는 설정이 기록되지 않게) |

- 실제로 쓰인 값: `GET /settings`의 `idempotency`. 켜짐 `{"enabled":true,"header":"Idempotency-Key","inProgress":"reject-409","mismatch":"reject-422","replayHeader":"Idempotent-Replayed","inProgressAsUnknown":false}`(3-1절의 설정을 켜면 `true`), 꺼짐 `{"enabled":false}`. `scripts/run.sh`가 실행마다 세 서비스의 값(`enabled`, 켜져 있으면 `inProgressAsUnknown`)을 조건과 견주고 다르면 실패한다. `inProgressAsUnknown`은 2026-11-24 20:30 뒤의 실행부터 나온다(첫 62개 실행의 `settings-*.json`에는 없다 — 그때는 늘 false였다).
- 꺼져 있어도 표 `idempotency_keys`는 만든다(`schema.sql`의 `create table if not exists`) — 비어 있다. `scripts/run.sh`가 실행마다 비운다.

## 2. 키의 꼴과 전달

- 헤더 `Idempotency-Key`(`ChainHeaders.IDEMPOTENCY_KEY`). 값은 부르는 쪽이 정한 비지 않은 문자열. 서비스는 키를 이 헤더에서만 읽는다(`X-Request-Id`와 같은 값이어도 따로 읽는다). 이 실험의 부하 도구는 요청마다 키 = requestId로 보낸다.
- 키는 사슬을 따라 그대로 넘어간다(`HttpNextLayerCall`이 받은 값을 붙인다 — 받지 않았으면 붙이지 않는다). 그래서 층마다 같은 키를 자기 DB에 저장한다.
- 켜져 있는데 헤더가 없거나 공백뿐이면 입구 필터가 **400** `{"error":"missing Idempotency-Key"}`(행 없음, 시각 기록 없음, 센 수 `missing-key` +1). 검사 차례는 `X-Request-Id` → `X-Fault` → `X-Attempt-Fault` → `X-Drop-Fault`(문법) → 키.

## 3. 처리 — 층마다

표 `idempotency_keys(key text primary key, request_id, row_id bigint, amount bigint, state text, response_status int, response_body text, created_at, updated_at)` — 서비스마다 자기 DB에. `row_id` = 같은 트랜잭션에 넣은 업무 행(`orders`·`reservations`·`payments`)의 id, `amount` = 받은 본문의 금액(재고에는 금액 칸이 없지만 키 행에는 남긴다).

**처음 온 키**(키 행이 없다):

| 층 | 첫 트랜잭션 | 아래 층 호출 뒤의 트랜잭션 | 응답 |
|---|---|---|---|
| 주문·재고 | 업무 행 PENDING + 키 행(`IN_PROGRESS`, 응답 칸 null) | 업무 행의 결과 상태 + 키 행의 상태·응답(아래 표) | 저장한 상태·본문 그대로 |
| 결제 | 결제 행 APPROVED + 키 행(`COMPLETED`, 201, 본문) | — | 201, 저장한 본문 |

키 행은 `insert … on conflict (key) do nothing`으로 넣는다. 0행이면(같은 키가 이미 있다) **그 트랜잭션을 되돌린다**(업무 행도 남지 않는다 — `setRollbackOnly`) → "다시 온 키"로 간다. 같은 키를 둘이 동시에 넣으면 PostgreSQL이 뒤의 것을 앞의 트랜잭션이 끝날 때까지 기다리게 한 뒤 0행으로 돌려준다.

아래 층 호출의 결과(`LayerOutcome`)로 정하는 키의 상태(`KeyState.of`)와 저장하는 응답:

| 결과 | 업무 행(주문 / 재고) | 키 상태 | 저장하는 응답 |
|---|---|---|---|
| `Completed` | `COMPLETED` / `RESERVED` | `COMPLETED` | 201 + 본문 |
| `Failed` | `FAILED` | `FAILED` | 502 + 본문 |
| `Unknown` | `UNKNOWN` | `UNKNOWN` | 504 + 본문(기록만 — 되돌려 주지 않는다) |

- **`FAILED`의 뜻**: "아래 층 호출이 `Failed`로 끝났다"이다. 대개 아래 층이 처리하지 않은 것이지만, **`Failed:HTTP_409`**(아래 층이 같은 키를 처리 중 — 3-1절의 설정이 꺼져 있을 때의 읽기)로 끝난 것도 `FAILED`로 저장되고, 그때 아래 층은 끝내 처리할 수 있다. 그래서 "`FAILED` = 이 층과 그 아래가 처리하지 않았다"는 이 설정이 꺼져 있을 때 참이 아니다 — 첫 측정의 가-2 켬에서 이 꼴이 43~45건(클라이언트는 502, 재고·결제는 처리)이었고, 저장한 502가 되돌아오므로 같은 키로 다시 보내도 맞출 수 없다.
- **둘째 커밋이 실패하면**(업무 행의 결과와 키의 상태·응답을 쓰는 트랜잭션이 예외로 끝나면 — DB 오류 등): 그 트랜잭션은 되돌려지고, 업무 행은 `PENDING`, 키는 **`IN_PROGRESS`로 남는다**. 그 요청의 응답은 `respond`를 지나지 않는 500(Spring의 기본 오류 처리)이고, 그 뒤 같은 키는 늘 409다 — 7절의 "처리 중에 죽은 요청"과 같은 꼴이다(회수가 없다). 아래 층은 이미 처리했을 수 있다. 측정에서는 대조 분석의 `pendingRows`(늘 0이어야 한다)와 키 상태 `IN_PROGRESS`로 드러난다 — 첫 62개 실행에서 일어나지 않았다(모두 `pendingRows` 0, `keyStates.IN_PROGRESS` 0).

본문은 `{"requestId":"…","status":"…","outcome":"…"}`(결제 `{"requestId":"…","status":"APPROVED"}`)를 `Idempotency.json`으로 만든 문자열이다. 처음 응답과 되돌려 주는 응답은 같은 바이트다.

**다시 온 키**(`Idempotency.repeated(key, amount)` — 키 행을 읽어 아래 차례로 처음 맞는 것, 그리고 센다):

| 차례 | 조건 | 갈래(`Repeat`) | 응답 | 센 수 |
|---|---|---|---|---|
| 1 | 저장된 `amount` ≠ 받은 금액 | `Mismatch` | **422** `{"error":"Idempotency-Key reused with a different amount"}` — 처리하지 않는다 | `mismatch` |
| 2 | 상태 `COMPLETED` 또는 `FAILED` | `Replay(status, body)` | 저장한 상태·본문 + 헤더 `Idempotent-Replayed: true` — 처리하지 않는다 | `replayed` |
| 3 | 상태 `UNKNOWN` | `Resume(rowId)` | 이 요청이 키를 `IN_PROGRESS`로 잡고(`update … where state = 'UNKNOWN'`), **업무 행을 새로 만들지 않고 `rowId`의 행으로** 아래 층을 같은 키로 다시 부른다 → 처음 온 키의 "아래 층 호출 뒤"와 같게 업무 행과 키를 고친다 | `resumed` |
| 4 | 상태 `IN_PROGRESS` | `InProgress` | **409** `{"error":"a request with the same Idempotency-Key is in progress"}` — 기다리지 않는다 | `in-progress` |

- **명세 계약과의 관계**(명세 9절 task 04: "같은 키가 다시 오면 처리하지 않고 처음의 결과를 돌려준다"): 이 구현은 "처음의 결과"를 **"처음으로 정해진 결과"**로 읽는다. `COMPLETED`·`FAILED`는 그 층이 결과를 정한 것이라 처리하지 않고 저장한 응답을 돌려준다(2). `UNKNOWN`은 그 층도 결과를 몰랐던 것(504) — 정해진 결과가 아직 없으므로 3에서 **아래 층을 다시 부른다**(이어 하기). 이때 이 층은 새 업무 행을 만들지 않지만, 같은 업무 행의 상태와 저장한 응답을 이어 한 결과로 **고쳐 쓴다**(저장한 본문의 `requestId`는 이어 한 요청의 것이다 — 7절). 아래 층은 같은 키를 가지고 있어 두 번 처리하지 않는다. 504를 되돌려 주면 결과 모름을 영영 맞출 수 없어서 이렇게 정했다(`docs/experiments/task-04-design.md` 1절). 그러니 "다시 온 키는 처리하지 않는다"는 `UNKNOWN`에서는 "이 층의 업무 행을 새로 만들지 않고 아래 층의 결과를 다시 묻는다"로 읽어야 한다.
- 3에서 잡지 못하면(다른 요청이 먼저 잡았다) 키 행을 다시 읽어 차례를 처음부터 한 번 더 한다. 두 번째도 잡지 못하면 `InProgress`(409).
- 결제의 키는 늘 `COMPLETED`로 들어가므로 결제에서는 3·4가 생기지 않는다(동시에 온 둘은 DB의 기다림 뒤 2).
- **동시에 온 같은 키는 거절(409)** — 까닭은 `docs/experiments/task-04-design.md` 1절. 위 층은 기본으로 409를 `Failed:HTTP_409`(다시 시도하지 않는 갈래 — `docs/retry.md` 2절)로 읽는다. 다르게 읽는 설정은 3-1절.
- **센 수 `replayed`의 뜻**: 저장한 응답을 **되돌려 주기로 한 수**(갈래 2에 든 수)다. 되돌려 준 응답이 클라이언트에 닿은 수가 아니다 — 그 뒤 `respond`에서 `X-Fault`의 `fail-after-commit`이 응답을 500으로 바꾸거나 `X-Drop-Fault`가 연결을 끊을 수 있다. 닿은 수는 위 층의 기록(시각 기록의 `callOutcome`, 시도 기록)이나 클라이언트의 `replayed`로 센다. `in-progress`·`mismatch`도 같은 뜻으로 "그 갈래에 든 수"다 — 분석기는 이 둘을 그 층의 시각 기록의 409·422 수와 늘 견준다(`docs/measurement.md` 9-5 ⑤).
- 꺼진 상태와 다른 것은 이 절의 경로뿐이다. 응답은 모두 `FaultInjection.respond`를 지난다(`X-Fault`의 `fail-after-commit`, `X-Drop-Fault`가 되돌려 주는 응답에도 걸린다).
- 순수한 규칙(DB 없이 시험할 수 있다): `IdempotencyRules.onRepeat(row: KeyRow, amount: Long): Repeat` — 위 표의 1~4 그대로(2에서 응답 칸이 null이면 `IllegalStateException`).

### 3-1. 위 층이 아래 층의 409를 읽는 법 — `in-progress-as-unknown`

| 설정 | 아래 층의 409를 | 재시도 감싸개의 갈래(`docs/retry.md` 2절) | 그 결과로 끝나면 이 층의 키 | 이 층의 응답 |
|---|---|---|---|---|
| `false`(기본 — 첫 62개 실행 모두) | `Failed:HTTP_409` | 없음(다시 시도하지 않는다) | **`FAILED`**(502를 저장해 되돌려 준다) | 502 |
| `true` | **`Unknown:HTTP_409`** | `unknown`(다시 시도한다 — `retry-on`에 `unknown`이 있을 때) | **`UNKNOWN`**(저장한 응답을 되돌려 주지 않고 다음에 같은 키가 오면 이어 한다 — 3절의 갈래 3) | 504 |

- 읽는 곳은 한 곳이다: 아래 층 호출 `HttpNextLayerCall`이 응답 상태를 받으면 순수 함수 `HttpOutcomes.read`(아래 표)를 부르고, 409는 이 설정에 따라 `Failed:HTTP_409` 또는 `Unknown:HTTP_409`가 된다. 그 뒤는 바뀐 것이 없다 — 재시도 감싸개는 결과 이름으로 갈래를 정하고(`Unknown` → `unknown`), 키 상태는 `KeyState.of`(`Unknown` → `UNKNOWN`), 응답 상태는 `httpStatus`(`Unknown` → 504)가 정한다.
- **`unknown`으로 둔 까닭**(`docs/retry.md` 2절의 정의 — `failed` = "아래 층이 처리하지 않았다고 볼 수 있는 실패", `unknown` = "처리했는지 모름"): 409는 이 요청을 아래 층이 처리하지 않았다는 뜻이지만, 같은 키(같은 논리 요청)의 다른 요청을 아래 층이 처리하는 중이라는 뜻이기도 하다. 부르는 쪽이 알고 싶은 것은 그 논리 요청의 결과이고, 그것은 아직 정해지지 않았다 — 그래서 "처리했는지 모름"이다. `failed`로 두면 위 층의 키가 `FAILED`로 굳어 아래 층이 끝내 처리한 것과 어긋난다(3절 "`FAILED`의 뜻").
- 이 설정은 위 층의 읽기만 바꾼다. 아래 층이 동시에 온 같은 키를 409로 거절하는 것(기다리지 않는다), 재고가 결제의 실패를 다 쓰고 `FAILED`(502)를 저장해 되돌려 주는 것은 바꾸지 않는다.
- 결제는 아래 층이 없어 이 설정을 쓰지 않는다(`/settings`에는 같은 값이 나온다).

**응답 상태를 결과로 읽는 규칙 전체** — `HttpOutcomes.read(status: Int, inProgressAsUnknown: Boolean): LayerOutcome`(`chain-link`, `lab.reliability.chain`, 파일 `NextLayerCall.kt`. Spring·HTTP 클라이언트 없이 부른다). 위에서 아래로 처음 맞는 줄:

| `status` | `inProgressAsUnknown` | 결과 | `label()` |
|---|---|---|---|
| 200~299(양끝 포함) | 상관없음 | `LayerOutcome.Completed` | `Completed` |
| 504 | 상관없음 | `LayerOutcome.Unknown("HTTP_504")` | `Unknown:HTTP_504` |
| 409 | `false` | `LayerOutcome.Failed("HTTP_409")` | `Failed:HTTP_409` |
| 409 | `true` | `LayerOutcome.Unknown("HTTP_409")` | `Unknown:HTTP_409` |
| 그 밖의 모든 정수(1xx, 3xx, 409 밖의 4xx, 504 밖의 5xx — 범위를 검사하지 않으므로 0·음수·600 이상도) | 상관없음 | `LayerOutcome.Failed("HTTP_<status>")`(10진, 앞의 0 없음 — 예 `HTTP_500`, `HTTP_0`, `HTTP_-1`) | `Failed:HTTP_<status>` |

- `inProgressAsUnknown`은 409에만 닿는다. 예외는 던지지 않는다.
- 응답을 받지 못한 끝은 이 함수 밖이다 — `HttpNextLayerCall`이 JDK HttpClient의 예외로 가른다: 연결 타임아웃 `Failed:CONNECT_TIMEOUT`, 요청 타임아웃 `Unknown:REQUEST_TIMEOUT`, 연결 거절 `Failed:CONNECT_REFUSED`, 그 밖의 `IOException` `Unknown:IO_<예외 이름>`(`docs/retry.md` 2절).
- gRPC 경로의 같은 규칙은 `GrpcOutcomes.read`(`docs/experiments/task-05-design.md`) — 트레일러의 `chain-status`를 읽는 갈래는 409를 따로 보지 않는다(gRPC 전송에서는 멱등 키를 켜지 않는다).

## 4. 커밋 뒤 끊김 — `X-Drop-Fault`

- 문법: `<service>:drop-after-commit=<n>[,…]`, n ≥ 1의 정수. 빈 값·공백뿐·없음 = 없음. 한 서비스를 가리키는 지시는 하나만. 공백은 쉼표로 나눈 지시의 앞뒤와 `:`의 앞뒤에만(`X-Attempt-Fault`와 같다). 그 밖은 틀린 헤더 → 서비스 400(행 없음), 분석기 입력 오류. 해석: `DropFaultDirectives.parse(raw: String?): List<DropFault>`(`DropFault(service: String, n: Int)`, 종류 이름 `DropFault.KIND` = `"drop-after-commit"`) — 틀리면 `IllegalArgumentException`.
- 입구 필터가 요청마다 한 번 정한다(`DropFaultInjection.decide`): 이 서비스를 가리키는 지시가 있으면 그 requestId로 이 서비스에 든 횟수 k(이번 것 포함, 1부터)를 세고 k ≤ n이면 끊기로 한다. 지시가 없으면 세지 않는다. 키의 상태와 무관하다(되돌려 주는 응답도 k ≤ n이면 끊긴다).
- 끊는 때: 처리 코드가 응답을 `FaultInjection.respond`로 낼 때(마지막 커밋 뒤). 응답을 **한 바이트도 보내지 않고** 연결을 닫는다(`closeWithoutResponse` — Tomcat 내부, `DropFault.kt`의 주석). 그때 센다. 끊긴 요청의 시각 기록 `status`는 0.
- 서비스가 센 끊은 수는 "끊으려고 `closeWithoutResponse`를 부른 수"다 — 그 함수는 Tomcat의 응답을 꺼내지 못할 때만 예외를 내고, 바이트가 실제로 나갔는지는 알 수 없다. 그래서 분석기는 끊은 수를 **위 층이 본 결과**(재고·결제의 끊김은 위 층의 `Unknown:IO_*` 시도, 주문의 끊김은 클라이언트의 `status` 0)와 늘 견준다(`docs/measurement.md` 9-5 ②).
- 서비스 쪽 끝점: `GET /drop-faults` → `{"drop-after-commit":n}`, `DELETE /drop-faults`(카운터와 든 횟수를 비운다).
- 위 층에서 보이는 것: JDK HttpClient는 `IOException` → `Unknown:IO_<예외>`(결과 모름 — 재시도가 켜져 있으면 `unknown` 갈래로 다시 시도한다), k6는 `status` 0.

## 5. 서비스의 끝점과 센 수

| 끝점 | 뜻 |
|---|---|
| `GET /idempotency-counts` | 마지막 DELETE 뒤 센 수 `{"new":n,"replayed":n,"in-progress":n,"mismatch":n,"resumed":n,"missing-key":n}`(`IdempotencyCounters.KINDS` 차례). `new` = 키 행을 넣은 수 |
| `DELETE /idempotency-counts` | 비운다 |
| `GET /drop-faults`, `DELETE /drop-faults` | 4절 |
| `GET /retry-budget` | (task 04, ledger R03-7) 떠 있는 재시도 감싸개의 예산 토큰: `{"enabled":true,"tokens":"10"}`(끝의 0을 뺀 10진 문자열) 또는 `{"enabled":false}`(재시도·예산이 꺼졌거나 맨 아래 층). 실행 스크립트가 부하 직전에 읽어 `retry-budget-start.json`과 `config.env`의 `<층>_retry_budget_start_tokens`에 남긴다 |

## 6. 테스트 입구 — 이름과 서명

`chain-link`(`lab.reliability.chain`) — Spring 없이 만들 수 있는 것:

```kotlin
enum class KeyState { IN_PROGRESS, COMPLETED, FAILED, UNKNOWN; companion object { fun of(outcome: LayerOutcome): KeyState } }
data class KeyRow(val key: String, val requestId: String, val rowId: Long, val amount: Long,
                  val state: KeyState, val responseStatus: Int?, val responseBody: String?)
sealed interface Repeat {
    data object Mismatch : Repeat
    data object InProgress : Repeat
    data class Replay(val status: Int, val body: String) : Repeat
    data class Resume(val rowId: Long) : Repeat
}
object IdempotencyRules { fun onRepeat(row: KeyRow, amount: Long): Repeat }

class IdempotencyCounters {                       // 종류: NEW·REPLAYED·IN_PROGRESS·MISMATCH·RESUMED·MISSING_KEY (KINDS)
    fun add(kind: String); fun counts(): Map<String, Long>; fun reset()
}
class IdempotencyKeys(jdbc: JdbcClient) {         // SQL — DB가 있어야 시험할 수 있다(이 저장소에는 Testcontainers가 없다)
    fun insert(key: String, requestId: String, rowId: Long, amount: Long, state: KeyState,
               responseStatus: Int? = null, responseBody: String? = null): Boolean
    fun find(key: String): KeyRow?
    fun claim(key: String): Boolean               // UNKNOWN → IN_PROGRESS
    fun finish(key: String, state: KeyState, responseStatus: Int, responseBody: String)
}
data class IdempotencySettings(val enabled: Boolean, val inProgressAsUnknown: Boolean = false)  // enabled=false인데 inProgressAsUnknown=true면 IllegalArgumentException
object HttpOutcomes { fun read(status: Int, inProgressAsUnknown: Boolean): LayerOutcome }      // 3-1절의 "응답 상태를 결과로 읽는 규칙 전체"
class HttpNextLayerCall(settings: NextLayerSettings, inProgressAsUnknown: Boolean = false,
                        counts: TransportCounts? = null)   // 응답 상태는 HttpOutcomes.read로 읽는다. counts: (task 05) 나간 호출을 센다 — 없으면 세지 않는다
class Idempotency(val enabled: Boolean, keys: IdempotencyKeys, val counters: IdempotencyCounters,
                  val inProgressAsUnknown: Boolean = false) {
    fun json(body: Map<String, Any?>): String
    fun repeated(key: String, amount: Long): Repeat           // 3절의 표 — 센다
    fun response(r: Repeat): ResponseEntity<*>                 // Mismatch 422 · InProgress 409 · Replay 저장한 것 + 헤더 · Resume은 IllegalStateException
    fun firstResponse(status: Int, body: String): ResponseEntity<*>
    fun settings(): Map<String, Any?>
}
```

- `Idempotency.repeated`를 DB 없이 시험하려면 `IdempotencyKeys`를 가짜로 바꿔 끼워야 하는데, 이것은 인터페이스가 아니라 클래스다(`open`이 아니다) — 이 단계에서 가짜를 끼우는 자리는 만들지 않았다(요청 없는 추상화를 피했다). 순수한 갈래는 `IdempotencyRules.onRepeat`로, 저장과 동시성은 보정 실행(`cal-idem`)으로 본다.

`fault-header`(`lab.reliability.chain`, Spring 없음):

```kotlin
data class DropFault(val service: String, val n: Int) { companion object { const val KIND = "drop-after-commit" } }
object DropFaultDirectives { fun parse(raw: String?): List<DropFault> }
```

`chain-link`의 끊김(입구 필터 밖에서 시험할 수 있는 것):

```kotlin
class DropFaultInjection {
    fun counts(): Map<String, Long>               // {"drop-after-commit": 끊은 수}
    fun reset()
    internal fun decide(requestId: String, directive: DropFault?): Boolean   // 같은 모듈의 테스트에서 부를 수 있다
    internal fun counted()
}
```

분석기(`lab.reliability.analyzer`): 명령줄 `runAnalyzer(arrayOf("idem", "--run", <폴더>, ["--expect", <파일>], ["--no-row-ids"]))` → 종료 코드(`docs/measurement.md` 9절). 읽는 함수: `readIdemAnswers(path: Path): List<IdemSend>`, `readResends(path: Path, answerIds: Set<String>): List<IdemSend>?`(파일이 없으면 null), `readKeyCsv(path: Path): List<KeyCsvRow>`, `readRowIds(path: Path): List<RowId>`, `readKeyRowIds(path: Path): List<KeyRowId>`, 보고 `idempotencyReport(run: Path, rowIds: Boolean = true): Pair<Map<String, Any?>, List<String>>`(보고와 늘 견주는 것의 차이 줄).

## 7. 일부러 단순하게 둔 것

- 키의 만료·청소가 없다 — 실행마다 표를 비운다. 상주 서비스라면 보관 기간과 청소가 있어야 한다.
- 처리 중에 죽은 요청의 키와 둘째 커밋이 실패한 요청의 키(3절)는 `IN_PROGRESS`로 남아 그 키는 영원히 409다 — 회수(기한이 지난 `IN_PROGRESS`를 다시 잡기)를 넣지 않았다.
- 키와 금액만 견준다(본문의 다른 칸이 없다). requestId가 다른 같은 키는 막지 않는다 — 이어 하기는 처음 키 행의 업무 행을 쓴다.
- 끊김은 Tomcat 11.0.27의 내부(`ResponseFacade`의 보호된 필드, `setSuspended`, `ActionCode.CLOSE_NOW`)에 기댄다. 응답 본문이 Tomcat의 응답 버퍼(8KB)보다 크면 앞부분이 나갈 수 있다 — 이 실험의 응답은 100바이트 안팎이다.
