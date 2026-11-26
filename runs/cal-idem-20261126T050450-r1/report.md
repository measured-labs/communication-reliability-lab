# 분석 보고 — cal-idem-20261126T050450-r1

- 정답 550줄, requestId 400개 (계획 550)
- 응답으로 가름: completed 350, failed 0, unknown 200
- **잃음 0 · 두 번 처리됨 350** · 층 사이 처리 수가 다른 requestId 150
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/cal-idem-20261126T050450-r1/expect.json`
- 판정: 통과

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 750 | 600 | 0 | 0 | 0 | 200 | 0 | 200 | 0 | 0 |
| inventory | 750 | 650 | 0 | 0 | 0 | 250 | 0 | 200 | 0 | - |
| payment | 750 | 750 | 0 | 0 | 0 | 350 | 0 | 200 | 0 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `(없음)` | 550 | `completed | COMPLETED | RESERVED | APPROVED` | 50 | 21.0 | 1017.0 | 1020.0 | 1033.0 |
| | | `completed+completed | COMPLETED+COMPLETED | RESERVED+RESERVED | APPROVED+APPROVED` | 150 | | | | |
| | | `unknown | COMPLETED+COMPLETED | RESERVED+RESERVED | APPROVED+APPROVED` | 50 | | | | |
| | | `unknown | COMPLETED+UNKNOWN | RESERVED+RESERVED | APPROVED+APPROVED` | 50 | | | | |
| | | `unknown | COMPLETED+UNKNOWN | RESERVED+UNKNOWN | APPROVED+APPROVED` | 100 | | | | |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 550 | 21.0 | 1017.0 | 1020.0 | 1033.0 |
| completed | 350 | 20.0 | 25.0 | 28.0 | 51.0 |
| failed | 0 | - | - | - | - |
| unknown | 200 | 23.0 | 1019.0 | 1023.0 | 1033.0 |
