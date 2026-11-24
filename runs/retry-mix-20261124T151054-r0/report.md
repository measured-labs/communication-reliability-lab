# 분석 보고 — retry-mix-20261124T151054-r0

- 정답 800줄, requestId 800개 (계획 800)
- 응답으로 가름: completed 764, failed 0, unknown 36
- **잃음 0 · 두 번 처리됨 119** · 층 사이 처리 수가 다른 requestId 119
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 판정: 통과

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 800 | 764 | 0 | 0 | 0 | 0 | 0 | 0 | 36 | 0 |
| inventory | 1078 | 883 | 0 | 0 | 0 | 119 | 0 | 0 | 36 | - |
| payment | 926 | 926 | 0 | 0 | 0 | 119 | 0 | 0 | 36 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `(없음)` | 800 | `completed | COMPLETED | FAILED+RESERVED | APPROVED` | 87 | 131.0 | 502.0 | 1071.0 | 1077.0 |
| | | `completed | COMPLETED | RESERVED | APPROVED` | 558 | | | | |
| | | `completed | COMPLETED | RESERVED+RESERVED | APPROVED+APPROVED` | 76 | | | | |
| | | `completed | COMPLETED | RESERVED+RESERVED | APPROVED+APPROVED+APPROVED` | 43 | | | | |
| | | `unknown | UNKNOWN | FAILED+FAILED+FAILED | -` | 36 | | | | |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 800 | 131.0 | 502.0 | 1071.0 | 1077.0 |
| completed | 764 | 56.0 | 487.0 | 500.0 | 522.0 |
| failed | 0 | - | - | - | - |
| unknown | 36 | 1068.0 | 1076.0 | 1077.0 | 1077.0 |
