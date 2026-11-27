# 분석 보고 — split-timeout-20261126T065100-r2

- 정답 800줄, requestId 800개 (계획 800)
- 응답으로 가름: completed 391, failed 0, unknown 409
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 392
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): **불일치**
- 기대 파일 `/runs/split-timeout-20261126T065100-r2/expect.json`
- 판정: **불일치 11건**
  - layerMismatch: 기대 400, 실제 392
  - faultCountsMatch: 기대 true, 실제 false
  - labels['']['completed | COMPLETED | RESERVED | APPROVED']: 기대 200, 실제 197
  - labels['']['unknown | - | - | -']: 기대 0, 실제 3
  - labels['payment:delay=1500']['unknown | - | - | -']: 기대 0, 실제 4
  - labels['payment:delay=1500']['unknown | UNKNOWN | UNKNOWN | APPROVED']: 기대 200, 실제 196
  - labels['payment:delay=2500']['unknown | - | - | -']: 기대 0, 실제 4
  - labels['payment:delay=2500']['unknown | UNKNOWN | UNKNOWN | APPROVED']: 기대 200, 실제 196
  - labels['payment:delay=500']['completed | COMPLETED | RESERVED | APPROVED']: 기대 200, 실제 194
  - labels['payment:delay=500']['unknown | - | - | -']: 기대 0, 실제 3
  - labels['payment:delay=500']['unknown | COMPLETED | RESERVED | APPROVED']: 기대 0, 실제 3

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 786 | 394 | 0 | 0 | 0 | 0 | 0 | 3 | 406 | 0 |
| inventory | 786 | 394 | 0 | 0 | 0 | 0 | 0 | 3 | 406 | - |
| payment | 786 | 786 | 0 | 0 | 0 | 0 | 0 | 395 | 14 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|
| payment | delay | 600 | 589 **≠** |

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `(없음)` | 200 | `completed | COMPLETED | RESERVED | APPROVED` | 197 | 16.0 | 20.0 | 10000.0 | 10001.0 |
| | | `unknown | - | - | -` | 3 | | | | |
| `payment:delay=1500` | 200 | `unknown | - | - | -` | 4 | 1013.0 | 1026.0 | 10001.0 | 10001.0 |
| | | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 196 | | | | |
| `payment:delay=2500` | 200 | `unknown | - | - | -` | 4 | 1013.0 | 1025.0 | 10001.0 | 10002.0 |
| | | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 196 | | | | |
| `payment:delay=500` | 200 | `completed | COMPLETED | RESERVED | APPROVED` | 194 | 516.0 | 520.0 | 10001.0 | 10001.0 |
| | | `unknown | - | - | -` | 3 | | | | |
| | | `unknown | COMPLETED | RESERVED | APPROVED` | 3 | | | | |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 800 | 1009.0 | 1016.0 | 10001.0 | 10002.0 |
| completed | 391 | 24.0 | 518.0 | 523.0 | 527.0 |
| failed | 0 | - | - | - | - |
| unknown | 409 | 1013.0 | 10001.0 | 10001.0 | 10002.0 |
