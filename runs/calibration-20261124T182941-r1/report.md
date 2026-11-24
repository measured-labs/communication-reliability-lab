# 분석 보고 — calibration-20261124T182941-r1

- 정답 600줄, requestId 600개 (계획 600)
- 응답으로 가름: completed 150, failed 300, unknown 150
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 200
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/calibration-20261124T182941-r1/expect.json`
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
| `(없음)` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 37.0 | 51.0 | 58.0 | 58.0 |
| `inventory:delay=2500` | 50 | `unknown | UNKNOWN | RESERVED | APPROVED` | 50 | 2014.0 | 2017.0 | 2017.0 | 2017.0 |
| `inventory:delay=300` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 338.0 | 345.0 | 369.0 | 369.0 |
| `inventory:error` | 50 | `failed | FAILED | - | -` | 50 | 16.0 | 20.0 | 32.0 | 32.0 |
| `inventory:fail-after-commit` | 50 | `failed | FAILED | RESERVED | APPROVED` | 50 | 38.0 | 46.0 | 67.0 | 67.0 |
| `order:delay=6000` | 50 | `unknown | COMPLETED | RESERVED | APPROVED` | 50 | 5001.0 | 5001.0 | 5002.0 | 5002.0 |
| `order:error` | 50 | `failed | - | - | -` | 50 | 3.0 | 4.0 | 8.0 | 8.0 |
| `order:fail-after-commit` | 50 | `failed | COMPLETED | RESERVED | APPROVED` | 50 | 39.0 | 48.0 | 51.0 | 51.0 |
| `payment:delay=1500` | 50 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 50 | 1029.0 | 1032.0 | 1033.0 | 1033.0 |
| `payment:delay=300` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 338.0 | 344.0 | 357.0 | 357.0 |
| `payment:error` | 50 | `failed | FAILED | FAILED | -` | 50 | 31.0 | 36.0 | 56.0 | 56.0 |
| `payment:fail-after-commit` | 50 | `failed | FAILED | FAILED | APPROVED` | 50 | 38.0 | 45.0 | 48.0 | 48.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 600 | 41.0 | 5001.0 | 5001.0 | 5002.0 |
| completed | 150 | 336.0 | 344.0 | 357.0 | 369.0 |
| failed | 300 | 33.0 | 44.0 | 49.0 | 67.0 |
| unknown | 150 | 2014.0 | 5001.0 | 5002.0 | 5002.0 |
