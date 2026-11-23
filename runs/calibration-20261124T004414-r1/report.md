# 분석 보고 — calibration-20261124T004414-r1

- 정답 600줄, requestId 600개 (계획 600)
- 응답으로 가름: completed 150, failed 300, unknown 150
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 200
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/calibration-20261124T004414-r1/expect.json`: 일치

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 |
|---|---|---|---|---|---|---|---|---|---|
| order | 550 | 250 | 0 | 0 | 0 | 0 | 50 | 50 | 100 |
| inventory | 500 | 350 | 0 | 0 | 0 | 0 | 100 | 100 | 50 |
| payment | 450 | 450 | 0 | 0 | 0 | 0 | 150 | 150 | 0 |

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
| `(없음)` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 35.0 | 41.0 | 50.0 | 50.0 |
| `inventory:delay=2500` | 50 | `unknown | UNKNOWN | RESERVED | APPROVED` | 50 | 2013.0 | 2015.0 | 2017.0 | 2017.0 |
| `inventory:delay=300` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 337.0 | 341.0 | 343.0 | 343.0 |
| `inventory:error` | 50 | `failed | FAILED | - | -` | 50 | 16.0 | 18.0 | 27.0 | 27.0 |
| `inventory:fail-after-commit` | 50 | `failed | FAILED | RESERVED | APPROVED` | 50 | 35.0 | 42.0 | 43.0 | 43.0 |
| `order:delay=6000` | 50 | `unknown | COMPLETED | RESERVED | APPROVED` | 50 | 5001.0 | 5001.0 | 5002.0 | 5002.0 |
| `order:error` | 50 | `failed | - | - | -` | 50 | 2.0 | 4.0 | 9.0 | 9.0 |
| `order:fail-after-commit` | 50 | `failed | COMPLETED | RESERVED | APPROVED` | 50 | 37.0 | 43.0 | 53.0 | 53.0 |
| `payment:delay=1500` | 50 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 50 | 1027.0 | 1030.0 | 1031.0 | 1031.0 |
| `payment:delay=300` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 336.0 | 343.0 | 344.0 | 344.0 |
| `payment:error` | 50 | `failed | FAILED | FAILED | -` | 50 | 29.0 | 33.0 | 40.0 | 40.0 |
| `payment:fail-after-commit` | 50 | `failed | FAILED | FAILED | APPROVED` | 50 | 37.0 | 42.0 | 45.0 | 45.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 600 | 38.0 | 5001.0 | 5001.0 | 5002.0 |
| completed | 150 | 335.0 | 341.0 | 343.0 | 344.0 |
| failed | 300 | 32.0 | 40.0 | 44.0 | 53.0 |
| unknown | 150 | 2013.0 | 5001.0 | 5002.0 | 5002.0 |
