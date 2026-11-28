# 분석 보고 — split-timeout-20261127T074210-r1

- 정답 800줄, requestId 800개 (계획 800)
- 응답으로 가름: completed 390, failed 0, unknown 410
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 392
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): **불일치**
- 기대 파일 `/runs/split-timeout-20261127T074210-r1/expect.json`
- 판정: **불일치 11건**
  - layerMismatch: 기대 400, 실제 392
  - faultCountsMatch: 기대 true, 실제 false
  - labels['']['completed | COMPLETED | RESERVED | APPROVED']: 기대 200, 실제 196
  - labels['']['unknown | - | - | -']: 기대 0, 실제 4
  - labels['payment:delay=1500']['unknown | - | - | -']: 기대 0, 실제 4
  - labels['payment:delay=1500']['unknown | UNKNOWN | RESERVED | APPROVED']: 기대 200, 실제 196
  - labels['payment:delay=2500']['unknown | - | - | -']: 기대 0, 실제 4
  - labels['payment:delay=2500']['unknown | UNKNOWN | UNKNOWN | APPROVED']: 기대 200, 실제 196
  - labels['payment:delay=500']['completed | COMPLETED | RESERVED | APPROVED']: 기대 200, 실제 194
  - labels['payment:delay=500']['unknown | - | - | -']: 기대 0, 실제 4
  - labels['payment:delay=500']['unknown | COMPLETED | RESERVED | APPROVED']: 기대 0, 실제 2

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 784 | 392 | 0 | 0 | 0 | 0 | 0 | 2 | 408 | 0 |
| inventory | 784 | 588 | 0 | 0 | 0 | 0 | 0 | 198 | 212 | - |
| payment | 784 | 784 | 0 | 0 | 0 | 0 | 0 | 394 | 16 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|
| payment | delay | 600 | 588 **≠** |

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `(없음)` | 200 | `completed | COMPLETED | RESERVED | APPROVED` | 196 | 17.0 | 20.0 | 10001.0 | 10001.0 |
| | | `unknown | - | - | -` | 4 | | | | |
| `payment:delay=1500` | 200 | `unknown | - | - | -` | 4 | 1007.0 | 1011.0 | 10002.0 | 10002.0 |
| | | `unknown | UNKNOWN | RESERVED | APPROVED` | 196 | | | | |
| `payment:delay=2500` | 200 | `unknown | - | - | -` | 4 | 1007.0 | 1009.0 | 10001.0 | 10002.0 |
| | | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 196 | | | | |
| `payment:delay=500` | 200 | `completed | COMPLETED | RESERVED | APPROVED` | 194 | 517.0 | 522.0 | 10001.0 | 10002.0 |
| | | `unknown | - | - | -` | 4 | | | | |
| | | `unknown | COMPLETED | RESERVED | APPROVED` | 2 | | | | |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 800 | 1004.0 | 1008.0 | 10001.0 | 10002.0 |
| completed | 390 | 25.0 | 519.0 | 522.0 | 535.0 |
| failed | 0 | - | - | - | - |
| unknown | 410 | 1007.0 | 10001.0 | 10002.0 | 10002.0 |
