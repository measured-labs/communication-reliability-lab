# 분석 보고 — retry-mix-20261127T024417-r1

- 정답 800줄, requestId 800개 (계획 800)
- 응답으로 가름: completed 176, failed 3, unknown 621
- **잃음 0 · 두 번 처리됨 9** · 층 사이 처리 수가 다른 requestId 9
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/retry-mix-20261127T024417-r1/expect.json`
- 판정: **불일치 11건**
  - doubledTotal: 기대 43, 실제 9
  - layerMismatch: 기대 43, 실제 9
  - labels['']['completed | COMPLETED | FAILED+RESERVED | APPROVED']: 기대 87, 실제 17
  - labels['']['completed | COMPLETED | RESERVED | APPROVED']: 기대 634, 실제 153
  - labels['']['completed | COMPLETED | RESERVED | APPROVED+APPROVED']: 기대 43, 실제 6
  - labels['']['failed | FAILED | FAILED+FAILED+FAILED | -']: 기대 36, 실제 3
  - labels['']['unknown | - | - | -']: 기대 0, 실제 611
  - labels['']['unknown | COMPLETED | FAILED+RESERVED | APPROVED']: 기대 0, 실제 3
  - labels['']['unknown | COMPLETED | RESERVED | APPROVED']: 기대 0, 실제 1
  - labels['']['unknown | COMPLETED | RESERVED | APPROVED+APPROVED']: 기대 0, 실제 3
  - labels['']['unknown | FAILED | FAILED+FAILED+FAILED | -']: 기대 0, 실제 3

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 189 | 183 | 0 | 0 | 0 | 0 | 0 | 7 | 614 | 0 |
| inventory | 221 | 183 | 0 | 0 | 0 | 0 | 0 | 7 | 614 | - |
| payment | 192 | 192 | 0 | 0 | 0 | 9 | 0 | 7 | 614 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `(없음)` | 800 | `completed | COMPLETED | FAILED+RESERVED | APPROVED` | 17 | 1.0 | 319.0 | 10001.0 | 10001.0 |
| | | `completed | COMPLETED | RESERVED | APPROVED` | 153 | | | | |
| | | `completed | COMPLETED | RESERVED | APPROVED+APPROVED` | 6 | | | | |
| | | `failed | FAILED | FAILED+FAILED+FAILED | -` | 3 | | | | |
| | | `unknown | - | - | -` | 611 | | | | |
| | | `unknown | COMPLETED | FAILED+RESERVED | APPROVED` | 3 | | | | |
| | | `unknown | COMPLETED | RESERVED | APPROVED` | 1 | | | | |
| | | `unknown | COMPLETED | RESERVED | APPROVED+APPROVED` | 3 | | | | |
| | | `unknown | FAILED | FAILED+FAILED+FAILED | -` | 3 | | | | |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 800 | 1.0 | 319.0 | 10001.0 | 10001.0 |
| completed | 176 | 16.0 | 524.0 | 1114.0 | 1120.0 |
| failed | 3 | 1231.0 | 1233.0 | 1233.0 | 1233.0 |
| unknown | 621 | 1.0 | 1.0 | 10001.0 | 10001.0 |
