# 분석 보고 — false-timeout-20261124T024511-r3

- 정답 1200줄, requestId 1200개 (계획 1200)
- 응답으로 가름: completed 511, failed 0, unknown 689
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 689
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 판정: 통과

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 1200 | 511 | 0 | 0 | 0 | 0 | 0 | 0 | 689 | 0 |
| inventory | 1200 | 511 | 0 | 0 | 0 | 0 | 0 | 0 | 689 | - |
| payment | 1200 | 1200 | 0 | 0 | 0 | 0 | 0 | 689 | 0 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|
| payment | delay | 1200 | 1200 |

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `payment:delay=100` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 107.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=101` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 110.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=102` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 102.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=103` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 104.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=104` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 101.0 | 130.0 | 130.0 | 130.0 |
| `payment:delay=105` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 101.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=106` | 10 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 10 | 105.0 | 125.0 | 125.0 | 125.0 |
| `payment:delay=107` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 105.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=108` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 102.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=109` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 104.0 | 114.0 | 114.0 | 114.0 |
| `payment:delay=110` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 102.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=111` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 102.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=112` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 102.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=113` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 103.0 | 123.0 | 123.0 | 123.0 |
| `payment:delay=114` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 104.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=115` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 101.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=116` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 118.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=117` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 103.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=118` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 102.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=119` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 106.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=120` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 103.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=121` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 100.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=122` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 101.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=123` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 103.0 | 124.0 | 124.0 | 124.0 |
| `payment:delay=124` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 104.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=125` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 106.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=126` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 103.0 | 123.0 | 123.0 | 123.0 |
| `payment:delay=127` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 125.0 | 125.0 | 125.0 | 125.0 |
| `payment:delay=128` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 104.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=129` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 101.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=13` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 44.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=130` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 104.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=131` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 106.0 | 112.0 | 112.0 | 112.0 |
| `payment:delay=132` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 102.0 | 122.0 | 122.0 | 122.0 |
| `payment:delay=133` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 107.0 | 109.0 | 109.0 | 109.0 |
| `payment:delay=134` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 103.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=135` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 102.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=136` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 121.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=137` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 110.0 | 127.0 | 127.0 | 127.0 |
| `payment:delay=138` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 105.0 | 114.0 | 114.0 | 114.0 |
| `payment:delay=139` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 104.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=14` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 46.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=140` | 5 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | 106.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=141` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 106.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=142` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 100.0 | 115.0 | 115.0 | 115.0 |
| `payment:delay=143` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 106.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=144` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 103.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=145` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 101.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=146` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 102.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=147` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 102.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=148` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 105.0 | 109.0 | 109.0 | 109.0 |
| `payment:delay=149` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 108.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=15` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 47.0 | 47.0 | 47.0 | 47.0 |
| `payment:delay=150` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 104.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=151` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 102.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=152` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 105.0 | 123.0 | 123.0 | 123.0 |
| `payment:delay=153` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 105.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=154` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 102.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=155` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 102.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=156` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 104.0 | 112.0 | 112.0 | 112.0 |
| `payment:delay=157` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 104.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=158` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 102.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=159` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 104.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=16` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 44.0 | 65.0 | 65.0 | 65.0 |
| `payment:delay=160` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 102.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=161` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 105.0 | 127.0 | 127.0 | 127.0 |
| `payment:delay=162` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 102.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=163` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 104.0 | 136.0 | 136.0 | 136.0 |
| `payment:delay=164` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 105.0 | 121.0 | 121.0 | 121.0 |
| `payment:delay=165` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 104.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=167` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 102.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=168` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 103.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=17` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 72.0 | 72.0 | 72.0 | 72.0 |
| `payment:delay=170` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 105.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=171` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 110.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=172` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 109.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=173` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 106.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=174` | 4 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | 102.0 | 109.0 | 109.0 | 109.0 |
| `payment:delay=175` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 104.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=176` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 102.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=179` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 104.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=18` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 56.0 | 56.0 | 56.0 | 56.0 |
| `payment:delay=180` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 118.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=181` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 106.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=182` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 104.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=185` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 118.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=187` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 103.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=19` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 51.0 | 52.0 | 52.0 | 52.0 |
| `payment:delay=191` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 101.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=192` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 103.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=193` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 105.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=194` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 106.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=196` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 102.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=197` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 102.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=198` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 111.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=199` | 3 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 3 | 102.0 | 115.0 | 115.0 | 115.0 |
| `payment:delay=20` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 51.0 | 72.0 | 72.0 | 72.0 |
| `payment:delay=200` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 103.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=201` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 104.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=202` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 97.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=203` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 105.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=204` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 102.0 | 115.0 | 115.0 | 115.0 |
| `payment:delay=205` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 97.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=206` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 102.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=207` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 98.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=209` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 102.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=21` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 52.0 | 59.0 | 59.0 | 59.0 |
| `payment:delay=211` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 109.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=212` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 122.0 | 122.0 | 122.0 | 122.0 |
| `payment:delay=213` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 105.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=214` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 105.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=216` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 103.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=218` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 117.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=219` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 100.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=22` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 52.0 | 55.0 | 55.0 | 55.0 |
| `payment:delay=221` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 106.0 | 109.0 | 109.0 | 109.0 |
| `payment:delay=223` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 106.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=224` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 108.0 | 113.0 | 113.0 | 113.0 |
| `payment:delay=225` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 106.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=226` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 103.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=227` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 108.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=23` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 53.0 | 56.0 | 56.0 | 56.0 |
| `payment:delay=234` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 106.0 | 112.0 | 112.0 | 112.0 |
| `payment:delay=235` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 99.0 | 99.0 | 99.0 | 99.0 |
| `payment:delay=236` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 104.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=24` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 54.0 | 61.0 | 61.0 | 61.0 |
| `payment:delay=243` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 104.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=244` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 105.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=245` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 104.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=248` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 103.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=25` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 56.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=250` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 105.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=254` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 118.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=255` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 102.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=258` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 107.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=259` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 103.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=26` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 59.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=268` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 109.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=269` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 102.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=27` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 56.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=272` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 104.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=273` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 107.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=276` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 100.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=278` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 100.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=279` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 106.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=28` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 61.0 | 81.0 | 81.0 | 81.0 |
| `payment:delay=280` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 103.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=281` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 101.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=282` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 104.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=287` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 107.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=29` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 62.0 | 65.0 | 65.0 | 65.0 |
| `payment:delay=292` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 105.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=296` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 102.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=298` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 103.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=30` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 62.0 | 82.0 | 82.0 | 82.0 |
| `payment:delay=300` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 103.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=31` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 60.0 | 80.0 | 80.0 | 80.0 |
| `payment:delay=313` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 102.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=315` | 2 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | 103.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=32` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 63.0 | 66.0 | 66.0 | 66.0 |
| `payment:delay=322` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 113.0 | 113.0 | 113.0 | 113.0 |
| `payment:delay=323` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 127.0 | 127.0 | 127.0 | 127.0 |
| `payment:delay=33` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 65.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=335` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 102.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=338` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 136.0 | 136.0 | 136.0 | 136.0 |
| `payment:delay=34` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 62.0 | 82.0 | 82.0 | 82.0 |
| `payment:delay=346` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 102.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=35` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 67.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=355` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 101.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=358` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 120.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=36` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 68.0 | 73.0 | 73.0 | 73.0 |
| `payment:delay=365` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 101.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=366` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 109.0 | 109.0 | 109.0 | 109.0 |
| `payment:delay=37` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 69.0 | 72.0 | 72.0 | 72.0 |
| `payment:delay=373` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 103.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=374` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 109.0 | 109.0 | 109.0 | 109.0 |
| `payment:delay=38` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 71.0 | 98.0 | 98.0 | 98.0 |
| `payment:delay=39` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 72.0 | 95.0 | 95.0 | 95.0 |
| `payment:delay=40` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 72.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=400` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 103.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=407` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 105.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=41` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 72.0 | 75.0 | 75.0 | 75.0 |
| `payment:delay=42` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 73.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=420` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 112.0 | 112.0 | 112.0 | 112.0 |
| `payment:delay=427` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 101.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=43` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 75.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=44` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 88.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=45` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 77.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=46` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 78.0 | 86.0 | 86.0 | 86.0 |
| `payment:delay=469` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 100.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=47` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 80.0 | 96.0 | 96.0 | 96.0 |
| `payment:delay=48` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 78.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=483` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 103.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=49` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 80.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=50` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 80.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=51` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 84.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=52` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 84.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=523` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 110.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=53` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 86.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=54` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 85.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=55` | 14 | `completed | COMPLETED | RESERVED | APPROVED` | 14 | 88.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=56` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 87.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=57` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 87.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=58` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 93.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=59` | 19 | `completed | COMPLETED | RESERVED | APPROVED` | 19 | 91.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=60` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 91.0 | 112.0 | 112.0 | 112.0 |
| `payment:delay=61` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 93.0 | 109.0 | 109.0 | 109.0 |
| `payment:delay=62` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 96.0 | 115.0 | 115.0 | 115.0 |
| `payment:delay=622` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 100.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=63` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 97.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=64` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 101.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=65` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 95.0 | 98.0 | 98.0 | 98.0 |
| `payment:delay=66` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 100.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=663` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 103.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=67` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 97.0 | 134.0 | 134.0 | 134.0 |
| | | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 2 | | | | |
| `payment:delay=68` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 99.0 | 125.0 | 125.0 | 125.0 |
| `payment:delay=69` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 100.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=70` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 102.0 | 123.0 | 123.0 | 123.0 |
| | | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | | | | |
| `payment:delay=71` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 104.0 | 124.0 | 124.0 | 124.0 |
| | | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 4 | | | | |
| `payment:delay=72` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 102.0 | 120.0 | 120.0 | 120.0 |
| | | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 5 | | | | |
| `payment:delay=73` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 101.0 | 118.0 | 118.0 | 118.0 |
| | | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 10 | | | | |
| `payment:delay=74` | 11 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 11 | 105.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=75` | 20 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 20 | 104.0 | 110.0 | 116.0 | 116.0 |
| `payment:delay=76` | 13 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 13 | 106.0 | 130.0 | 130.0 | 130.0 |
| `payment:delay=77` | 11 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 11 | 105.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=78` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 107.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=79` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 104.0 | 114.0 | 114.0 | 114.0 |
| `payment:delay=80` | 10 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 10 | 103.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=81` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 104.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=82` | 12 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 12 | 104.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=83` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 109.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=84` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 106.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=85` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 103.0 | 125.0 | 125.0 | 125.0 |
| `payment:delay=86` | 7 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 7 | 102.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=87` | 10 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 10 | 104.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=88` | 10 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 10 | 102.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=89` | 11 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 11 | 104.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=9` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 38.0 | 38.0 | 38.0 | 38.0 |
| `payment:delay=90` | 15 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 15 | 110.0 | 130.0 | 130.0 | 130.0 |
| `payment:delay=91` | 6 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 6 | 105.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=92` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 104.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=93` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 103.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=94` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 103.0 | 122.0 | 122.0 | 122.0 |
| `payment:delay=95` | 11 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 11 | 104.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=96` | 8 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 8 | 102.0 | 123.0 | 123.0 | 123.0 |
| `payment:delay=97` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 107.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=98` | 11 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 11 | 103.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=99` | 9 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 9 | 104.0 | 123.0 | 123.0 | 123.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 1200 | 102.0 | 119.0 | 128.0 | 136.0 |
| completed | 511 | 84.0 | 107.0 | 118.0 | 134.0 |
| failed | 0 | - | - | - | - |
| unknown | 689 | 104.0 | 123.0 | 129.0 | 136.0 |
