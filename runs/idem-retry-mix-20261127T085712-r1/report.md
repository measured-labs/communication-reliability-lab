# 분석 보고 — idem-retry-mix-20261127T085712-r1

- 정답 800줄, requestId 800개 (계획 800)
- 응답으로 가름: completed 233, failed 30, unknown 537
- **잃음 0 · 두 번 처리됨 44** · 층 사이 처리 수가 다른 requestId 44
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/idem-retry-mix-20261127T085712-r1/expect.json`
- 판정: **불일치 9건**
  - doubledTotal: 기대 122, 실제 44
  - layerMismatch: 기대 122, 실제 44
  - labels['']['completed | COMPLETED | RESERVED | APPROVED']: 기대 555, 실제 193
  - labels['']['completed | COMPLETED | RESERVED | APPROVED+APPROVED']: 기대 122, 실제 40
  - labels['']['failed | FAILED | FAILED | -']: 기대 123, 실제 30
  - labels['']['unknown | - | - | -']: 기대 0, 실제 530
  - labels['']['unknown | COMPLETED | RESERVED | APPROVED']: 기대 0, 실제 1
  - labels['']['unknown | COMPLETED | RESERVED | APPROVED+APPROVED']: 기대 0, 실제 4
  - labels['']['unknown | FAILED | FAILED | -']: 기대 0, 실제 2

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 270 | 238 | 0 | 0 | 0 | 0 | 0 | 5 | 532 | 0 |
| inventory | 270 | 238 | 0 | 0 | 0 | 0 | 0 | 5 | 532 | - |
| payment | 282 | 282 | 0 | 0 | 0 | 44 | 0 | 5 | 532 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `(없음)` | 800 | `completed | COMPLETED | RESERVED | APPROVED` | 193 | 1.0 | 318.0 | 1118.0 | 10001.0 |
| | | `completed | COMPLETED | RESERVED | APPROVED+APPROVED` | 40 | | | | |
| | | `failed | FAILED | FAILED | -` | 30 | | | | |
| | | `unknown | - | - | -` | 530 | | | | |
| | | `unknown | COMPLETED | RESERVED | APPROVED` | 1 | | | | |
| | | `unknown | COMPLETED | RESERVED | APPROVED+APPROVED` | 4 | | | | |
| | | `unknown | FAILED | FAILED | -` | 2 | | | | |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 800 | 1.0 | 318.0 | 1118.0 | 10001.0 |
| completed | 233 | 113.0 | 1112.0 | 1118.0 | 1125.0 |
| failed | 30 | 317.0 | 323.0 | 332.0 | 332.0 |
| unknown | 537 | 0.0 | 1.0 | 10000.0 | 10001.0 |
