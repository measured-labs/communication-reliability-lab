# 분석 보고 — calibration-20261125T141929-r1

- 정답 600줄, requestId 600개 (계획 600)
- 응답으로 가름: completed 150, failed 300, unknown 150
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 100
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/calibration-20261125T141929-r1/expect.json`
- 판정: 통과

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 550 | 250 | 0 | 0 | 0 | 0 | 50 | 50 | 100 | 0 |
| inventory | 450 | 300 | 0 | 0 | 0 | 0 | 100 | 50 | 100 | - |
| payment | 350 | 350 | 0 | 0 | 0 | 0 | 150 | 50 | 100 | 0 |

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
| `(없음)` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 51.0 | 66.0 | 72.0 | 72.0 |
| `inventory:delay=2500` | 50 | `unknown | UNKNOWN | - | -` | 50 | 2025.0 | 2034.0 | 2043.0 | 2043.0 |
| `inventory:delay=300` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 354.0 | 386.0 | 404.0 | 404.0 |
| `inventory:error` | 50 | `failed | FAILED | - | -` | 50 | 23.0 | 33.0 | 48.0 | 48.0 |
| `inventory:fail-after-commit` | 50 | `failed | FAILED | RESERVED | APPROVED` | 50 | 54.0 | 82.0 | 106.0 | 106.0 |
| `order:delay=6000` | 50 | `unknown | COMPLETED | RESERVED | APPROVED` | 50 | 5001.0 | 5004.0 | 5010.0 | 5010.0 |
| `order:error` | 50 | `failed | - | - | -` | 50 | 3.0 | 8.0 | 12.0 | 12.0 |
| `order:fail-after-commit` | 50 | `failed | COMPLETED | RESERVED | APPROVED` | 50 | 51.0 | 69.0 | 78.0 | 78.0 |
| `payment:delay=1500` | 50 | `unknown | UNKNOWN | UNKNOWN | -` | 50 | 1040.0 | 1054.0 | 1058.0 | 1058.0 |
| `payment:delay=300` | 50 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 355.0 | 377.0 | 387.0 | 387.0 |
| `payment:error` | 50 | `failed | FAILED | FAILED | -` | 50 | 40.0 | 55.0 | 69.0 | 69.0 |
| `payment:fail-after-commit` | 50 | `failed | FAILED | FAILED | APPROVED` | 50 | 55.0 | 88.0 | 92.0 | 92.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 600 | 60.0 | 5001.0 | 5002.0 | 5010.0 |
| completed | 150 | 349.0 | 377.0 | 388.0 | 404.0 |
| failed | 300 | 42.0 | 70.0 | 92.0 | 106.0 |
| unknown | 150 | 2025.0 | 5002.0 | 5007.0 | 5010.0 |
