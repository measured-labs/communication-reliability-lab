# 분석 보고 — idem-retry-mix-20261127T031811-r1

- 정답 800줄, requestId 800개 (계획 800)
- 응답으로 가름: completed 764, failed 0, unknown 36
- **잃음 0 · 두 번 처리됨 198** · 층 사이 처리 수가 다른 requestId 198
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/idem-retry-mix-20261127T031811-r1/expect.json`
- 판정: 통과

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 800 | 764 | 0 | 0 | 0 | 0 | 0 | 0 | 36 | 0 |
| inventory | 1078 | 883 | 0 | 0 | 0 | 119 | 0 | 0 | 36 | - |
| payment | 1005 | 1005 | 0 | 0 | 0 | 198 | 0 | 0 | 36 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `(없음)` | 800 | `completed | COMPLETED | FAILED+RESERVED | APPROVED` | 87 | 115.0 | 470.0 | 1057.0 | 1058.0 |
| | | `completed | COMPLETED | RESERVED | APPROVED` | 479 | | | | |
| | | `completed | COMPLETED | RESERVED | APPROVED+APPROVED` | 79 | | | | |
| | | `completed | COMPLETED | RESERVED+RESERVED | APPROVED+APPROVED` | 76 | | | | |
| | | `completed | COMPLETED | RESERVED+RESERVED | APPROVED+APPROVED+APPROVED` | 43 | | | | |
| | | `unknown | UNKNOWN | FAILED+FAILED+FAILED | -` | 36 | | | | |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 800 | 115.0 | 470.0 | 1057.0 | 1058.0 |
| completed | 764 | 114.0 | 462.0 | 470.0 | 473.0 |
| failed | 0 | - | - | - | - |
| unknown | 36 | 1056.0 | 1058.0 | 1058.0 | 1058.0 |
