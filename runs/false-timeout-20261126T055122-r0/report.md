# 분석 보고 — false-timeout-20261126T055122-r0

- 정답 1200줄, requestId 1200개 (계획 1200)
- 응답으로 가름: completed 1200, failed 0, unknown 0
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 0
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/false-timeout-20261126T055122-r0/expect.json`
- 판정: 통과

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 1200 | 1200 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| inventory | 1200 | 1200 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| payment | 1200 | 1200 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|
| payment | delay | 1200 | 1200 |

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `payment:delay=100` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 114.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=101` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 115.0 | 121.0 | 121.0 | 121.0 |
| `payment:delay=102` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 116.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=103` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 114.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=104` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 122.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=105` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 116.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=106` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 122.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=107` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 121.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=108` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 123.0 | 130.0 | 130.0 | 130.0 |
| `payment:delay=109` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 121.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=110` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 124.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=111` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 125.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=112` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 128.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=113` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 128.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=114` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 130.0 | 132.0 | 132.0 | 132.0 |
| `payment:delay=115` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 128.0 | 132.0 | 132.0 | 132.0 |
| `payment:delay=116` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 131.0 | 142.0 | 142.0 | 142.0 |
| `payment:delay=117` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 132.0 | 135.0 | 135.0 | 135.0 |
| `payment:delay=118` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 133.0 | 133.0 | 133.0 | 133.0 |
| `payment:delay=119` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 134.0 | 134.0 | 134.0 | 134.0 |
| `payment:delay=120` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 135.0 | 137.0 | 137.0 | 137.0 |
| `payment:delay=121` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 129.0 | 135.0 | 135.0 | 135.0 |
| `payment:delay=122` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 137.0 | 140.0 | 140.0 | 140.0 |
| `payment:delay=123` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 135.0 | 141.0 | 141.0 | 141.0 |
| `payment:delay=124` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 139.0 | 143.0 | 143.0 | 143.0 |
| `payment:delay=125` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 141.0 | 144.0 | 144.0 | 144.0 |
| `payment:delay=126` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 137.0 | 145.0 | 145.0 | 145.0 |
| `payment:delay=127` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 145.0 | 145.0 | 145.0 | 145.0 |
| `payment:delay=128` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 142.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=129` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 143.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=13` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 30.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=130` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 147.0 | 147.0 | 147.0 | 147.0 |
| `payment:delay=131` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 145.0 | 159.0 | 159.0 | 159.0 |
| `payment:delay=132` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 145.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=133` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 148.0 | 149.0 | 149.0 | 149.0 |
| `payment:delay=134` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 146.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=135` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 150.0 | 151.0 | 151.0 | 151.0 |
| `payment:delay=136` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 155.0 | 156.0 | 156.0 | 156.0 |
| `payment:delay=137` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 150.0 | 156.0 | 156.0 | 156.0 |
| `payment:delay=138` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 152.0 | 153.0 | 153.0 | 153.0 |
| `payment:delay=139` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 158.0 | 159.0 | 159.0 | 159.0 |
| `payment:delay=14` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 26.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=140` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 152.0 | 157.0 | 157.0 | 157.0 |
| `payment:delay=141` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 155.0 | 164.0 | 164.0 | 164.0 |
| `payment:delay=142` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 157.0 | 157.0 | 157.0 | 157.0 |
| `payment:delay=143` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 155.0 | 158.0 | 158.0 | 158.0 |
| `payment:delay=144` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 160.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=145` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 161.0 | 161.0 | 161.0 | 161.0 |
| `payment:delay=146` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 161.0 | 162.0 | 162.0 | 162.0 |
| `payment:delay=147` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 160.0 | 162.0 | 162.0 | 162.0 |
| `payment:delay=148` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 163.0 | 164.0 | 164.0 | 164.0 |
| `payment:delay=149` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 159.0 | 165.0 | 165.0 | 165.0 |
| `payment:delay=15` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 33.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=150` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 167.0 | 167.0 | 167.0 | 167.0 |
| `payment:delay=151` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 158.0 | 165.0 | 165.0 | 165.0 |
| `payment:delay=152` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 167.0 | 169.0 | 169.0 | 169.0 |
| `payment:delay=153` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 167.0 | 167.0 | 167.0 | 167.0 |
| `payment:delay=154` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 164.0 | 171.0 | 171.0 | 171.0 |
| `payment:delay=155` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 170.0 | 171.0 | 171.0 | 171.0 |
| `payment:delay=156` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 170.0 | 171.0 | 171.0 | 171.0 |
| `payment:delay=157` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 170.0 | 173.0 | 173.0 | 173.0 |
| `payment:delay=158` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 165.0 | 170.0 | 170.0 | 170.0 |
| `payment:delay=159` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 171.0 | 176.0 | 176.0 | 176.0 |
| `payment:delay=16` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 26.0 | 35.0 | 35.0 | 35.0 |
| `payment:delay=160` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 175.0 | 175.0 | 175.0 | 175.0 |
| `payment:delay=161` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 175.0 | 176.0 | 176.0 | 176.0 |
| `payment:delay=162` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 177.0 | 183.0 | 183.0 | 183.0 |
| `payment:delay=163` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 178.0 | 181.0 | 181.0 | 181.0 |
| `payment:delay=164` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 174.0 | 176.0 | 176.0 | 176.0 |
| `payment:delay=165` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 178.0 | 183.0 | 183.0 | 183.0 |
| `payment:delay=167` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 175.0 | 175.0 | 175.0 | 175.0 |
| `payment:delay=168` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 183.0 | 187.0 | 187.0 | 187.0 |
| `payment:delay=17` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 33.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=170` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 188.0 | 189.0 | 189.0 | 189.0 |
| `payment:delay=171` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 182.0 | 185.0 | 185.0 | 185.0 |
| `payment:delay=172` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 186.0 | 190.0 | 190.0 | 190.0 |
| `payment:delay=173` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 185.0 | 190.0 | 190.0 | 190.0 |
| `payment:delay=174` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 188.0 | 190.0 | 190.0 | 190.0 |
| `payment:delay=175` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 189.0 | 189.0 | 189.0 | 189.0 |
| `payment:delay=176` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 189.0 | 189.0 | 189.0 | 189.0 |
| `payment:delay=179` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 193.0 | 195.0 | 195.0 | 195.0 |
| `payment:delay=18` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 33.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=180` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 194.0 | 194.0 | 194.0 | 194.0 |
| `payment:delay=181` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 196.0 | 196.0 | 196.0 | 196.0 |
| `payment:delay=182` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 197.0 | 199.0 | 199.0 | 199.0 |
| `payment:delay=185` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 203.0 | 203.0 | 203.0 | 203.0 |
| `payment:delay=187` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 198.0 | 204.0 | 204.0 | 204.0 |
| `payment:delay=19` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 36.0 | 36.0 | 36.0 | 36.0 |
| `payment:delay=191` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 205.0 | 205.0 | 205.0 | 205.0 |
| `payment:delay=192` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 207.0 | 207.0 | 207.0 | 207.0 |
| `payment:delay=193` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 208.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=194` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 210.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=196` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 207.0 | 218.0 | 218.0 | 218.0 |
| `payment:delay=197` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 211.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=198` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 216.0 | 216.0 | 216.0 | 216.0 |
| `payment:delay=199` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 213.0 | 213.0 | 213.0 | 213.0 |
| `payment:delay=20` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 31.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=200` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 208.0 | 208.0 | 208.0 | 208.0 |
| `payment:delay=201` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 217.0 | 217.0 | 217.0 | 217.0 |
| `payment:delay=202` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 216.0 | 220.0 | 220.0 | 220.0 |
| `payment:delay=203` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 218.0 | 218.0 | 218.0 | 218.0 |
| `payment:delay=204` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 212.0 | 218.0 | 218.0 | 218.0 |
| `payment:delay=205` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 217.0 | 220.0 | 220.0 | 220.0 |
| `payment:delay=206` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 221.0 | 224.0 | 224.0 | 224.0 |
| `payment:delay=207` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 220.0 | 221.0 | 221.0 | 221.0 |
| `payment:delay=209` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 225.0 | 225.0 | 225.0 | 225.0 |
| `payment:delay=21` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 37.0 | 40.0 | 40.0 | 40.0 |
| `payment:delay=211` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 225.0 | 230.0 | 230.0 | 230.0 |
| `payment:delay=212` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 242.0 | 242.0 | 242.0 | 242.0 |
| `payment:delay=213` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 227.0 | 227.0 | 227.0 | 227.0 |
| `payment:delay=214` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 227.0 | 231.0 | 231.0 | 231.0 |
| `payment:delay=216` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 227.0 | 227.0 | 227.0 | 227.0 |
| `payment:delay=218` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 235.0 | 235.0 | 235.0 | 235.0 |
| `payment:delay=219` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 231.0 | 234.0 | 234.0 | 234.0 |
| `payment:delay=22` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 37.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=221` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 237.0 | 242.0 | 242.0 | 242.0 |
| `payment:delay=223` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 235.0 | 235.0 | 235.0 | 235.0 |
| `payment:delay=224` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 239.0 | 244.0 | 244.0 | 244.0 |
| `payment:delay=225` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 240.0 | 240.0 | 240.0 | 240.0 |
| `payment:delay=226` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 240.0 | 246.0 | 246.0 | 246.0 |
| `payment:delay=227` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 241.0 | 241.0 | 241.0 | 241.0 |
| `payment:delay=23` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 36.0 | 38.0 | 38.0 | 38.0 |
| `payment:delay=234` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 248.0 | 251.0 | 251.0 | 251.0 |
| `payment:delay=235` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 250.0 | 250.0 | 250.0 | 250.0 |
| `payment:delay=236` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 253.0 | 255.0 | 255.0 | 255.0 |
| `payment:delay=24` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 38.0 | 46.0 | 46.0 | 46.0 |
| `payment:delay=243` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 260.0 | 260.0 | 260.0 | 260.0 |
| `payment:delay=244` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 260.0 | 260.0 | 260.0 | 260.0 |
| `payment:delay=245` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 261.0 | 261.0 | 261.0 | 261.0 |
| `payment:delay=248` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 259.0 | 259.0 | 259.0 | 259.0 |
| `payment:delay=25` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 40.0 | 44.0 | 44.0 | 44.0 |
| `payment:delay=250` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 267.0 | 267.0 | 267.0 | 267.0 |
| `payment:delay=254` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 271.0 | 271.0 | 271.0 | 271.0 |
| `payment:delay=255` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 268.0 | 269.0 | 269.0 | 269.0 |
| `payment:delay=258` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 274.0 | 274.0 | 274.0 | 274.0 |
| `payment:delay=259` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 273.0 | 273.0 | 273.0 | 273.0 |
| `payment:delay=26` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 41.0 | 46.0 | 46.0 | 46.0 |
| `payment:delay=268` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 283.0 | 283.0 | 283.0 | 283.0 |
| `payment:delay=269` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 281.0 | 287.0 | 287.0 | 287.0 |
| `payment:delay=27` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 42.0 | 44.0 | 44.0 | 44.0 |
| `payment:delay=272` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 291.0 | 291.0 | 291.0 | 291.0 |
| `payment:delay=273` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 290.0 | 290.0 | 290.0 | 290.0 |
| `payment:delay=276` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 290.0 | 290.0 | 290.0 | 290.0 |
| `payment:delay=278` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 293.0 | 293.0 | 293.0 | 293.0 |
| `payment:delay=279` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 286.0 | 286.0 | 286.0 | 286.0 |
| `payment:delay=28` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 43.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=280` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 296.0 | 296.0 | 296.0 | 296.0 |
| `payment:delay=281` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 296.0 | 296.0 | 296.0 | 296.0 |
| `payment:delay=282` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 301.0 | 301.0 | 301.0 | 301.0 |
| `payment:delay=287` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 298.0 | 298.0 | 298.0 | 298.0 |
| `payment:delay=29` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 36.0 | 43.0 | 43.0 | 43.0 |
| `payment:delay=292` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 306.0 | 306.0 | 306.0 | 306.0 |
| `payment:delay=296` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 316.0 | 316.0 | 316.0 | 316.0 |
| `payment:delay=298` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 309.0 | 312.0 | 312.0 | 312.0 |
| `payment:delay=30` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 45.0 | 54.0 | 54.0 | 54.0 |
| `payment:delay=300` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 315.0 | 315.0 | 315.0 | 315.0 |
| `payment:delay=31` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 47.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=313` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 327.0 | 327.0 | 327.0 | 327.0 |
| `payment:delay=315` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 331.0 | 339.0 | 339.0 | 339.0 |
| `payment:delay=32` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 46.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=322` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 336.0 | 336.0 | 336.0 | 336.0 |
| `payment:delay=323` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 331.0 | 331.0 | 331.0 | 331.0 |
| `payment:delay=33` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 47.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=335` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 348.0 | 348.0 | 348.0 | 348.0 |
| `payment:delay=338` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 355.0 | 355.0 | 355.0 | 355.0 |
| `payment:delay=34` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 48.0 | 52.0 | 52.0 | 52.0 |
| `payment:delay=346` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 361.0 | 361.0 | 361.0 | 361.0 |
| `payment:delay=35` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 47.0 | 49.0 | 49.0 | 49.0 |
| `payment:delay=355` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 365.0 | 365.0 | 365.0 | 365.0 |
| `payment:delay=358` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 371.0 | 371.0 | 371.0 | 371.0 |
| `payment:delay=36` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 49.0 | 54.0 | 54.0 | 54.0 |
| `payment:delay=365` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 384.0 | 384.0 | 384.0 | 384.0 |
| `payment:delay=366` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 385.0 | 385.0 | 385.0 | 385.0 |
| `payment:delay=37` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 50.0 | 53.0 | 53.0 | 53.0 |
| `payment:delay=373` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 388.0 | 388.0 | 388.0 | 388.0 |
| `payment:delay=374` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 390.0 | 390.0 | 390.0 | 390.0 |
| `payment:delay=38` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 54.0 | 57.0 | 57.0 | 57.0 |
| `payment:delay=39` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 55.0 | 62.0 | 62.0 | 62.0 |
| `payment:delay=40` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 55.0 | 57.0 | 57.0 | 57.0 |
| `payment:delay=400` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 415.0 | 415.0 | 415.0 | 415.0 |
| `payment:delay=407` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 418.0 | 418.0 | 418.0 | 418.0 |
| `payment:delay=41` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 57.0 | 59.0 | 59.0 | 59.0 |
| `payment:delay=42` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 57.0 | 60.0 | 60.0 | 60.0 |
| `payment:delay=420` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 430.0 | 430.0 | 430.0 | 430.0 |
| `payment:delay=427` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 438.0 | 438.0 | 438.0 | 438.0 |
| `payment:delay=43` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 58.0 | 63.0 | 63.0 | 63.0 |
| `payment:delay=44` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 60.0 | 64.0 | 64.0 | 64.0 |
| `payment:delay=45` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 59.0 | 64.0 | 64.0 | 64.0 |
| `payment:delay=46` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 61.0 | 63.0 | 63.0 | 63.0 |
| `payment:delay=469` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 483.0 | 483.0 | 483.0 | 483.0 |
| `payment:delay=47` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 62.0 | 65.0 | 65.0 | 65.0 |
| `payment:delay=48` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 62.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=483` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 502.0 | 502.0 | 502.0 | 502.0 |
| `payment:delay=49` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 64.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=50` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 65.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=51` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 66.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=52` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 66.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=523` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 541.0 | 541.0 | 541.0 | 541.0 |
| `payment:delay=53` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 66.0 | 71.0 | 71.0 | 71.0 |
| `payment:delay=54` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 69.0 | 73.0 | 73.0 | 73.0 |
| `payment:delay=55` | 14 | `completed | COMPLETED | RESERVED | APPROVED` | 14 | 69.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=56` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 71.0 | 75.0 | 75.0 | 75.0 |
| `payment:delay=57` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 69.0 | 79.0 | 79.0 | 79.0 |
| `payment:delay=58` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 72.0 | 86.0 | 86.0 | 86.0 |
| `payment:delay=59` | 19 | `completed | COMPLETED | RESERVED | APPROVED` | 19 | 74.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=60` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 75.0 | 80.0 | 80.0 | 80.0 |
| `payment:delay=61` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 76.0 | 81.0 | 81.0 | 81.0 |
| `payment:delay=62` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 77.0 | 81.0 | 81.0 | 81.0 |
| `payment:delay=622` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 636.0 | 636.0 | 636.0 | 636.0 |
| `payment:delay=63` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 78.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=64` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 79.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=65` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 78.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=66` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 79.0 | 83.0 | 83.0 | 83.0 |
| `payment:delay=663` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 678.0 | 678.0 | 678.0 | 678.0 |
| `payment:delay=67` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 84.0 | 86.0 | 86.0 | 86.0 |
| `payment:delay=68` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 84.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=69` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 80.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=70` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 85.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=71` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 87.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=72` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 86.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=73` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 88.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=74` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 88.0 | 94.0 | 94.0 | 94.0 |
| `payment:delay=75` | 20 | `completed | COMPLETED | RESERVED | APPROVED` | 20 | 89.0 | 94.0 | 96.0 | 96.0 |
| `payment:delay=76` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 91.0 | 95.0 | 95.0 | 95.0 |
| `payment:delay=77` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 93.0 | 96.0 | 96.0 | 96.0 |
| `payment:delay=78` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 93.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=79` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 91.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=80` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 95.0 | 99.0 | 99.0 | 99.0 |
| `payment:delay=81` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 96.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=82` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 96.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=83` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 95.0 | 99.0 | 99.0 | 99.0 |
| `payment:delay=84` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 100.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=85` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 100.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=86` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 99.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=87` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 99.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=88` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 100.0 | 104.0 | 104.0 | 104.0 |
| `payment:delay=89` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 105.0 | 112.0 | 112.0 | 112.0 |
| `payment:delay=9` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 28.0 | 28.0 | 28.0 | 28.0 |
| `payment:delay=90` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 105.0 | 109.0 | 109.0 | 109.0 |
| `payment:delay=91` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 105.0 | 109.0 | 109.0 | 109.0 |
| `payment:delay=92` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 108.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=93` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 108.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=94` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 108.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=95` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 110.0 | 112.0 | 112.0 | 112.0 |
| `payment:delay=96` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 110.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=97` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 112.0 | 113.0 | 113.0 | 113.0 |
| `payment:delay=98` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 112.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=99` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 113.0 | 118.0 | 118.0 | 118.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 1200 | 94.0 | 241.0 | 384.0 | 678.0 |
| completed | 1200 | 94.0 | 241.0 | 384.0 | 678.0 |
| failed | 0 | - | - | - | - |
| unknown | 0 | - | - | - | - |
