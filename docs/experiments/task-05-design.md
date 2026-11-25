# task 05 설계 — gRPC: 기한·취소 전파와 위 층이 포기한 뒤의 헛일

> 명세 9절 task 05의 계약을 실험으로 옮긴 것. **결과를 보기 전에** 썼다(2026-11-25). 결과와 예상의 대조는 `docs/results/task-05-grpc.md`.
> 무엇을 재려는지의 출처: 하네스 문서 `communication-reliability.md` 2절의 "잴 것" 가운데 기한 줄(C가 느릴 때 기한 전파 유무별 B와 C의 헛일)과 취소 전파.
> 노트북 한 대에서의 **상대 비교**다(명세 2절). 범위는 기한·취소 전파만이다(사용자 11-25 10:48) — 재시도·멱등 키·한도는 gRPC로 다시 재지 않고 모두 끈다.
> gRPC를 넣는 법은 사용자가 정했다: Spring Boot gRPC 스타터 + grpc-java의 blocking 스텁(코루틴 스텁 없음 — HTTP 쪽과 같은 "요청 하나 = 스레드 하나").

## 0. 같게 두는 것과 바꾸는 것

- 같게 둔다: task 02 실험 다의 부하 그대로 — 시나리오 `split-timeout`(20/s × 40s = 800건, 요청 i의 결제 지연 `[없음, 500, 1500, 2500][i % 4]`ms — 종류마다 200건), k6 요청 타임아웃 10s, k6의 연결 재사용은 켠 채(task 02 실험 다와 같다), 플랫폼 스레드, 자원 제한(서비스마다 CPU 2·768m, PostgreSQL CPU 2·1g), Hikari 풀 10·대기 1000ms, 워밍업, 재시도·멱등 키·한도 꺼짐.
- 부하 도구와 주문 사이는 늘 HTTP다. 바뀌는 것은 서비스 사이의 두 구간(주문 → 재고, 재고 → 결제)뿐이다.
- **조건 하나 = 서비스를 그 조건의 설정으로 다시 띄움 → 버리는 실행 1회(`r0`) → 잰 실행 1회.** 조건 넷을 한 바퀴 안에서 번갈아 돌고(4절의 차례), 세 바퀴를 돈다 — 조건마다 잰 실행 3회.
- 실제로 걸린 조건은 실행마다 `settings-<서비스>.json`(떠 있는 객체의 값)으로 `run.sh`가 견준다(다르면 실패 — 1절). 실제로 쓰인 전송은 전송 대조(`analyzer transport` — 1절)가 다시 본다.

## 1. 전송을 고르는 설정과 조용한 통과 막기

| 설정(환경 변수 → 속성) | 값 | 뜻 |
|---|---|---|
| `CHAIN_TRANSPORT` → `chain.transport` | `http`(기본) · `grpc` | 서비스 사이의 전송. 세 서비스에 같은 값 |
| `GRPC_DEADLINE_PROPAGATION` → `chain.grpc.deadline-propagation` | `false`(기본) · `true` | 받은 기한·취소를 아래 호출에 넘기나(G2·G3) |
| `GRPC_OBSERVE_CANCELLATION` → `chain.grpc.observe-cancellation` | `false`(기본) · `true` | 처리 코드가 취소와 남은 기한을 보나(G3) |

- `http`면 task 01~04-b와 같은 객체가 뜬다(`HttpNextLayerCall`, gRPC 서버 없음 — Boot의 gRPC 서버 자동 구성은 `BindableService` 빈이 있을 때만 돈다). 위의 두 gRPC 조건은 `http`에서 켜면 기동이 실패한다. `grpc`에서 재시도·멱등 키·한도를 켜도 기동이 실패한다(잰 적 없는 조합을 조용히 재지 않게).
- `grpc`면 주문과 재고가 아래 층을 gRPC blocking 스텁으로 부르고(`GrpcNextLayerCall`), 재고와 결제가 gRPC 서버(Netty, 포트 9090 — Compose 네트워크 안에서만)를 띄운다. 업무 처리는 HTTP 컨트롤러와 같은 메서드다(`LayerHandler` — 재고의 예약, 결제의 승인).
- **조용한 통과 막기** — 세 겹이다:
  1. `run.sh`가 세 서비스의 `/settings`에서 고른 전송(`transport.chosen`), 아래 층 호출의 구현(`nextLayerCall` — `GrpcNextLayerCall`/`HttpNextLayerCall`), gRPC 서버가 떠 있는지(`grpcServerRunning`), gRPC 조건 둘을 그 실행의 조건과 견준다. 다르면 실행 실패(종료 1).
  2. 서비스가 **전송마다 호출 수를 센다**(`GET /transport-counts` — 들어온 HTTP는 입구 필터의 맨 앞, 들어온 gRPC는 서비스 정의를 감싼 서버 인터셉터, 나간 HTTP는 HTTP 클라이언트가 보내기 직전, 나간 gRPC는 채널에 건 클라이언트 인터셉터). 시각 기록을 남기는 자리와 다른 자리다.
  3. 분석기 `analyzer transport`(모든 시나리오에서 돈다 — `docs/measurement.md` 11절)가 고른 전송 · 센 수 · 시각 기록의 `transport` 칸을 견준다. gRPC를 골랐는데 재고나 결제가 HTTP로 받은 호출이 하나라도 있거나(그 반대도), 센 수와 기록의 수가 다르면 불일치(종료 2).
- 기한 전파가 실제로 걸렸는지도 기록에서 본다: gRPC 서버는 받은 기한의 절대 시각을 시각 기록에 남긴다(`deadlineUs`). 기한을 넘기면 결제의 기한은 재고의 기한을 넘을 수 없다(아래 호출의 기한 = 자기 값과 받은 기한 가운데 이른 것). 전파를 켰는데 결제의 기한이 재고의 기한보다 50ms 넘게 늦은 기록이 있으면 불일치다. 처리 코드가 취소를 보지 않는 조건(H·G1·G2)에서 멈춘 기록(`stopped`)이 있어도 불일치다.

## 2. gRPC의 끝을 기존 갈래로 읽는 규칙(완료 / 실패 / 결과 모름)과 그 까닭

정본은 `chain-link/…/GrpcRules.kt`의 `GrpcOutcomes`(순수 함수 — 9절의 테스트 입구).

**서버(불린 쪽)가 닫는 꼴**: 처리 코드의 응답 상태는 HTTP 경로와 같은 수다(201 처리함 · 502 아래가 처리 안 함 · 504 모름 · 500 넣은 오류와 커밋 뒤 실패 · 400 헤더 오류). 2xx면 `OK`, 아니면 `FAILED_PRECONDITION` + 트레일러 `chain-status: <수>`.
- `FAILED_PRECONDITION`을 고른 까닭: grpc-java 라이브러리가 스스로 내는 코드(`CANCELLED`, `DEADLINE_EXCEEDED`, `UNAVAILABLE`, `UNKNOWN`, `INTERNAL`, `RESOURCE_EXHAUSTED`, `UNIMPLEMENTED`)와 겹치지 않는다 — 처리 코드가 답한 끝과 라이브러리가 만든 끝이 섞이지 않게. 갈래는 트레일러의 수로 읽고 코드 이름으로 읽지 않는다.

**부른 쪽이 읽는 규칙**(차례대로 처음 맞는 것):

| 끝 | 결과 | 시각 기록의 `callOutcome` | 까닭 |
|---|---|---|---|
| 트레일러에 `chain-status`가 있음 | HTTP와 같은 규칙: 2xx 완료 · 504 결과 모름 · 그 밖 실패 | `Completed` · `Unknown:CHAIN_504` · `Failed:CHAIN_<n>` | 아래 층의 처리 코드가 답했다 — HTTP 경로에서 받은 응답 상태와 같은 정보다. 보정의 기대 건수가 두 전송에서 같아야 하는 근거 |
| 트레일러 없이 `OK` | 완료 | `Completed` | 이 서버는 2xx일 때만 OK로 닫는다 |
| 트레일러 없이 `DEADLINE_EXCEEDED` | **결과 모름** | `Unknown:DEADLINE_EXCEEDED` | 부른 쪽의 기한이 지났다(HTTP의 요청 타임아웃과 같다). 아래 층은 처리했을 수도 안 했을 수도 있다 |
| 트레일러 없이 `CANCELLED` | **결과 모름** | `Unknown:CANCELLED` | 위의 취소가 전해져 이 호출이 끊겼다(G2·G3에서 재고가 결제를 부르는 중에 주문이 포기한 경우). 아래 층이 이미 처리했을 수 있다 |
| 트레일러 없이 `UNAVAILABLE` | **결과 모름** | `Unknown:UNAVAILABLE` | grpc-java는 연결을 맺지 못한 것과 맺은 뒤 끊긴 것을 같은 코드로 낸다. HTTP 경로는 앞의 것(연결 거절·연결 타임아웃)을 실패로 읽지만, gRPC에서는 가를 수 없으므로 처리했을 수 있는 쪽(결과 모름)으로 읽는다 — 처리된 것을 실패로 세면 "실패인데 처리됨"이 가려진다 |
| 트레일러 없이 그 밖의 코드 | **결과 모름** | `Unknown:<코드 이름>` | 처리 코드가 답하지 않은 끝 — 처리했는지 모른다 |

- 정리하면 **처리 코드가 답한 끝은 HTTP와 같게, 답하지 않은 끝은 모두 결과 모름**이다. 결과 모름이 성공이나 실패로 세지지 않는 것(명세 2절)은 그대로다.
- 시각 지표에서 "위 층이 포기했다"(`giveUp`, `docs/measurement.md` 7-3)로 보는 `callOutcome`을 `Unknown:REQUEST_TIMEOUT`에서 그것과 `Unknown:DEADLINE_EXCEEDED`·`Unknown:CANCELLED`로 넓힌다. HTTP 경로의 기록에는 뒤의 둘이 나오지 않으므로 HTTP 실행의 값은 바뀌지 않는다.
- 위 층이 자기 행을 정하는 법, 클라이언트에 내는 응답 상태(201·502·504)는 바뀌지 않는다 — 결과(`LayerOutcome`)가 같은 갈래로 오기 때문이다.

**메타데이터**: `X-Request-Id`·`X-Fault`·`X-Attempt-Fault`·`X-Drop-Fault`·`Idempotency-Key`를 같은 이름의 소문자 메타데이터로 받은 그대로 넘긴다(`GrpcHeaders`). gRPC 입구는 HTTP 입구 필터와 같은 부품으로 `X-Request-Id`가 없거나 `X-Fault`가 틀리면 400(위의 규칙대로 `Failed:CHAIN_400`)으로 닫고, 이 서비스를 가리키는 `X-Attempt-Fault`·`X-Drop-Fault`도 400으로 닫는다(재시도·끊김은 이 task의 범위 밖 — 넘겨주기만 한다).

## 3. 조건 넷 — 무엇을 바꾸나(조건 사이에 하나씩)

| 조건 | 전송 | 위가 건 기한을 아래로 넘기나 | 처리 코드가 취소·남은 기한을 보나 | 앞 조건과 다른 것 하나 |
|---|---|---|---|---|
| **H** | HTTP | — (구간마다 미리 나눈 타임아웃) | 아니오 | 기준선(task 02 실험 다를 다시 잰다) |
| **G1** | gRPC | 아니오 — 구간마다 같은 값을 그 호출의 기한으로 따로 건다 | 아니오 | 전송(H → G1) |
| **G2** | gRPC | 예 — 받은 기한이 아래 호출에도 걸리고, 위의 취소가 아래 호출을 끊는다 | 아니오 | 기한·취소 전파(G1 → G2) |
| **G3** | gRPC | 예 | 예 | 처리 코드가 취소를 봄(G2 → G3) |

- **G1의 "넘기지 않음"**: 재고는 결제를 부를 때 받은 문맥을 `Context.fork()`로 끊는다(grpc-api 1.84.1 소스: `fork`는 값을 넘기되 취소할 수 있는 조상을 두지 않아 기한이 없고 위의 취소가 닿지 않는다). 그래서 재고의 결제 호출에는 재고 자신의 값(`INVENTORY_TO_PAYMENT_TIMEOUT`)만 기한으로 걸린다 — HTTP의 미리 나눈 타임아웃과 같은 꼴이다. 주문은 맨 위라(부하 도구와는 HTTP) 넘길 것이 없다.
- **G2의 "넘김"**: 재고가 받은 문맥 그대로 결제를 부른다. 재고 자신의 값도 그대로 걸고, grpc-java가 둘 가운데 이른 것을 쓴다(`ClientCallImpl.effectiveDeadline = min(호출의 기한, 문맥의 기한)`) — 그래서 "남은 시간이 아래 호출의 기한이 된다"는 받은 기한이 더 이를 때다. 주문이 기한에 포기하면 재고의 문맥이 취소되고, 재고가 기다리던 결제 호출도 grpc-java가 끊는다(`CANCELLED` 또는 `DEADLINE_EXCEEDED`). **처리 코드는 취소를 보지 않는다** — 넣은 지연은 HTTP와 같은 `Thread.sleep`이고, 끊긴 호출의 결과로 재고는 행을 `UNKNOWN`으로 마친다.
- **G3의 "봄"**(gRPC로 받는 재고·결제의 입구 — `GrpcChainLayerService`): ① 입구에서 이미 취소됐거나 남은 기한이 0 이하면 아무것도 시작하지 않는다(`stopped = deadline-before-start`) ② 넣은 지연은 문맥의 취소를 기다리며 자고, 기다리는 중에 취소되면 멈춘다(`cancelled-in-delay`) ③ 지연 뒤, 처리(첫 DB 트랜잭션) 앞에서 한 번 더 본다(`deadline-before-work`). 멈추면 행을 만들지 않고 응답하지 않는다(시각 기록의 `status` 0).
  - **DB 트랜잭션과의 경계**: 멈추는 자리(①②③)는 모두 첫 트랜잭션보다 앞이다 — 그래서 멈추면 되돌릴 것이 없다. 처리를 시작한 뒤에는 취소를 보지 않는다: 결제의 승인(한 트랜잭션)은 시작하면 커밋까지 가고, 재고는 PENDING 행을 커밋한 뒤 결제를 부르며, 그 호출이 gRPC에 의해 끊기면 그 결과로 행을 `UNKNOWN`으로 커밋한다. 트랜잭션 안에서 취소가 와도 SQL을 끊지 않는다(`Statement.cancel`을 쓰지 않는다). 즉 "어디까지 가면 되돌리나" = 없다(시작 전에만 멈춘다), "어디부터 커밋되나" = 첫 트랜잭션을 시작한 뒤의 모든 것.
- **grpc-java가 스스로 하는 것(G1·G2·G3 모두)**: 서버의 실행기 큐에서 기다리는 동안 취소된 호출(부른 쪽이 끊었거나 받은 기한이 지남)은 처리 코드가 불리지 않는다(grpc-core 1.84.1 `ServerCallImpl.ServerStreamListenerImpl.halfClosed` — 호출이 취소됐으면 돌아간다. unary의 처리 코드는 halfClose에서 불린다). 설계를 쓰기 전의 스모크(결제가 차는 `slow-layer` 부하, G1, 한 번)에서 결제가 받은 3000건 가운데 343건이 그렇게 처리 코드에 닿지 않았다 — 그래서 그 호출은 시각 기록이 없고 넣은 지연도 세지 않는다. 서버가 그 수를 따로 센다(`/transport-counts`의 `grpcCancelledBeforeHandler` — 전송 대조가 "센 수 = 기록 수 + 이것"으로 견준다). 이 task의 실험 부하(동시 처리 ≈ 50)는 실행기가 차지 않으므로 0이 예상된다. 이 몫은 G3의 ①과 겹치는 일(포기한 뒤에 시작하지 않기)을 라이브러리가 큐 단계에서 하는 것이고, HTTP(Tomcat)에는 없다(task 02 나-2에서 결제가 찼을 때 헛일의 약 89%가 포기한 뒤에 시작한 일이었다).
- 모든 gRPC 조건에서 서버는 이 호출의 문맥이 취소된 것을 안 시각을 기록한다(`cancelledUs` — 위가 끊었거나 받은 기한이 지났다. 기록만 하고 G1·G2에서는 아무것도 바꾸지 않는다). 그래서 "취소가 아래 층에 닿기까지의 시간"을 잴 수 있다: `cancelledUs − giveUp`(5절). HTTP 경로에서는 잴 수 없다 — 이 구현의 HTTP 서버(Tomcat + 서블릿)는 부른 쪽이 끊은 것을 처리 코드에 알리지 않는다(응답을 쓸 때에야 드러난다).
- 숨은 기본값(확인한 곳은 `docs/dependencies.md` "task 05"): 채널의 재시도 끔(`disableRetry()` — 서비스 설정의 정책과 투명한 재시도 모두), 서버 실행기는 상한 있는 고정 크기(최대 200 = Tomcat의 최대 스레드, 큐는 상한 없음 — Tomcat의 큐도 상한이 없다), 채널의 기본 기한 없음(기한은 코드에서 요청마다), 관측(서버·클라이언트) 끔, 리플렉션 끔, gRPC 헬스 끔. 실제 값은 `/settings`의 `nextLayer`(채널 객체의 `retryEnabled`를 반사로 읽음)와 `grpc.server`(포트, 등록된 서비스, 실행기, 서버 객체가 그 실행기를 쓰는지).

## 4. 배치 둘과 돌리는 차례

| 배치 | 주문 → 재고 | 재고 → 결제 | 뜻 |
|---|---|---|---|
| **P1** | 2000ms | 1000ms | 아래 구간이 위 층 안에 든다(task 02 다-1) |
| **P2** | 1000ms | 2000ms | 아래 구간이 위 층보다 길다(task 02 다-2) |

- gRPC 조건에서 이 값은 그 구간 호출의 기한이다(G1은 그것만, G2·G3은 그것과 받은 기한 가운데 이른 것).
- 차례(바퀴마다 조건 넷을 번갈아 — 같은 차례만 되풀이하면 차례의 몫과 가를 수 없다): 바퀴 1 = H → G1 → G2 → G3, 바퀴 2 = G3 → G2 → G1 → H, 바퀴 3 = G1 → G3 → H → G2. 한 바퀴 안에서 P1의 넷을 돈 뒤 P2의 넷을 돈다. 조건을 바꿀 때마다 다시 띄우고 `r0`을 버린다. 실행 48개(버리는 실행 24 + 잰 실행 24), 실행 앞뒤의 load average를 `config.env`(`host_loadavg_start`·`_end`)와 `runs/task-05-uptime.txt`에 남긴다.
- 실험 앞의 보정은 5·6절.

## 5. 재는 것

`docs/measurement.md` 7절(task 02의 헛일 지표 그대로)과 11절(이 task에서 더한 것). 층 L(재고·결제)마다:

| 지표 | 뜻 |
|---|---|
| `waste.<L>.wasted` · `wastedProcessed` · `wastedMsSum` | 위 층이 포기한 뒤에도 L이 계속한 일의 건수 · 그 가운데 L이 처리 행을 커밋한 수 · 시간 합(task 02 그대로 — `giveUp`의 갈래만 2절대로 넓힘) |
| `waste.<L>.startedAfterGiveUp` | 포기한 뒤에 **시작한** 일의 수 |
| `waste.<L>.stopped` | 헛일 가운데 G3에서 멈춘 것 |
| `waste.<L>.cancelSeen` · `cancelAfterGiveUpMs` · `endAfterCancelMs` | 헛일 가운데 L이 취소를 안 것의 수 · 안 시각 − 위가 포기한 시각(취소가 L에 닿기까지) · 끝 − 안 시각(안 뒤에도 계속한 시간) |
| `layers.<L>.stopped.<자리>` | G3에서 멈춘 기록의 수(자리마다) |
| 대조 분석 `layers.<L>.unknownProcessed`·`failedButProcessed` | **클라이언트는 결과 모름·실패인데 L에 처리 행이 있는 요청 수** |
| 전송 대조 `deadline.paymentAfterInventoryMs` · `…OverSlack` | 결제가 받은 기한 − 재고가 받은 기한(분위수), 50ms를 넘는 수 |
| 전송 대조 `counts.<L>.cancelledBeforeHandler.grpc` | 받았지만 처리 코드에 닿기 전에 취소돼 grpc-java가 처리 코드를 부르지 않은 호출 수 |

## 6. 보정 — 답을 아는 실행(실험 전에, 통과해야 수치를 쓴다)

| 실행 | 조건 | 기대 | 기대 파일 |
|---|---|---|---|
| HTTP 기본 | `calibration`, `cal-false-timeout` T=300·T=1000 | task 01·02와 같다(기존 기대 파일 그대로) — HTTP일 때 동작이 그대로인지 | `calibration.json`, `cal-false-timeout-t300/t1000.timing.json` |
| G1 | 같은 셋 | **HTTP와 같은 기대 건수**(같은 파일). 2절의 규칙이 맞으면 갈래가 같다 | 같은 파일 |
| G2 | `calibration` | G1과 하나만 다르다: `inventory:delay=2500`(재고가 2.5s 자는 동안 주문이 2000ms에 포기) 50건이 `unknown \| UNKNOWN \| RESERVED \| APPROVED` → **`unknown \| UNKNOWN \| UNKNOWN \| -`**(재고가 깨어 결제를 부를 때 문맥이 이미 취소돼 호출이 바로 끊긴다). `layerMismatch` 200 → 150 | `calibration-grpc-g2.json` |
| G3 | `calibration` | G2에서 둘이 더 다르다: `inventory:delay=2500` → **`unknown \| UNKNOWN \| - \| -`**(재고가 지연 중에 멈춰 행을 만들지 않는다), `payment:delay=1500`(재고가 1000ms에 포기) → **`unknown \| UNKNOWN \| UNKNOWN \| -`**(결제가 지연 중에 멈춘다). `layerMismatch` 100. 넣은 지연은 기다리기 시작할 때 세므로 장애 수는 그대로 일치 | `calibration-grpc-g3.json` |
| G3 | `cal-false-timeout` T=300(모든 결제 600ms, 재고 → 결제 300ms, 주문 → 재고 5000ms) | 재고 → 결제 타임아웃 400, **거짓 타임아웃 0**(결제 행 0), 결제의 헛일 가운데 처리 행 커밋 0, 결제의 헛일 시간 합 ≤ 8000ms(건당 20ms 이하), 결제가 지연 중에 멈춘 기록 400. 대조 분석: `payment:delay=600` 400건 모두 `unknown \| UNKNOWN \| UNKNOWN \| -` | `cal-false-timeout-grpc-g3.timing.json`, `cal-false-timeout-grpc-g3.json` |

## 7. 예상(보기 전에) — 수치 범위와 판정 규칙

**설계에서 정해지는 값과 재는 값을 가른다.** 아래 표의 건수와 "넣은 지연 − 경로의 가장 짧은 타임아웃"으로 나오는 시간 합은 **이 섞음과 이 타임아웃 값에서 산수로 정해지는 값**이다 — 맞으면 도구와 조건이 규칙대로 걸렸다는 뜻이지 새로 안 것이 아니다. 새로 재는 것은 ① G3에서 취소가 일을 얼마나 빨리 멈추는가(헛일 시간의 남은 몫, `endAfterCancelMs`) ② 취소가 아래 층에 닿기까지의 시간(`cancelAfterGiveUpMs`) ③ 기한·취소가 경계에서 새는 몫(G3인데 처리 행이 커밋된 수, 헛일로 세진 수의 흔들림) ④ H와 G1의 차(프로토콜과 서버가 함께 바뀐 차 — 한계 참조)다.

넣은 지연 1500·2500ms의 400건만 헛일이 생긴다(없음·500ms짜리는 두 배치 모두 모든 구간 안에 끝난다). 칸은 실행 하나의 값.

### P1 — 주문 2000 / 재고 → 결제 1000

재고가 1000ms에 포기하고 504로 답한다 — 주문은 2000 안에 답을 받으므로 포기하지 않는다. 그래서 P1에서는 받은 기한(주문의 2000)이 결제 호출의 기한(1000)보다 늦어 **G2가 G1과 같은 동작**이다(전파가 바꾸는 것이 없다 — 정해지는 결과).

| 지표 | H | G1 | G2 | G3 |
|---|---|---|---|---|
| 결제 `wasted` | 400 | 400 | 400 | 0~400(재는 값 — 결제가 멈춘 때와 재고가 포기한 때의 경쟁) |
| 결제 `wastedProcessed` | 400 | 400 | 400 | **0** |
| 결제 `wastedMsSum`(s) | 395~415 | 395~415 | 395~415 | **≤ 8**(건당 20ms 이하) |
| 결제 `layers.payment.stopped.cancelled-in-delay` | 0 | 0 | 0 | 400 |
| 결제 `cancelAfterGiveUpMs` p50(ms) | 없음(HTTP) | 0~20 | 0~20 | 0~20 |
| 재고 `wasted` | 0 | 0 | 0 | 0 |
| 결제 `unknownProcessed`(클라이언트는 결과 모름인데 결제 행 있음) | 400 | 400 | 400 | **0** |
| `deadline.paymentAfterInventoryOverSlack` | 없음 | 0 | 0 | 0 |
| `startedAfterGiveUp`(두 층) | 0 | 0 | 0 | 0 |
| 처리 코드에 닿기 전에 취소된 gRPC 호출(`counts.<L>.cancelledBeforeHandler.grpc`) | 0(HTTP) | 0 | 0 | 0 |

### P2 — 주문 1000 / 재고 → 결제 2000

주문이 1000ms에 포기한다. H·G1은 재고가 자기 값(2000)대로 결제를 기다린다 — 1500짜리는 1500에 `RESERVED`, 2500짜리는 2000에 포기.

| 지표 | H | G1 | G2 | G3 |
|---|---|---|---|---|
| 재고 `wasted` | 400 | 400 | 0~400(재는 값) | 0~400 |
| 재고 `wastedProcessed` | 200 | 200 | **0** | **0** |
| 재고 `wastedMsSum`(s) | 295~315 | 295~315 | **≤ 8** | **≤ 8** |
| 결제 `wasted` | 400 | 400 | 400 | 0~400 |
| 결제 `wastedProcessed` | 400 | 400 | **400**(전파만으로는 줄지 않는다) | **0** |
| 결제 `wastedMsSum`(s) | 395~415 | 395~415 | **395~415** | **≤ 8** |
| 결제 `cancelSeen`(헛일 가운데) | 없음 | **200**(2500짜리만 — 재고의 2000이 지나야 취소가 온다) | 400 | 0~400 |
| 결제 `cancelAfterGiveUpMs` p50(ms) | 없음 | **950~1050**(재고의 2000 − 주문의 1000) | 0~20 | 0~20 |
| 결제 `endAfterCancelMs` p50(ms) | 없음 | 450~550(2500짜리: 2500 − 2000) | 450~1550(무리 둘 — 대표값 아님) | 0~20 |
| 주문 → 재고 `falseTimeouts` | 200 | 200 | **0** | **0** |
| 재고 `unknownProcessed` · 결제 `unknownProcessed` | 200 · 400 | 200 · 400 | 0 · **400** | 0 · **0** |
| `deadline.paymentAfterInventoryOverSlack` | 없음 | **800**(모든 결제 호출 — 결제의 기한이 재고의 기한보다 약 1000ms 늦다) | 0 | 0 |
| 처리 코드에 닿기 전에 취소된 gRPC 호출 | 0(HTTP) | 0 | 0 | 0 |

### 판정 규칙

- 조건 사이의 차를 말하려면 두 조건의 세 반복 범위(최소~최대)가 겹치지 않아야 한다. 겹치면 "가를 수 없다"고 적는다. 반복 3회로 범위가 겹치지 않는 것은 차이가 없어도 일어날 수 있다(3 대 3에서 10%) — 그래서 위 표의 "정해지는 값"이 아닌 재는 값의 차는 크기와 함께 "이 자료에서 그랬다"로만 적는다.
- 예상 범위 밖이면 결과 문서에 틀렸다고 그대로 적고, 까닭을 원시 기록으로 확인한 만큼만 적는다(확인하지 못하면 모른다고).
- 건수의 예상이 틀리면(예: G3인데 결제 처리 행이 0이 아님) 먼저 도구와 조건을 의심하고 원시 기록(시각 기록의 `cancelledUs`·`stopped`, DB 행)으로 그 요청들을 본다.

## 8. 줄인 것과 까닭

- 실험은 task 02 실험 다의 부하 하나다. 아래 층이 찬 경우(위 3절의 스모크 — 한 번만 돌렸고 결과 표에 넣지 않는다)(task 02 실험 나 — "포기한 뒤 시작한 일"이 대부분인 자리)는 재지 않는다: G3의 ①(남은 기한이 없으면 시작하지 않음)이 막을 몫은 이 부하에서 0으로 예상된다(task 02 다의 `startedAfterGiveUp` 0). 그 몫을 재려면 결제가 차는 부하가 필요하다 — 결과 문서에 "말할 수 없는 것"으로 적는다.
- 처리 코드가 보는 취소는 넣은 지연(잠)에서만이다. DB 쿼리·외부 호출의 취소는 재지 않는다.
- 보정은 G1 셋, G2 하나, G3 둘. G2의 `cal-false-timeout`은 주문의 5000ms가 결제 호출의 기한(300·1000)보다 늦어 G1과 같은 동작이 정해지므로 돌리지 않는다.
- 재시도·멱등 키·한도는 끈다(사용자의 답 — gRPC로 다시 재지 않는다).

## 9. 단위 테스트의 입구 — Spring·네트워크 없이 부르는 것

구현을 보지 않는 워커가 이 문서와 `docs/measurement.md` 11절에서 테스트를 쓴다. 모두 `chain-link` 모듈, 패키지 `lab.reliability.chain`(`GrpcRules.kt`). gRPC 타입은 `io.grpc`(grpc-api — `chain-link`의 의존성).

| 입구 | 서명 | 지키는 규칙 |
|---|---|---|
| 전송 해석 | `Transport.parse(raw: String): Transport` · `Transport.label` | `"http"`·`"grpc"`(앞뒤 공백 무시) 밖은 `IllegalArgumentException` |
| 서버가 닫는 꼴 | `GrpcOutcomes.close(httpStatus: Int): GrpcOutcomes.Close` — `Close(code: Status.Code, chainStatus: Int?)` | 2xx → `(OK, null)`, 그 밖 → `(FAILED_PRECONDITION, httpStatus)` |
| 끝을 결과로 읽기 | `GrpcOutcomes.read(code: Status.Code, chainStatus: Int?): LayerOutcome` | 2절의 표(차례대로). `chainStatus`가 있으면 코드는 보지 않는다 |
| 트레일러 읽기 | `GrpcOutcomes.chainStatusOf(trailers: Metadata?): Int?` · `GrpcOutcomes.CHAIN_STATUS`(키 `chain-status`) | 없거나 정수가 아니면 null(앞뒤 공백은 무시) |
| 메타데이터 | `GrpcHeaders.outbound(requestId: String, rawFault: String?, rawAttemptFault: String?, rawDropFault: String?, idempotencyKey: String?): Metadata` · `GrpcHeaders.read(md: Metadata): GrpcHeaders.Inbound` | 키는 HTTP 헤더 이름의 소문자(`x-request-id`, `x-fault`, `x-attempt-fault`, `x-drop-fault`, `idempotency-key`). null인 것은 붙이지 않는다. 읽기는 없으면 null. 값은 바꾸지 않는다 |
| 아래 호출의 문맥 | `GrpcDeadlines.downstreamContext(current: Context, propagate: Boolean): Context` | true → `current` 그대로(같은 객체). false → 기한이 없고(`deadline == null`) `current`가 취소돼도 취소되지 않는 문맥 |
| 시작해도 되나 | `GrpcDeadlines.shouldStart(cancelled: Boolean, remainingNanos: Long?): Boolean` | 취소됐으면 false, 남은 기한 ≤ 0이면 false, 기한 없음(null)이면 true |
| 기한의 절대 시각 | `GrpcDeadlines.absoluteDeadlineUs(nowUs: Long, remainingNanos: Long?): Long?` | `nowUs + floor(remainingNanos / 1000)`, null이면 null(음수 남은 기한도 그대로 더한다) |
| 취소를 보며 자기 | `GrpcDeadlines.awaitDelay(ms: Long, cancelled: CountDownLatch): Boolean` | 다 잤으면 true, 그 사이 `cancelled`가 열리면(이미 열려 있으면 바로) false |
| 멈춘 자리 | `StopReason.BEFORE_START`·`IN_DELAY`·`BEFORE_WORK`·`ALL` | 값 `deadline-before-start`·`cancelled-in-delay`·`deadline-before-work` |

입구가 없어 보정·실험 실행으로만 보는 것: gRPC 입구(`GrpcChainLayerService` — 넣은 장애, 멈추는 차례, 시각 기록의 칸), 채널 맞춤(`disableRetry`)과 서버 실행기, `/settings`의 반사 읽기. 분석기 쪽(`analyzer transport`, 시각 지표의 새 칸)은 `docs/measurement.md` 11절이 정본이다.

## 10. 더 재는 조건 G1o — 리뷰 뒤(2026-11-25, 결과를 보기 전에 썼다)

> 까닭: 3절의 조건 넷으로는 G3의 이득이 **기한 전파**(G1 → G2)의 몫인지 **아래 층이 받은 기한·취소를 본 것**의 몫인지 가를 수 없다. P1에서 결제가 지연 중에 멈춘 것은 재고가 건 자기 구간의 기한(1000ms)이 결제의 문맥에서 지났기 때문이다 — 전파를 끈 G1에서도 결제는 그 기한을 받는다(`withDeadlineAfter`). 그래서 G1에 관찰만 더한 조건을 하나 더 잰다. 하네스 문서의 세 줄(기한 전파 / 취소 전파 / 구간마다 나눈 타임아웃)의 경계가 이것이다.

### 10-1. G1o가 바꾸는 것

| 조건 | 전송 | 위가 건 기한을 아래로 넘기나 | 처리 코드가 받은 기한·취소를 보나 | 견주는 조건과 다른 것 하나 |
|---|---|---|---|---|
| **G1o** | gRPC | 아니오 — G1과 같다(재고는 결제를 부를 때 `Context.fork()`) | 예 — G3과 같은 코드(`GrpcChainLayerService`의 ①②③) | G1에서 관찰(G1 → G1o) · G3에서 전파(G1o → G3) |

- 설정: `CHAIN_TRANSPORT=grpc`, `GRPC_DEADLINE_PROPAGATION=false`, `GRPC_OBSERVE_CANCELLATION=true`(세 서비스에 같은 값). **설정의 조합으로 된다** — 서비스의 검사(`GrpcConfiguration.transport`)는 HTTP 전송에서만 두 값을 막고, `run.sh`도 같다. 코드는 바꾸지 않는다(`scripts/task05-all.sh`에 조건 이름과 차례만 더한다).
- 층마다 받는 기한: **재고**는 주문이 건 기한(주문 → 재고의 값)을 받는다 — 주문은 맨 위라 G1에서도 자기 값을 기한으로 건다. **결제**는 재고가 건 자기 값(재고 → 결제)만 받는다 — 위의 기한·취소는 닿지 않는다. 두 층이 그 받은 기한을 본다(지연 앞 ① · 지연 중 ② · 처리 앞 ③).
- 재고의 관찰이 하는 일: 이 섞음에서 재고에는 넣은 지연이 없다 — 재고의 일은 결제 호출을 기다리는 것이고, 처리 코드는 **그 기다림을 보지 않는다**(G3도 같다. G3에서 재고의 기다림을 끊는 것은 전파된 문맥이 취소되며 grpc-java가 결제 호출을 끊는 것이지 처리 코드가 아니다 — 3절). 그래서 재고에서 G1o의 관찰이 바꾸는 것은 ①③(시작 앞에 이미 기한이 지났나)뿐이고, 이 부하에서는 0으로 예상한다(`startedAfterGiveUp` 0).

### 10-2. 배치마다의 예상

**정해지는 값**(산수)과 **재는 값**을 가른다. 넣은 지연 1500·2500ms의 400건만 헛일이 생긴다. 칸은 실행 하나의 값. G1·G3의 칸은 7절과 같은 예상이다(이 바퀴에서 다시 잰다).

**P1 — 주문 2000 / 재고 → 결제 1000.** 결제가 받은 기한은 G1o에서 1000(자기 값), G3에서 min(1000, 주문의 남은 ≈ 2000) = 1000 — **같다**. 그래서 결제의 동작은 G1o와 G3이 같다고 정해진다: 1500·2500짜리 400건이 1000ms에 지연 중 멈추고 처리 행을 만들지 않는다. 재고는 1000ms에 결제 호출이 끝나 504로 답하므로 주문이 포기하지 않는다(재고 헛일 0 — 7절).

| 지표 | G1 | G1o | G3 |
|---|---|---|---|
| 결제 `wasted` | 400 | 0~400(재는 값 — 7절의 경쟁) | 0~400 |
| 결제 `wastedProcessed` | 400 | **0** | **0** |
| 결제 `wastedMsSum`(s) | 395~415 | **≤ 8** | **≤ 8** |
| 결제 `layers.payment.stopped.cancelled-in-delay` | 0 | **400** | **400** |
| 결제 `unknownProcessed` | 400 | **0** | **0** |
| 재고 `wasted` | 0 | 0 | 0 |
| `deadline.paymentAfterInventoryOverSlack` | 0 | 0 | 0 |

- **P1의 물음 — G1o의 결제 헛일은 G3과 같은가**: 건수·처리 행·멈춘 수는 같다고 정해진다(위). 재는 것은 남은 몫(헛일 시간 합 ≤ 8s 안의 크기, `endAfterCancelMs`)이고, 두 조건의 코드 경로가 같으므로 **범위가 겹칠 것**(가를 수 없음)으로 예상한다. 겹치지 않으면 그 크기를 적고 까닭은 원시 기록으로 본 만큼만 적는다.

**P2 — 주문 1000 / 재고 → 결제 2000.** 주문은 1500·2500짜리 모두 1000ms에 포기한다. G1o에서 결제가 받은 기한은 자기 값 2000이다 — 그래서 **2000ms보다 긴 지연(2500짜리 200건)에서만 2000ms에 멈추고**, 1500짜리 200건은 기한 안에 끝나 처리 행을 커밋한다. 재고는 결제의 답을 기다린다 — 1500짜리는 1500ms에 `RESERVED`, 2500짜리는 결제가 멈춘(또는 재고의 기한이 지난) 2000ms에 `UNKNOWN`.

| 지표 | G1 | G1o | G3 |
|---|---|---|---|
| 결제 `wasted` | 400 | 400 | 0~400 |
| 결제 `wastedProcessed` | 400 | **200**(1500짜리) | **0** |
| 결제 `wastedMsSum`(s) | 395~415 | **295~315**(200 × 500 + 200 × 1000) | **≤ 8** |
| 결제 `layers.payment.stopped.cancelled-in-delay` | 0 | **200**(2500짜리) | 400 |
| 결제 `cancelSeen`(헛일 가운데) | 200 | 200(2500짜리 — 자기 기한 2000에) | 0~400 |
| 결제 `cancelAfterGiveUpMs` p50(ms) | 950~1050 | 950~1050(2000 − 1000) | 0~20 |
| 재고 `wasted` | 400 | 400 | 0~400 |
| 재고 `wastedProcessed` | 200 | **200** | **0** |
| 재고 `wastedMsSum`(s) | 295~315 | **295~315**(G1과 같다 — 200 × 500 + 200 × 1000) | ≤ 8 |
| 주문 → 재고 `falseTimeouts` | 200 | 200 | 0 |
| 재고 · 결제 `unknownProcessed` | 200 · 400 | 200 · **200** | 0 · 0 |
| `deadline.paymentAfterInventoryOverSlack` | 800 | 800 | 0 |
| `startedAfterGiveUp`(두 층) · 처리 코드에 닿기 전에 취소된 gRPC 호출 | 0 · 0 | 0 · 0 | 0 · 0 |

- **P2의 물음 — G1o는 G3과 얼마나 다른가**: 정해진다. 결제의 헛일 합은 G1 ≈ 400s → G1o ≈ 300s → G3 ≈ 0(2500짜리를 2000에서 멈춘 몫 200 × 500ms = 100s만 관찰이 줄이고, 나머지 ≈ 300s는 전파된 기한이 있어야 준다). 재고의 헛일은 G1o에서 줄지 않는다(≈ 300s — 위의 "재고의 관찰이 하는 일").
- 그래서 이 조건으로 **나누어 말할 몫**(산수로 정해진다 — 맞으면 조건이 규칙대로 걸렸다는 뜻): P1의 결제 헛일 ≈ 400s 가운데 관찰(G1 → G1o)의 몫 ≈ 400s, 전파(G1o → G3)의 몫 0. P2의 결제 ≈ 400s 가운데 관찰 ≈ 100s, 전파 ≈ 300s. P2의 재고 ≈ 300s는 모두 전파의 몫.
- **새 지표 `waste.<L>.cancelBeforeDeadline`**(헛일 가운데 받은 기한보다 0.5ms 넘게 먼저 취소를 안 것 — `docs/measurement.md` 7-3): 예상 범위를 두지 않는다(재는 값). G1·G1o에서 0이 아니면 그것은 같은 기한에서 부른 쪽이 끊은 신호가 서버 자신의 타이머보다 먼저 닿은 몫이다(받은 쪽은 기한을 "받은 때 + 남은 시간"으로 다시 세워 전달 시간만큼 늦다 — 11-3절 50ms의 까닭) — 전파가 아니다. G3에서 G1·G1o보다 많으면 그 차가 전파된 취소가 기한보다 먼저 닿은 몫의 크기다.

### 10-3. 보정과 돌리는 차례

| 실행 | 조건 | 기대 | 기대 파일 |
|---|---|---|---|
| G1o | `calibration`(주문 → 재고 2000, 재고 → 결제 1000 — task 01 값) | **G3과 같다**: `inventory:delay=2500`은 재고가 받은 주문의 기한(2000)에 지연 중 멈춘다 → `unknown \| UNKNOWN \| - \| -`. `payment:delay=1500`은 결제가 받은 재고의 기한(1000)에 멈춘다 → `unknown \| UNKNOWN \| UNKNOWN \| -`. 이 배치에서는 결제가 받는 기한이 전파 유무와 상관없이 1000이라 G3과 가를 수 없다 — **G1o가 G1과 다르게 걸렸는지**(관찰이 켜졌는지)를 보는 보정이다. 전파를 끈 것은 P2 실험의 `paymentAfterInventoryOverSlack`(800이어야 한다)이 본다 | `calibration-grpc-g3.json` |
| G1o | `cal-false-timeout` T=300(주문 → 재고 5000) | G3과 같다(결제가 받은 기한 300 — 주문의 5000이 더 늦다) | `cal-false-timeout-grpc-g3.json`, `cal-false-timeout-grpc-g3.timing.json` |

- 차례: 보정 둘 → 실험. 실험은 **G1 · G1o · G3을 같은 바퀴에서 번갈아**(바퀴 1 = G1 → G1o → G3, 바퀴 2 = G1o → G3 → G1, 바퀴 3 = G3 → G1 → G1o — 자리마다 조건 하나씩), 바퀴 안에서 P1의 셋 뒤에 P2의 셋, 조건을 바꿀 때마다 다시 띄우고 r0을 버린다. 실행 36개(버리는 실행 18 + 잰 실행 18), 실험 이름 `exp2-P1`·`exp2-P2`(색인 `runs/task-05-index.tsv`). **앞의 실험(`exp-*`)에서 잰 G1·G3과 섞어 견주지 않는다** — 다른 바퀴(다른 시각의 부하)다. `scripts/task05-all.sh cal2 exp2`.
- 판정 규칙은 7절 그대로.
