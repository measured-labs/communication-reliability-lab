# 분석 보고 — false-timeout-20261126T060636-r3

- 정답 1200줄, requestId 1200개 (계획 1200)
- 응답으로 가름: completed 576, failed 0, unknown 624
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 624
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/false-timeout-20261126T060636-r3/expect.json`
- 판정: 통과

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 1200 | 576 | 0 | 0 | 0 | 0 | 0 | 0 | 624 | 0 |
| inventory | 1200 | 576 | 0 | 0 | 0 | 0 | 0 | 0 | 624 | - |
| payment | 1200 | 1200 | 0 | 0 | 0 | 0 | 0 | 624 | 0 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|
| payment | delay | 1200 | 1200 |

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `payment:delay=100` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=101` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=102` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=103` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=104` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 92.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=105` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=106` | 10 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 10 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=107` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=108` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=109` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 88.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=110` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=111` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=112` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 91.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=113` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 89.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=114` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 91.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=115` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 89.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=116` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 90.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=117` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 88.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=118` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 89.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=119` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=120` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 89.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=121` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 89.0 | 99.0 | 99.0 | 99.0 |
| `payment:delay=122` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 89.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=123` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 91.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=124` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=125` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=126` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=127` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=128` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 89.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=129` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=13` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 26.0 | 30.0 | 30.0 | 30.0 |
| `payment:delay=130` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=131` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=132` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 89.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=133` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 89.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=134` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=135` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=136` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 91.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=137` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 89.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=138` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=139` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=14` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 30.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=140` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=141` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 89.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=142` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 88.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=143` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 91.0 | 95.0 | 95.0 | 95.0 |
| `payment:delay=144` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=145` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=146` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 89.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=147` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 86.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=148` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=149` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 89.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=15` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 31.0 | 31.0 | 31.0 | 31.0 |
| `payment:delay=150` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=151` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 87.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=152` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=153` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 86.0 | 86.0 | 86.0 | 86.0 |
| `payment:delay=154` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=155` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=156` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 88.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=157` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 88.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=158` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 89.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=159` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=16` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 27.0 | 31.0 | 31.0 | 31.0 |
| `payment:delay=160` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 89.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=161` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=162` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=163` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=164` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 91.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=165` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 89.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=167` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=168` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=17` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 33.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=170` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 89.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=171` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 89.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=172` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 87.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=173` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=174` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 88.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=175` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=176` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 87.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=179` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 86.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=18` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 32.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=180` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 89.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=181` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=182` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=185` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=187` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 85.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=19` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 33.0 | 36.0 | 36.0 | 36.0 |
| `payment:delay=191` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 87.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=192` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 88.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=193` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 89.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=194` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=196` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 89.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=197` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 89.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=198` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 88.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=199` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=20` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 28.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=200` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=201` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=202` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 87.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=203` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 87.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=204` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=205` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=206` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=207` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=209` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=21` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 31.0 | 35.0 | 35.0 | 35.0 |
| `payment:delay=211` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 88.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=212` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=213` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=214` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 87.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=216` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=218` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=219` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 85.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=22` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 36.0 | 37.0 | 37.0 | 37.0 |
| `payment:delay=221` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 87.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=223` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=224` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 89.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=225` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=226` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 91.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=227` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=23` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 30.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=234` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=235` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 87.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=236` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 91.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=24` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 37.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=243` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 87.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=244` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=245` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 87.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=248` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=25` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 36.0 | 41.0 | 41.0 | 41.0 |
| `payment:delay=250` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 89.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=254` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 92.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=255` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 88.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=258` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=259` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 87.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=26` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 39.0 | 40.0 | 40.0 | 40.0 |
| `payment:delay=268` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 87.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=269` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 86.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=27` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 41.0 | 42.0 | 42.0 | 42.0 |
| `payment:delay=272` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 89.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=273` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=276` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 85.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=278` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 88.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=279` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=28` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 41.0 | 43.0 | 43.0 | 43.0 |
| `payment:delay=280` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=281` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=282` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 86.0 | 86.0 | 86.0 | 86.0 |
| `payment:delay=287` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 89.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=29` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 40.0 | 40.0 | 40.0 | 40.0 |
| `payment:delay=292` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=296` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 92.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=298` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 86.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=30` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 43.0 | 45.0 | 45.0 | 45.0 |
| `payment:delay=300` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=31` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 44.0 | 46.0 | 46.0 | 46.0 |
| `payment:delay=313` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=315` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=32` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 45.0 | 46.0 | 46.0 | 46.0 |
| `payment:delay=322` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 85.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=323` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 93.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=33` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 47.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=335` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 87.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=338` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=34` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 44.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=346` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=35` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 49.0 | 49.0 | 49.0 | 49.0 |
| `payment:delay=355` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=358` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=36` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 50.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=365` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 89.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=366` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 92.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=37` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 51.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=373` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=374` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 93.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=38` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 52.0 | 54.0 | 54.0 | 54.0 |
| `payment:delay=39` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 53.0 | 55.0 | 55.0 | 55.0 |
| `payment:delay=40` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 54.0 | 55.0 | 55.0 | 55.0 |
| `payment:delay=400` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=407` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 88.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=41` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 55.0 | 56.0 | 56.0 | 56.0 |
| `payment:delay=42` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 56.0 | 58.0 | 58.0 | 58.0 |
| `payment:delay=420` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=427` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=43` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 57.0 | 61.0 | 61.0 | 61.0 |
| `payment:delay=44` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 58.0 | 61.0 | 61.0 | 61.0 |
| `payment:delay=45` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 58.0 | 60.0 | 60.0 | 60.0 |
| `payment:delay=46` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 59.0 | 61.0 | 61.0 | 61.0 |
| `payment:delay=469` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 88.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=47` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 61.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=48` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 62.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=483` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 92.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=49` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 63.0 | 64.0 | 64.0 | 64.0 |
| `payment:delay=50` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 63.0 | 66.0 | 66.0 | 66.0 |
| `payment:delay=51` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 65.0 | 70.0 | 70.0 | 70.0 |
| `payment:delay=52` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 65.0 | 67.0 | 67.0 | 67.0 |
| `payment:delay=523` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 92.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=53` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 66.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=54` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 67.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=55` | 14 | `completed | COMPLETED | RESERVED | APPROVED` | 14 | 69.0 | 73.0 | 73.0 | 73.0 |
| `payment:delay=56` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 70.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=57` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 70.0 | 75.0 | 75.0 | 75.0 |
| `payment:delay=58` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 72.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=59` | 19 | `completed | COMPLETED | RESERVED | APPROVED` | 19 | 72.0 | 75.0 | 75.0 | 75.0 |
| `payment:delay=60` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 73.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=61` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 75.0 | 77.0 | 77.0 | 77.0 |
| `payment:delay=62` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 76.0 | 77.0 | 77.0 | 77.0 |
| `payment:delay=622` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=63` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 76.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=64` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 78.0 | 79.0 | 79.0 | 79.0 |
| `payment:delay=65` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 79.0 | 80.0 | 80.0 | 80.0 |
| `payment:delay=66` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 80.0 | 81.0 | 81.0 | 81.0 |
| `payment:delay=663` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 90.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=67` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 82.0 | 86.0 | 86.0 | 86.0 |
| `payment:delay=68` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 82.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=69` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 83.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=70` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 84.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=71` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 84.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=72` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 86.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=73` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 86.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=74` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 88.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=75` | 20 | `completed | COMPLETED | RESERVED | APPROVED` | 20 | 88.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=76` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 89.0 | 92.0 | 92.0 | 92.0 |
| | | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | | | | |
| `payment:delay=77` | 11 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 11 | 91.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=78` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=79` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 90.0 | 95.0 | 95.0 | 95.0 |
| `payment:delay=80` | 10 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 10 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=81` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=82` | 12 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 12 | 90.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=83` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 89.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=84` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=85` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 91.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=86` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=87` | 10 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 10 | 90.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=88` | 10 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 10 | 89.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=89` | 11 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 11 | 90.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=9` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 24.0 | 24.0 | 24.0 | 24.0 |
| `payment:delay=90` | 15 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 15 | 91.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=91` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=92` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=93` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=94` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 90.0 | 98.0 | 98.0 | 98.0 |
| `payment:delay=95` | 11 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 11 | 89.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=96` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=97` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 90.0 | 99.0 | 99.0 | 99.0 |
| `payment:delay=98` | 11 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 11 | 90.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=99` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 90.0 | 90.0 | 90.0 | 90.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 1200 | 87.0 | 92.0 | 93.0 | 118.0 |
| completed | 576 | 65.0 | 87.0 | 90.0 | 91.0 |
| failed | 0 | - | - | - | - |
| unknown | 624 | 90.0 | 92.0 | 97.0 | 118.0 |
