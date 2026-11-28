# 분석 보고 — calibration-20261126T045242-r2

- 정답 600줄, requestId 600개 (계획 600)
- 응답으로 가름: completed 150, failed 300, unknown 150
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 200
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/calibration-20261126T045242-r2/expect.json`
- 판정: 통과

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 550 | 250 | 0 | 0 | 0 | 0 | 50 | 50 | 100 | 0 |
| inventory | 500 | 350 | 0 | 0 | 0 | 0 | 100 | 100 | 50 | - |
| payment | 450 | 450 | 0 | 0 | 0 | 0 | 150 | 150 | 0 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|
| order | delay | 50 | 50 |
| order | error | 50 | 50 |
| order | fail-after-commit | 50 | 50 |
| inventory | delay | 100 | 100 |
| inventory | error | 50 | 50 |
| inventory | fail-after-commit | 50 | 50 |
| payment | delay | 100 | 100 |
| payment | error | 50 | 50 |
| payment | fail-after-commit | 50 | 50 |

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `(없음)` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 16.0 | 23.0 | 30.0 | 30.0 |
| `inventory:delay=2500` | 50 | `unknown | UNKNOWN | RESERVED | APPROVED` | 50 | 2007.0 | 2009.0 | 2012.0 | 2012.0 |
| `inventory:delay=300` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 317.0 | 319.0 | 321.0 | 321.0 |
| `inventory:error` | 50 | `failed | FAILED | - | -` | 50 | 8.0 | 10.0 | 12.0 | 12.0 |
| `inventory:fail-after-commit` | 50 | `failed | FAILED | RESERVED | APPROVED` | 50 | 17.0 | 19.0 | 19.0 | 19.0 |
| `order:delay=6000` | 50 | `unknown | COMPLETED | RESERVED | APPROVED` | 50 | 5001.0 | 5002.0 | 5002.0 | 5002.0 |
| `order:error` | 50 | `failed | - | - | -` | 50 | 2.0 | 3.0 | 5.0 | 5.0 |
| `order:fail-after-commit` | 50 | `failed | COMPLETED | RESERVED | APPROVED` | 50 | 18.0 | 22.0 | 24.0 | 24.0 |
| `payment:delay=1500` | 50 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 50 | 1013.0 | 1015.0 | 1019.0 | 1019.0 |
| `payment:delay=300` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 317.0 | 319.0 | 329.0 | 329.0 |
| `payment:error` | 50 | `failed | FAILED | FAILED | -` | 50 | 14.0 | 16.0 | 16.0 | 16.0 |
| `payment:fail-after-commit` | 50 | `failed | FAILED | FAILED | APPROVED` | 50 | 17.0 | 19.0 | 26.0 | 26.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 600 | 18.0 | 5001.0 | 5001.0 | 5002.0 |
| completed | 150 | 315.0 | 319.0 | 325.0 | 329.0 |
| failed | 300 | 14.0 | 19.0 | 22.0 | 26.0 |
| unknown | 150 | 2007.0 | 5001.0 | 5002.0 | 5002.0 |
