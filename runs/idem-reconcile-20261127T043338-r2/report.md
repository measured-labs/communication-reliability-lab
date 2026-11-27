# 분석 보고 — idem-reconcile-20261127T043338-r2

- 정답 800줄, requestId 800개 (계획 800)
- 응답으로 가름: completed 400, failed 0, unknown 400
- **잃음 0 · 두 번 처리됨 400** · 층 사이 처리 수가 다른 requestId 320
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/idem-reconcile-20261127T043338-r2/expect.json`
- 판정: 통과

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 1200 | 880 | 0 | 0 | 0 | 80 | 0 | 400 | 0 | 0 |
| inventory | 1200 | 1040 | 0 | 0 | 0 | 240 | 0 | 400 | 0 | - |
| payment | 1200 | 1200 | 0 | 0 | 0 | 400 | 0 | 400 | 0 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `(없음)` | 800 | `completed | COMPLETED | RESERVED | APPROVED` | 400 | 9.0 | 2004.0 | 2005.0 | 2010.0 |
| | | `unknown | COMPLETED+COMPLETED | RESERVED+RESERVED | APPROVED+APPROVED` | 80 | | | | |
| | | `unknown | COMPLETED+UNKNOWN | RESERVED+RESERVED | APPROVED+APPROVED` | 160 | | | | |
| | | `unknown | COMPLETED+UNKNOWN | RESERVED+UNKNOWN | APPROVED+APPROVED` | 160 | | | | |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 800 | 9.0 | 2004.0 | 2005.0 | 2010.0 |
| completed | 400 | 8.0 | 10.0 | 15.0 | 27.0 |
| failed | 0 | - | - | - | - |
| unknown | 400 | 9.0 | 2004.0 | 2005.0 | 2010.0 |
