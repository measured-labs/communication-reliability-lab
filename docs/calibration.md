# 보정 실행 — 측정 도구가 답을 아는 실행에서 맞는 수를 내나

> 2026-11-24, 노트북 한 대(24코어, 36GB)에서 세 서비스·DB·k6를 함께 돌렸다. 코드 `fab5703` — 각 실행의 `config.env`에 `git_dirty=yes`로 적혔고, 바뀐 경로는 `git_dirty_paths=docs/plans/…/log.md`(작업 기록 문서 하나 — 코드·설정·스크립트는 커밋과 같다).
> 기록(커밋한 실행 기록은 이 넷뿐이다): `runs/calibration-20261124T012508-r1`, `…012622-r2`, `…012721-r3`, `runs/duplicate-20261124T012821-r1`. 아래 수는 모두 그 폴더의 `report.json`에서 옮겼다(다시 계산하려면 `scripts/run.sh`가 부르는 분석기를 그 폴더에 다시 돌린다).
> 이 기록은 금액 대조·늘 0이어야 하는 지표·처리 중인 요청 수로 판정하는 가라앉기를 넣은 뒤의 것이다. 그 전의 기록(`…004414-r1` 등, 코드 `be3a721`)은 새 형식(정답 줄의 `amount`)과 맞지 않아 이 넷으로 바꿨다.
> 노트북에서 잰 지연은 상대 비교로만 쓴다(명세 2절).

## 실행 조건

- 부하: k6 `constant-arrival-rate` 20/s × 30s = 600건, k6 요청 타임아웃 5s, 요청마다 금액 `1000 + 요청 번호`. 워밍업 20/s × 15s(장애 없음) 뒤 DB·카운터 초기화. k6가 보내지 못한 반복 0(`k6-summary.json`에 `dropped_iterations` 없음).
- 가라앉기: 세 서비스의 처리 중인 요청 수(`GET /inflight`)가 모두 0이 될 때까지, 최대 20s. 세 번 모두 k6가 끝난 뒤 861~866ms에 0이 됐다(`config.env`의 `settled_after_ms` — `order:delay=6000`의 요청이 k6 타임아웃 5s 뒤에도 1s쯤 더 잔다).
- keep-alive: 클라이언트(JDK HttpClient) 15s < 서버(Tomcat) 20s(`settings-*.json`의 `jdkSystemProperties`·`tomcat.keepAliveTimeoutMs`).
- 층 사이 타임아웃: order→inventory 연결 500ms·요청 2000ms, inventory→payment 연결 500ms·요청 1000ms. 재시도 없음.
- 서비스마다 CPU 2·메모리 768m, 플랫폼 스레드(Tomcat 실행기 `ThreadPoolExecutor`, 최대 200), Hikari 풀 10·대기 1000ms — 떠 있는 객체에서 읽은 값(`settings-*.json`).
- 섞음(`load/chain.js` `CALIBRATION_MIX`): i번째 요청 = 12종류 가운데 `i % 12` → 종류마다 정확히 50건.

## 결과 — 세 번 모두 기대와 건수까지 같다

기대(`load/expectations/calibration.json`)와 분석기 결과의 불일치: **r1 0건 · r2 0건 · r3 0건**. 세 번의 수가 서로 모두 같다.

| 종류(라벨) | 기대 서명(응답 \| order \| inventory \| payment) | 기대 | r1 | r2 | r3 |
|---|---|---|---|---|---|
| 정상 | `completed \| COMPLETED \| RESERVED \| APPROVED` | 50 | 50 | 50 | 50 |
| `order:error` | `failed \| - \| - \| -` | 50 | 50 | 50 | 50 |
| `inventory:error` | `failed \| FAILED \| - \| -` | 50 | 50 | 50 | 50 |
| `payment:error` | `failed \| FAILED \| FAILED \| -` | 50 | 50 | 50 | 50 |
| `inventory:delay=300` | `completed \| COMPLETED \| RESERVED \| APPROVED` | 50 | 50 | 50 | 50 |
| `payment:delay=300` | `completed \| COMPLETED \| RESERVED \| APPROVED` | 50 | 50 | 50 | 50 |
| `order:delay=6000`(> k6 5s) | `unknown \| COMPLETED \| RESERVED \| APPROVED` | 50 | 50 | 50 | 50 |
| `inventory:delay=2500`(> 2000ms) | `unknown \| UNKNOWN \| RESERVED \| APPROVED` | 50 | 50 | 50 | 50 |
| `payment:delay=1500`(> 1000ms) | `unknown \| UNKNOWN \| UNKNOWN \| APPROVED` | 50 | 50 | 50 | 50 |
| `order:fail-after-commit` | `failed \| COMPLETED \| RESERVED \| APPROVED` | 50 | 50 | 50 | 50 |
| `inventory:fail-after-commit` | `failed \| FAILED \| RESERVED \| APPROVED` | 50 | 50 | 50 | 50 |
| `payment:fail-after-commit` | `failed \| FAILED \| FAILED \| APPROVED` | 50 | 50 | 50 | 50 |

| 지표 | 기대 | r1 | r2 | r3 |
|---|---|---|---|---|
| 정답 줄(= 계획 600) | 600 | 600 | 600 | 600 |
| 응답: 완료 / 실패 / 결과 모름 | 150 / 300 / 150 | 같음 | 같음 | 같음 |
| 잃음 | 0 | 0 | 0 | 0 |
| 두 번 처리됨 | 0 | 0 | 0 | 0 |
| 층 사이 처리 수가 다른 requestId | 200 | 200 | 200 | 200 |
| 정답에 없는 행 / PENDING 행 / 금액이 보낸 금액과 다른 처리 행 | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |
| 장애 수(넣은 수 = 서비스가 센 수, 9칸) | 일치 | 일치 | 일치 | 일치 |

층마다(세 번 같음): order 행 550·처리 250·실패 응답인데 처리 50·결과 모름→처리 50·결과 모름→처리 안 됨 100·금액 불일치 0 / inventory 500·350·100·100·50(금액 칸 없음) / payment 450·450·150·150·0·금액 불일치 0.
"넣은 것으로 설명되는 것"만 남았다: 실패 응답인데 처리된 행은 `fail-after-commit`에서만, 결과 모름인데 처리된 행은 타임아웃보다 긴 지연에서만 나왔다.

장애 수(세 번 같음): order delay 50·error 50·fail-after-commit 50, inventory delay 100(300ms 50 + 2500ms 50)·error 50·fail-after-commit 50, payment delay 100·error 50·fail-after-commit 50 — 넣은 수와 서비스가 센 수가 모두 같다.

## 처리 시간의 편차 (k6가 잰 경과, ms — p50 / p95 / p99 / max)

| 묶음 | r1 | r2 | r3 |
|---|---|---|---|
| 정상 | 35 / 40 / 53 / 53 | 32 / 35 / 44 / 44 | 31 / 34 / 39 / 39 |
| `inventory:delay=300` | 337 / 344 / 393 / 393 | 334 / 339 / 343 / 343 | 334 / 342 / 349 / 349 |
| `payment:delay=300` | 337 / 346 / 349 / 349 | 332 / 336 / 339 / 339 | 333 / 339 / 342 / 342 |
| `inventory:delay=2500` | 2013 / 2017 / 2044 / 2044 | 2012 / 2016 / 2024 / 2024 | 2012 / 2016 / 2017 / 2017 |
| `payment:delay=1500` | 1027 / 1032 / 1032 / 1032 | 1026 / 1029 / 1034 / 1034 | 1025 / 1030 / 1036 / 1036 |
| `order:delay=6000` | 5001 / 5002 / 5002 / 5002 | 5001 / 5002 / 5002 / 5002 | 5001 / 5002 / 5002 / 5002 |
| `order:error` | 2 / 4 / 8 / 8 | 2 / 3 / 4 / 4 | 2 / 4 / 4 / 4 |
| `inventory:error` | 16 / 21 / 26 / 26 | 14 / 17 / 25 / 25 | 14 / 16 / 19 / 19 |
| `payment:error` | 29 / 36 / 40 / 40 | 26 / 30 / 44 / 44 | 26 / 31 / 32 / 32 |
| `order:fail-after-commit` | 38 / 46 / 56 / 56 | 35 / 39 / 44 / 44 | 34 / 44 / 71 / 71 |
| 완료 전체(150) | 335 / 343 / 349 / 393 | 332 / 337 / 340 / 343 | 332 / 339 / 344 / 349 |
| 실패 전체(300) | 32 / 43 / 51 / 130 | 28 / 37 / 42 / 45 | 29 / 37 / 44 / 71 |

(소수점 아래는 버렸다.)

- 타임아웃이 끊는 요청(2000ms·1000ms·5000ms)의 경과는 세 번 사이 차이가 p50 1~2ms 안이다 — 타임아웃이 설정대로 걸린다.
- 정상 요청의 p50은 31~35ms, 세 번 사이 차이 4ms(첫 번째가 가장 느리다 — 새로 띄운 직후의 실행, 워밍업 1회차 281/300). 50건짜리 묶음의 p99·max는 한 건이 정하므로 10~90ms씩 흔들린다(r1의 `inventory:fail-after-commit` max 130ms, `inventory:delay=300` max 393ms). **노트북에서 몇 ms 차이를 견주는 실험은 이 흔들림보다 큰 차이만 뜻이 있다** — 조건당 반복 횟수는 메인이 이 표를 보고 정한다.

## 일부러 틀린 경우 — 같은 requestId를 두 번 보낸다

`scripts/run.sh duplicate 1`(`runs/duplicate-20261124T012821-r1`): 20/s × 15s = 300건, 처음 100건은 둘씩 같은 id(50쌍), 나머지 200건은 서로 다른 id, 장애 없음.

| 지표 | 기대 | 결과 |
|---|---|---|
| 정답 줄 / requestId | 300 / 250 | 300 / 250 |
| 두 번 처리됨(전체, 층마다) | 50 (50·50·50) | 50 (50·50·50) |
| 서명 `completed+completed \| COMPLETED+COMPLETED \| RESERVED+RESERVED \| APPROVED+APPROVED` | 50 | 50 |
| 잃음 | 0 | 0 |
| 금액 불일치 · 정답에 없는 행 · PENDING 행 | 0 · 0 · 0 | 0 · 0 · 0 |

분석기는 넣은 중복 수만큼 "두 번 처리됨"을 냈다(`runs/duplicate-20261124T012821-r1/report.md`). 같은 id의 두 요청은 금액이 다르다(1000 + 요청 번호) — 두 결제 행의 금액이 각각 그 id로 보낸 금액 가운데 하나여서 금액 불일치는 0이다.

## 일부러 틀리게 한 확인 — 조용히 통과하지 않나 (손으로 한 확인)

보정 실행 `…012508-r1`의 기록을 복사해 고쳐 넣고 분석기를 돌렸다(고친 사본은 남기지 않았다):
- 결제 행 하나의 금액을 1000 → 1로 → `amountMismatch: 늘 0, 실제 1`과 `amountMismatch: 기대 0, 실제 1`, 종료 코드 2.
- 정답에 없는 행(order·inventory에 `stray-1`)을 더함 → `orphanRows: 늘 0, 실제 2`와 `orphanRows: 기대 0, 실제 2`, 종료 코드 2. 기대 파일 없이 돌려도 종료 코드 2.

실행 스크립트:
- 가라앉기를 시나리오의 가장 긴 지연(6s)보다 짧게(`SETTLE_TIMEOUT_SECONDS=0 scripts/run.sh calibration 90`) → "가라앉기 0s 안에 처리 중인 요청이 0이 되지 않았다(order=2) — DB를 내보내지 않고 멈춘다", 종료 코드 1, `db/`가 빈 채로 멈춤(이 실행의 기록은 커밋하지 않았다).

손으로 만든 작은 기록(분석기 컨테이너): 결제 금액 바꿈 → 2 · 정답에 없는 행 → 2 · PENDING 행 → 2(셋 다 기대 파일 없이) · CSV에 CR → 1(있던 `report.json` 지워짐) · 모르는 status 낱말 → 1 · `order:error,order:error` → 1 · `--run` 두 번 → 1 · `"status":201.0` → 1 · `fault-counts.json` 여분 키 → 1 · JSON 배열 줄 → 1 · 빈 금액 → 1 · 정답에 `amount` 없음 → 1 · 기대 값 `"0"`(문자열) → 1 · 기대에 0으로 적은 서명이 실제에 없음과 `--planned 0002` → 0.

## 보정하며 드러나 고친 것

1. **k6가 계획보다 하나 더 보냈다** — 30s × 20/s에 601번 반복(첫 번째 시도 r2). 끝 경계의 반복이다. 기대와 다르다고 분석기가 종료 코드 2로 잡았고, 스크립트가 계획한 수를 넘는 반복은 보내지 않게 고쳤다(`load/chain.js`).
2. **막 뜬 JVM의 첫 워밍업에서 일부가 504** — 300건 중 19~32건(inventory→payment 1000ms를 넘김). 워밍업을 "모두 201인 회차가 나올 때까지 최대 3회차"로 바꿨다. 위 r1은 1회차 281/300, 2회차 300/300으로 이어졌다(`config.env`의 `warmup_round_*`).

## 명세 8절의 가정

1. **노트북 한 대에서도 조건 사이의 상대 차이가 드러난다** — 지금까지는 맞다. 건수는 세 번 모두 같고, 지연은 타임아웃·주입 지연이 정한 값 둘레에서 몇 ms 안에 모인다. 다만 부하는 20/s로 가볍다. 스레드·풀이 차는 부하(task 02)에서도 그런지는 아직 모른다.
2. **장애를 요청 단위로 정확히 넣을 수 있다** — 맞다. 세 번 모두 아홉 칸에서 넣은 수 = 서비스가 센 수, 종류마다의 DB 결과가 기대 서명과 건수까지 같다. ("죽임"은 이번 task에서 넣지 않았다 — 헤더 장애만.)
