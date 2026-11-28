# 분석 보고 — idem-reconcile-20261127T043608-r3

- 정답 800줄, requestId 800개 (계획 800)
- 응답으로 가름: completed 350, failed 0, unknown 450
- **잃음 0 · 두 번 처리됨 307** · 층 사이 처리 수가 다른 requestId 320
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/idem-reconcile-20261127T043608-r3/expect.json`
- 판정: **불일치 8건**
  - doubledTotal: 기대 400, 실제 307
  - labels['']['completed | COMPLETED | RESERVED | APPROVED']: 기대 400, 실제 350
  - labels['']['unknown | COMPLETED | RESERVED | APPROVED']: 기대 0, 실제 68
  - labels['']['unknown | COMPLETED+COMPLETED | RESERVED+RESERVED | APPROVED+APPROVED']: 기대 80, 실제 62
  - labels['']['unknown | COMPLETED+UNKNOWN | RESERVED+RESERVED | APPROVED+APPROVED']: 기대 160, 실제 123
  - labels['']['unknown | COMPLETED+UNKNOWN | RESERVED+UNKNOWN | APPROVED+APPROVED']: 기대 160, 실제 122
  - labels['']['unknown | UNKNOWN | RESERVED | APPROVED']: 기대 0, 실제 37
  - labels['']['unknown | UNKNOWN | UNKNOWN | APPROVED']: 기대 0, 실제 38

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 1107 | 787 | 0 | 0 | 0 | 62 | 0 | 375 | 75 | 0 |
| inventory | 1107 | 947 | 0 | 0 | 0 | 185 | 0 | 412 | 38 | - |
| payment | 1107 | 1107 | 0 | 0 | 0 | 307 | 0 | 450 | 0 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `(없음)` | 800 | `completed | COMPLETED | RESERVED | APPROVED` | 350 | 15.0 | 2007.0 | 10001.0 | 10001.0 |
| | | `unknown | COMPLETED | RESERVED | APPROVED` | 68 | | | | |
| | | `unknown | COMPLETED+COMPLETED | RESERVED+RESERVED | APPROVED+APPROVED` | 62 | | | | |
| | | `unknown | COMPLETED+UNKNOWN | RESERVED+RESERVED | APPROVED+APPROVED` | 123 | | | | |
| | | `unknown | COMPLETED+UNKNOWN | RESERVED+UNKNOWN | APPROVED+APPROVED` | 122 | | | | |
| | | `unknown | UNKNOWN | RESERVED | APPROVED` | 37 | | | | |
| | | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 38 | | | | |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 800 | 15.0 | 2007.0 | 10001.0 | 10001.0 |
| completed | 350 | 14.0 | 20.0 | 22.0 | 31.0 |
| failed | 0 | - | - | - | - |
| unknown | 450 | 15.0 | 2009.0 | 10001.0 | 10001.0 |
