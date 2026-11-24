# 멱등 키와 커밋 뒤 끊김 — 규칙 (task 04)

> 이 문서만 보고 멱등 키·끊김 장애·`analyzer idem`의 단위 테스트를 쓸 수 있게 적는다. 코드와 이 문서가 다르면 이 문서가 계약이다(코드를 고친다).
> 코드: `chain-link/src/main/kotlin/lab/reliability/chain/Idempotency.kt`(규칙·저장·센 수), `DropFault.kt`(끊김), `ChainRequest.kt`(입구 필터), `Fault.kt`(`respond`), 세 서비스의 `*Application.kt`(업무 행과 키를 한 트랜잭션에), `fault-header/…/DropFaultDirectives.kt`(끊김 헤더의 해석). 분석기: `analyzer/…/Idempotency.kt`(`docs/measurement.md` 9절).
> 라이브러리를 쓰지 않는다 — 명세 2절("직접 짠 최소 코드"). 실험의 설계와 예상: `docs/experiments/task-04-design.md`.

## 1. 켜고 끄기

| 설정 | 환경 변수 | 형 | 기본 | 뜻 |
|---|---|---|---|---|
| `chain.idempotency.enabled` | `IDEMPOTENCY_ENABLED`(`compose.yaml`이 세 서비스에 같은 값) | 불 | `false` | 꺼져 있으면 처리 코드는 task 01~03의 경로를 그대로 탄다 — 키 헤더를 읽지 않고(400도 내지 않는다), `idempotency_keys`를 쓰지 않는다 |

- 실제로 쓰인 값: `GET /settings`의 `idempotency`. 켜짐 `{"enabled":true,"header":"Idempotency-Key","inProgress":"reject-409","mismatch":"reject-422","replayHeader":"Idempotent-Replayed"}`, 꺼짐 `{"enabled":false}`. `scripts/run.sh`가 실행마다 세 서비스의 값을 조건과 견주고 다르면 실패한다.
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

본문은 `{"requestId":"…","status":"…","outcome":"…"}`(결제 `{"requestId":"…","status":"APPROVED"}`)를 `Idempotency.json`으로 만든 문자열이다. 처음 응답과 되돌려 주는 응답은 같은 바이트다.

**다시 온 키**(`Idempotency.repeated(key, amount)` — 키 행을 읽어 아래 차례로 처음 맞는 것, 그리고 센다):

| 차례 | 조건 | 갈래(`Repeat`) | 응답 | 센 수 |
|---|---|---|---|---|
| 1 | 저장된 `amount` ≠ 받은 금액 | `Mismatch` | **422** `{"error":"Idempotency-Key reused with a different amount"}` — 처리하지 않는다 | `mismatch` |
| 2 | 상태 `COMPLETED` 또는 `FAILED` | `Replay(status, body)` | 저장한 상태·본문 + 헤더 `Idempotent-Replayed: true` — 처리하지 않는다 | `replayed` |
| 3 | 상태 `UNKNOWN` | `Resume(rowId)` | 이 요청이 키를 `IN_PROGRESS`로 잡고(`update … where state = 'UNKNOWN'`), **업무 행을 새로 만들지 않고 `rowId`의 행으로** 아래 층을 같은 키로 다시 부른다 → 처음 온 키의 "아래 층 호출 뒤"와 같게 업무 행과 키를 고친다 | `resumed` |
| 4 | 상태 `IN_PROGRESS` | `InProgress` | **409** `{"error":"a request with the same Idempotency-Key is in progress"}` — 기다리지 않는다 | `in-progress` |

- 3에서 잡지 못하면(다른 요청이 먼저 잡았다) 키 행을 다시 읽어 차례를 처음부터 한 번 더 한다. 두 번째도 잡지 못하면 `InProgress`(409).
- 결제의 키는 늘 `COMPLETED`로 들어가므로 결제에서는 3·4가 생기지 않는다(동시에 온 둘은 DB의 기다림 뒤 2).
- **동시에 온 같은 키는 거절(409)** — 까닭은 `docs/experiments/task-04-design.md` 1절. 위 층은 409를 `Failed:HTTP_409`(다시 시도하지 않는 갈래 — `docs/retry.md` 2절)로 읽는다. 이 규칙은 바꾸지 않았다.
- 꺼진 상태와 다른 것은 이 절의 경로뿐이다. 응답은 모두 `FaultInjection.respond`를 지난다(`X-Fault`의 `fail-after-commit`, `X-Drop-Fault`가 되돌려 주는 응답에도 걸린다).
- 순수한 규칙(DB 없이 시험할 수 있다): `IdempotencyRules.onRepeat(row: KeyRow, amount: Long): Repeat` — 위 표의 1~4 그대로(2에서 응답 칸이 null이면 `IllegalStateException`).

## 4. 커밋 뒤 끊김 — `X-Drop-Fault`

- 문법: `<service>:drop-after-commit=<n>[,…]`, n ≥ 1의 정수. 빈 값·공백뿐·없음 = 없음. 한 서비스를 가리키는 지시는 하나만. 공백은 쉼표로 나눈 지시의 앞뒤와 `:`의 앞뒤에만(`X-Attempt-Fault`와 같다). 그 밖은 틀린 헤더 → 서비스 400(행 없음), 분석기 입력 오류. 해석: `DropFaultDirectives.parse(raw: String?): List<DropFault>`(`DropFault(service: String, n: Int)`, 종류 이름 `DropFault.KIND` = `"drop-after-commit"`) — 틀리면 `IllegalArgumentException`.
- 입구 필터가 요청마다 한 번 정한다(`DropFaultInjection.decide`): 이 서비스를 가리키는 지시가 있으면 그 requestId로 이 서비스에 든 횟수 k(이번 것 포함, 1부터)를 세고 k ≤ n이면 끊기로 한다. 지시가 없으면 세지 않는다. 키의 상태와 무관하다(되돌려 주는 응답도 k ≤ n이면 끊긴다).
- 끊는 때: 처리 코드가 응답을 `FaultInjection.respond`로 낼 때(마지막 커밋 뒤). 응답을 **한 바이트도 보내지 않고** 연결을 닫는다(`closeWithoutResponse` — Tomcat 내부, `DropFault.kt`의 주석). 그때 센다. 끊긴 요청의 시각 기록 `status`는 0.
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
class Idempotency(val enabled: Boolean, keys: IdempotencyKeys, val counters: IdempotencyCounters) {
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

분석기(`lab.reliability.analyzer`): 명령줄 `runAnalyzer(arrayOf("idem", "--run", <폴더>, ["--expect", <파일>]))` → 종료 코드(`docs/measurement.md` 9절). 읽는 함수: `readIdemAnswers(path: Path): List<IdemSend>`, `readResends(path: Path, answerIds: Set<String>): List<IdemSend>?`(파일이 없으면 null), `readKeyCsv(path: Path): List<KeyCsvRow>`, 보고 `idempotencyReport(run: Path): Pair<Map<String, Any?>, List<String>>`(보고와 늘 견주는 것의 차이 줄).

## 7. 일부러 단순하게 둔 것

- 키의 만료·청소가 없다 — 실행마다 표를 비운다. 상주 서비스라면 보관 기간과 청소가 있어야 한다.
- 처리 중에 죽은 요청의 키는 `IN_PROGRESS`로 남아 그 키는 영원히 409다 — 회수(기한이 지난 `IN_PROGRESS`를 다시 잡기)를 넣지 않았다.
- 키와 금액만 견준다(본문의 다른 칸이 없다). requestId가 다른 같은 키는 막지 않는다 — 이어 하기는 처음 키 행의 업무 행을 쓴다.
- 끊김은 Tomcat 11.0.27의 내부(`ResponseFacade`의 보호된 필드, `setSuspended`, `ActionCode.CLOSE_NOW`)에 기댄다. 응답 본문이 Tomcat의 응답 버퍼(8KB)보다 크면 앞부분이 나갈 수 있다 — 이 실험의 응답은 100바이트 안팎이다.
