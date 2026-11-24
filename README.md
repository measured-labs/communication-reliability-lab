# communication-reliability-lab

서비스 사이의 동기 호출에서 타임아웃, 재시도, 멱등 키, 기한 전파를 어떻게 고르느냐가 실패·중복·지연·부하에 어떤 차이를 만드는지 재는 실험이다.

명세: `docs/plans/2026-11-23/communication-reliability-experiment/requirement-spec.md`

## 이 랩이 묻는 것

- 타임아웃 값에 따라 "실제로는 처리됐는데 실패로 본" 요청이 얼마나 생기나, 느린 아래 층이 위 층을 얼마나 묶나(task 02)
- 재시도 횟수·백오프·예산이 맨 아래 층의 요청 수와 회복에 주는 차이(task 03)
- 멱등 키가 없을 때와 있을 때의 이중 처리, 키 저장의 비용(task 04)
- gRPC의 기한·취소 전파가 HTTP의 미리 나눈 타임아웃보다 헛일을 얼마나 줄이나(task 05)

"잃음"과 "두 번 처리됨"은 응답이 아니라 **DB의 행**으로 판정한다. 그래서 먼저 측정 도구가 답을 아는 실행에서 맞는 수를 내는지 확인했다(보정 — `docs/calibration.md`).

## 구조

```
k6 (load/chain.js) ──HTTP──▶ order :8081 ──HTTP──▶ inventory :8082 ──HTTP──▶ payment :8083
                              │                     │                         │
                              ▼                     ▼                         ▼
                           order_db              inventory_db              payment_db     (PostgreSQL 18 한 인스턴스)
```

- **재고가 결제를 부르는 것은 호출의 깊이를 셋으로 만들려는 실험용 구조이지 설계의 권고가 아니다.** 실제 주문 흐름이라면 주문이 재고와 결제를 각각 부르는 꼴이 더 흔하다.
- 서비스마다 자기 데이터베이스를 쓴다. 각 층은 `PENDING` 행을 커밋하고, 아래 층을 부르고, 결과(`COMPLETED`/`RESERVED`, `FAILED`, `UNKNOWN`)를 커밋한다. 결제는 한 트랜잭션에 `APPROVED` 행 하나.
- task 01에는 재시도도 멱등 키도 없다. 같은 `requestId`가 두 번 오면 두 번 처리된다(분석기가 "두 번 처리됨"으로 센다).
- 장애는 요청 헤더 `X-Fault`로 넣는다(어느 서비스에서, 늦춤·오류 응답·커밋 뒤 실패). 헤더는 사슬을 따라 넘어가고, 서비스는 넣은 장애를 종류마다 센다(`GET /faults`).

| 폴더 | 내용 |
|---|---|
| `fault-header/` | 장애 헤더(`X-Fault`)의 해석 — Spring에 기대지 않아 서비스와 분석기가 같은 코드를 쓴다 |
| `chain-link/` | 세 서비스가 같은 규칙으로 쓰는 것: 장애 주입과 카운터(`Fault.kt`), 요청 문맥과 입구 필터(`ChainRequest.kt`), 아래 층 호출(`NextLayerCall.kt` — 뒤에 gRPC 구현을 끼울 자리), 같게 두는 설정(`chain-link.yml`) |
| `order/`, `inventory/`, `payment/` | 세 서비스(Kotlin, Spring Boot 4.2, Spring MVC, `JdbcClient`) |
| `analyzer/` | 정답과 DB 상태를 대조하는 명령줄 분석기 |
| `load/` | k6 스크립트와 시나리오마다의 기대 파일 |
| `scripts/` | 띄우기·실행·내리기 |
| `runs/` | 실행마다의 기록. 커밋한 것: task 01의 보정 실행 세 번과 일부러 중복을 넣은 실행 한 번(`docs/calibration.md`), task 02의 보정과 실험의 모든 실행(버리는 실행·실패한 실행 포함)의 요약과 `raw.tar.gz`(아래 "task 02") — 나머지는 `.gitignore` |
| `docs/measurement.md` | 측정 도구의 입력·출력 형식과 판정 규칙(분석기의 계약) |
| `docs/calibration.md` | 보정 실행의 결과와 편차 |
| `docs/dependencies.md` | 들인 것마다 필요한가·왜·선택지·버전, 꺼 둔 숨은 기본값 |
| `docs/experiments/task-02-design.md` | task 02(타임아웃) 실험의 설계와 보기 전의 예상 |
| `docs/results/task-02-timeouts.md` | task 02의 결과 표, 예상과의 대조, 한계 |
| `docs/retry.md` | task 03 재시도 감싸개의 규칙(다시 시도하는 실패, 간격의 식, 지터, 예산, 테스트 입구) |
| `docs/experiments/task-03-design.md` | task 03(재시도) 실험의 설계와 보기 전의 예상 |
| `docs/results/task-03-retries.md` | task 03의 결과 표, 예상과의 대조, 한계 |
| `docs/idempotency.md` | task 04 멱등 키와 커밋 뒤 끊김 장애(`X-Drop-Fault`)의 규칙과 테스트 입구 |
| `docs/experiments/task-04-design.md` | task 04(멱등 키) 실험의 설계와 보기 전의 예상 |
| `docs/results/task-04-idempotency.md` | task 04의 결과 표, 예상과의 대조, 한계 |

## 실행

필요한 것: Docker(Compose v2), k6, curl. **JDK는 필요 없다** — 빌드와 실행 모두 컨테이너 안(JDK 25)에서 한다.

```bash
scripts/up.sh                      # 이미지를 짓고 DB와 세 서비스를 띄운다(호스트 포트 8081~8083)
scripts/run.sh calibration 1       # 보정 실행 한 번 → runs/calibration-<시각>-r1/
scripts/run.sh duplicate 1         # 일부러 같은 requestId를 두 번 보내는 실행
scripts/test.sh                    # 테스트(컨테이너 안). 마지막 줄에 통과·실패·건너뜀 수, 돈 테스트가 0개여도 실패
scripts/down.sh                    # 이 프로젝트의 컨테이너·네트워크·볼륨을 내린다
```

- `run.sh`의 종료 코드: 0 통과, 1 실행 실패(서비스가 뜨지 않음, 떠 있는 서비스가 지금의 코드로 지은 이미지가 아님, 워밍업 실패, k6가 보내지 못한 반복, 가라앉기 시간 안에 처리 중인 요청이 0이 되지 않음, 입력 오류 등), 2 분석 결과가 기대(`load/expectations/<시나리오>.json`)와 다르거나 늘 0이어야 하는 지표(정답에 없는 행, PENDING 행, 금액 불일치)가 0이 아님.
- Docker 자원은 compose 프로젝트 이름 `communication-reliability-lab`으로만 만든다.
- DB 계정 `lab`/`lab`은 **로컬 실험용** 값이다(비밀 값이 아니다).

## task 02 — 타임아웃

```bash
scripts/task02-all.sh                 # 보정 → 실험 가·나·다(조건마다 서비스를 다시 띄우고 버리는 실행 1회 + 반복 3회). 2026-11-24 노트북에서 약 55분(02:38~03:33)
scripts/task02-all.sh ga              # 실험 하나만(cal | ga | na | da)
scripts/task02-table.sh ga 'edges.inventory->payment.falseTimeoutRatio'   # 결과 표의 줄(반복마다의 값, 가운데값, 범위)
scripts/task02-recompute.sh runs/false-timeout-…-r1                       # 커밋한 raw.tar.gz에서 분석을 다시 돌려 커밋한 보고와 견준다
```

- 조건(타임아웃 둘, 스레드 방식)은 환경 변수 `ORDER_TO_INVENTORY_TIMEOUT`·`INVENTORY_TO_PAYMENT_TIMEOUT`·`VIRTUAL_THREADS`로 준다(`compose.yaml`이 읽는다 — 주지 않으면 task 01의 값). `run.sh`가 떠 있는 서비스의 `/settings`(실제 값)와 견주고 다르면 실패한다.
- 서비스는 업무 요청마다 시작·끝 시각과 아래 층 호출을 메모리에 남기고(`GET /timings`), 100ms마다 처리 중 요청·바쁜 스레드·큐를 뜬다(`GET /samples`). 형식과 지표의 식: `docs/measurement.md` 7절.
- 실행 하나의 기록은 수 MB라 다 커밋하지 않는다. 커밋하는 것: 실행 폴더의 `config.env`, `compose.resolved.yaml`, `images.txt`, `k6-summary.json`, 보고(`report.*`, `report-timing.json`, `metrics.tsv`)와, 결과를 다시 계산하는 입력을 묶은 `raw.tar.gz`(`scripts/task02-pack.sh` — 정답·조회·DB 상태·장애 수·설정·시각 기록·표본·기대 파일), 그리고 `runs/task-02-index.tsv`(조건마다의 실행과 종료 코드). 커밋하지 않는 것: k6 원본 로그(정답·조회 줄은 `raw.tar.gz`에 있다), 워밍업 로그, 이미지 짓기 출력.

## task 03 — 재시도

```bash
scripts/task03-all.sh                 # 기본 설정 확인 → 보정 → 실험 가·나·다·라(조건마다 다시 띄우고 버리는 실행 1회 + 반복 3회)
scripts/task03-all.sh ga              # 묶음 하나만(base | cal | ga | na | da | ra)
scripts/task03-table.sh ga clients.successRatio layers.payment.arrivals   # 결과 표의 줄(반복마다, 가운데값, 범위)
scripts/task03-recompute.sh runs/retry-mix-…-r1                          # 커밋한 raw.tar.gz에서 분석을 다시 돌려 커밋한 보고와 견준다
```

- 재시도는 층마다 환경 변수 `ORDER_RETRY_*`·`INVENTORY_RETRY_*`로 켠다(규칙과 이름: `docs/retry.md`). **주지 않으면 꺼짐** — 감싸개를 만들지 않아 task 01·02의 호출 그대로다. `run.sh`가 떠 있는 서비스의 `/settings`의 `retry`와 견주고 다르면 실패한다.
- 시도마다 넣는 실패는 헤더 `X-Attempt-Fault`(`fail-first`·`slow-first`·`outage` — `docs/measurement.md` 8-1). 서비스는 시도 기록(`GET /attempts`)과 넣은 수(`GET /attempt-faults`)를 낸다.
- task 03의 시나리오는 모두 k6의 연결 재사용을 끈다. 분석은 대조 분석 + `analyzer retry`(8절) — 서버에 닿지 못한 요청이 있거나 넣은 실패의 수가 기록과 다르면 불일치(종료 코드 2).
- 커밋하는 기록: task 02와 같은 꼴(요약·보고와 `raw.tar.gz` — `scripts/task03-pack.sh`), `runs/task-03-index.tsv`.

## task 04 — 멱등 키

```bash
scripts/task04-all.sh                 # 꺼진 상태 확인(task 01~03 보정) → 보정 cal-idem 끔/켬 → 재기동 확인 → 실험 가·나·다·라
scripts/task04-all.sh na              # 묶음 하나만(base | cal | restart | ga | na | da | ra)
scripts/task04-table.sh na doubledTotal 'duplicates.pairs.201+409'          # 결과 표의 줄(metrics-idem.tsv에서)
METRICS=metrics-retry.tsv scripts/task04-table.sh ga clients.successRatio   # 재시도 분석의 지표(실험 가)
scripts/task04-recompute.sh runs/idem-plain-…-r1                           # 커밋한 raw.tar.gz에서 분석을 다시 돌려 커밋한 보고와 견준다
```

- 멱등 키는 환경 변수 `IDEMPOTENCY_ENABLED`(세 서비스에 같은 값)로 켠다(규칙: `docs/idempotency.md`). **주지 않으면 꺼짐** — 처리 코드는 task 01~03의 경로 그대로다. `run.sh`가 떠 있는 서비스의 `/settings`의 `idempotency`와 견주고 다르면 실패한다.
- 새 장애 `X-Drop-Fault: <서비스>:drop-after-commit=<n>` — 처음 n번 든 것은 커밋한 뒤 응답 없이 연결을 닫는다(`docs/measurement.md` 9-1).
- 결과 모름을 맞추는 시나리오(`cal-idem`, `idem-reconcile`)는 부하가 가라앉은 뒤 결과 모름을 같은 키로 다시 보낸다(`resends.jsonl`). 분석은 대조 분석 + `analyzer idem`(9절) + 재시도가 켜진 시나리오는 `analyzer retry`. 부하 직전의 재시도 예산 토큰을 `retry-budget-start.json`에 남긴다(R03-7).
- 커밋하는 기록: task 03과 같은 꼴(요약·보고와 `raw.tar.gz` — `scripts/task04-pack.sh`), `runs/task-04-index.tsv`, 재기동 확인의 `runs/restart-check-*/`.
