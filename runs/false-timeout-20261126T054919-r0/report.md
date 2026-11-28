# 분석 보고 — false-timeout-20261126T054919-r0

- 정답 1200줄, requestId 1200개 (계획 1200)
- 응답으로 가름: completed 1180, failed 0, unknown 20
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 20
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/false-timeout-20261126T054919-r0/expect.json`
- 판정: 통과

## 층마다

| 층 | 행 | 처리 행 | PENDING | 정답에 없는 행 | 잃음 | 두 번 처리됨 | 실패 응답인데 처리됨 | 결과 모름 → 처리됨 | 결과 모름 → 처리 안 됨 | 금액 불일치 |
|---|---|---|---|---|---|---|---|---|---|---|
| order | 1200 | 1180 | 0 | 0 | 0 | 0 | 0 | 0 | 20 | 0 |
| inventory | 1200 | 1180 | 0 | 0 | 0 | 0 | 0 | 0 | 20 | - |
| payment | 1200 | 1200 | 0 | 0 | 0 | 0 | 0 | 20 | 0 | 0 |

## 장애 수

| 서비스 | 종류 | 넣은 수 | 서비스가 센 수 |
|---|---|---|---|
| payment | delay | 1200 | 1200 |

## 장애 라벨마다 (서명 = 응답 | order | inventory | payment)

| 라벨 | 정답 | 서명 | requestId 수 | p50 | p95 | p99 | max (ms) |
|---|---|---|---|---|---|---|---|
| `payment:delay=100` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 113.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=101` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 116.0 | 121.0 | 121.0 | 121.0 |
| `payment:delay=102` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 117.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=103` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 119.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=104` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 120.0 | 124.0 | 124.0 | 124.0 |
| `payment:delay=105` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 117.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=106` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 119.0 | 125.0 | 125.0 | 125.0 |
| `payment:delay=107` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 123.0 | 127.0 | 127.0 | 127.0 |
| `payment:delay=108` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 124.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=109` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 124.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=110` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 121.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=111` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 126.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=112` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 127.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=113` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 127.0 | 132.0 | 132.0 | 132.0 |
| `payment:delay=114` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 127.0 | 135.0 | 135.0 | 135.0 |
| `payment:delay=115` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 129.0 | 132.0 | 132.0 | 132.0 |
| `payment:delay=116` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 135.0 | 148.0 | 148.0 | 148.0 |
| `payment:delay=117` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 132.0 | 137.0 | 137.0 | 137.0 |
| `payment:delay=118` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 130.0 | 135.0 | 135.0 | 135.0 |
| `payment:delay=119` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 132.0 | 132.0 | 132.0 | 132.0 |
| `payment:delay=120` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 132.0 | 136.0 | 136.0 | 136.0 |
| `payment:delay=121` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 132.0 | 138.0 | 138.0 | 138.0 |
| `payment:delay=122` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 137.0 | 138.0 | 138.0 | 138.0 |
| `payment:delay=123` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 137.0 | 141.0 | 141.0 | 141.0 |
| `payment:delay=124` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 138.0 | 140.0 | 140.0 | 140.0 |
| `payment:delay=125` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 139.0 | 144.0 | 144.0 | 144.0 |
| `payment:delay=126` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 139.0 | 143.0 | 143.0 | 143.0 |
| `payment:delay=127` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 146.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=128` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 138.0 | 149.0 | 149.0 | 149.0 |
| `payment:delay=129` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 142.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=13` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 27.0 | 34.0 | 34.0 | 34.0 |
| `payment:delay=130` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 147.0 | 147.0 | 147.0 | 147.0 |
| `payment:delay=131` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 146.0 | 148.0 | 148.0 | 148.0 |
| `payment:delay=132` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 149.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=133` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 142.0 | 147.0 | 147.0 | 147.0 |
| `payment:delay=134` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 147.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=135` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 143.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=136` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 150.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=137` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 153.0 | 156.0 | 156.0 | 156.0 |
| `payment:delay=138` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 152.0 | 156.0 | 156.0 | 156.0 |
| `payment:delay=139` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 153.0 | 159.0 | 159.0 | 159.0 |
| `payment:delay=14` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 31.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=140` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 152.0 | 155.0 | 155.0 | 155.0 |
| `payment:delay=141` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 154.0 | 172.0 | 172.0 | 172.0 |
| `payment:delay=142` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 154.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=143` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 156.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=144` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 159.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=145` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 161.0 | 161.0 | 161.0 | 161.0 |
| `payment:delay=146` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 159.0 | 162.0 | 162.0 | 162.0 |
| `payment:delay=147` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 159.0 | 164.0 | 164.0 | 164.0 |
| `payment:delay=148` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 163.0 | 163.0 | 163.0 | 163.0 |
| `payment:delay=149` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 159.0 | 162.0 | 162.0 | 162.0 |
| `payment:delay=15` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 35.0 | 35.0 | 35.0 | 35.0 |
| `payment:delay=150` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 169.0 | 169.0 | 169.0 | 169.0 |
| `payment:delay=151` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 165.0 | 165.0 | 165.0 | 165.0 |
| `payment:delay=152` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 168.0 | 169.0 | 169.0 | 169.0 |
| `payment:delay=153` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 168.0 | 168.0 | 168.0 | 168.0 |
| `payment:delay=154` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 167.0 | 170.0 | 170.0 | 170.0 |
| `payment:delay=155` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 169.0 | 172.0 | 172.0 | 172.0 |
| `payment:delay=156` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 168.0 | 174.0 | 174.0 | 174.0 |
| `payment:delay=157` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 170.0 | 173.0 | 173.0 | 173.0 |
| `payment:delay=158` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 169.0 | 170.0 | 170.0 | 170.0 |
| `payment:delay=159` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 171.0 | 176.0 | 176.0 | 176.0 |
| `payment:delay=16` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 31.0 | 35.0 | 35.0 | 35.0 |
| `payment:delay=160` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 176.0 | 176.0 | 176.0 | 176.0 |
| `payment:delay=161` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 174.0 | 178.0 | 178.0 | 178.0 |
| `payment:delay=162` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 178.0 | 182.0 | 182.0 | 182.0 |
| `payment:delay=163` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 176.0 | 181.0 | 181.0 | 181.0 |
| `payment:delay=164` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 178.0 | 180.0 | 180.0 | 180.0 |
| `payment:delay=165` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 180.0 | 184.0 | 184.0 | 184.0 |
| `payment:delay=167` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 182.0 | 182.0 | 182.0 | 182.0 |
| `payment:delay=168` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 180.0 | 187.0 | 187.0 | 187.0 |
| `payment:delay=17` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 36.0 | 36.0 | 36.0 | 36.0 |
| `payment:delay=170` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 183.0 | 185.0 | 185.0 | 185.0 |
| `payment:delay=171` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 180.0 | 199.0 | 199.0 | 199.0 |
| `payment:delay=172` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 187.0 | 190.0 | 190.0 | 190.0 |
| `payment:delay=173` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 188.0 | 191.0 | 191.0 | 191.0 |
| `payment:delay=174` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 186.0 | 191.0 | 191.0 | 191.0 |
| `payment:delay=175` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 191.0 | 192.0 | 192.0 | 192.0 |
| `payment:delay=176` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 188.0 | 188.0 | 188.0 | 188.0 |
| `payment:delay=179` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 194.0 | 194.0 | 194.0 | 194.0 |
| `payment:delay=18` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 32.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=180` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 197.0 | 197.0 | 197.0 | 197.0 |
| `payment:delay=181` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 196.0 | 196.0 | 196.0 | 196.0 |
| `payment:delay=182` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 196.0 | 197.0 | 197.0 | 197.0 |
| `payment:delay=185` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 200.0 | 200.0 | 200.0 | 200.0 |
| `payment:delay=187` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 199.0 | 208.0 | 208.0 | 208.0 |
| `payment:delay=19` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 37.0 | 40.0 | 40.0 | 40.0 |
| `payment:delay=191` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 206.0 | 206.0 | 206.0 | 206.0 |
| `payment:delay=192` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 206.0 | 206.0 | 206.0 | 206.0 |
| `payment:delay=193` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 209.0 | 214.0 | 214.0 | 214.0 |
| `payment:delay=194` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 206.0 | 212.0 | 212.0 | 212.0 |
| `payment:delay=196` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 209.0 | 213.0 | 213.0 | 213.0 |
| `payment:delay=197` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 211.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=198` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 217.0 | 217.0 | 217.0 | 217.0 |
| `payment:delay=199` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 214.0 | 215.0 | 215.0 | 215.0 |
| `payment:delay=20` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 31.0 | 38.0 | 38.0 | 38.0 |
| `payment:delay=200` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 215.0 | 215.0 | 215.0 | 215.0 |
| `payment:delay=201` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 216.0 | 216.0 | 216.0 | 216.0 |
| `payment:delay=202` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 215.0 | 216.0 | 216.0 | 216.0 |
| `payment:delay=203` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 213.0 | 213.0 | 213.0 | 213.0 |
| `payment:delay=204` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 216.0 | 218.0 | 218.0 | 218.0 |
| `payment:delay=205` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 217.0 | 225.0 | 225.0 | 225.0 |
| `payment:delay=206` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 218.0 | 221.0 | 221.0 | 221.0 |
| `payment:delay=207` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 216.0 | 217.0 | 217.0 | 217.0 |
| `payment:delay=209` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 223.0 | 223.0 | 223.0 | 223.0 |
| `payment:delay=21` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 36.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=211` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 224.0 | 225.0 | 225.0 | 225.0 |
| `payment:delay=212` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 227.0 | 227.0 | 227.0 | 227.0 |
| `payment:delay=213` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 227.0 | 227.0 | 227.0 | 227.0 |
| `payment:delay=214` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 231.0 | 232.0 | 232.0 | 232.0 |
| `payment:delay=216` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 231.0 | 231.0 | 231.0 | 231.0 |
| `payment:delay=218` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 229.0 | 229.0 | 229.0 | 229.0 |
| `payment:delay=219` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 235.0 | 236.0 | 236.0 | 236.0 |
| `payment:delay=22` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 30.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=221` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 233.0 | 242.0 | 242.0 | 242.0 |
| `payment:delay=223` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 237.0 | 237.0 | 237.0 | 237.0 |
| `payment:delay=224` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 239.0 | 243.0 | 243.0 | 243.0 |
| `payment:delay=225` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 237.0 | 237.0 | 237.0 | 237.0 |
| `payment:delay=226` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 245.0 | 246.0 | 246.0 | 246.0 |
| `payment:delay=227` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 243.0 | 243.0 | 243.0 | 243.0 |
| `payment:delay=23` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 37.0 | 38.0 | 38.0 | 38.0 |
| `payment:delay=234` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 241.0 | 248.0 | 248.0 | 248.0 |
| `payment:delay=235` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 252.0 | 252.0 | 252.0 | 252.0 |
| `payment:delay=236` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 252.0 | 255.0 | 255.0 | 255.0 |
| `payment:delay=24` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 36.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=243` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 260.0 | 260.0 | 260.0 | 260.0 |
| `payment:delay=244` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 261.0 | 261.0 | 261.0 | 261.0 |
| `payment:delay=245` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 261.0 | 261.0 | 261.0 | 261.0 |
| `payment:delay=248` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 265.0 | 265.0 | 265.0 | 265.0 |
| `payment:delay=25` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 41.0 | 43.0 | 43.0 | 43.0 |
| `payment:delay=250` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 261.0 | 261.0 | 261.0 | 261.0 |
| `payment:delay=254` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 273.0 | 273.0 | 273.0 | 273.0 |
| `payment:delay=255` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 265.0 | 271.0 | 271.0 | 271.0 |
| `payment:delay=258` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 271.0 | 271.0 | 271.0 | 271.0 |
| `payment:delay=259` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 279.0 | 279.0 | 279.0 | 279.0 |
| `payment:delay=26` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 40.0 | 46.0 | 46.0 | 46.0 |
| `payment:delay=268` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 284.0 | 286.0 | 286.0 | 286.0 |
| `payment:delay=269` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 286.0 | 288.0 | 288.0 | 288.0 |
| `payment:delay=27` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 43.0 | 46.0 | 46.0 | 46.0 |
| `payment:delay=272` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 287.0 | 287.0 | 287.0 | 287.0 |
| `payment:delay=273` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 290.0 | 290.0 | 290.0 | 290.0 |
| `payment:delay=276` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 291.0 | 291.0 | 291.0 | 291.0 |
| `payment:delay=278` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 293.0 | 293.0 | 293.0 | 293.0 |
| `payment:delay=279` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 286.0 | 286.0 | 286.0 | 286.0 |
| `payment:delay=28` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 43.0 | 49.0 | 49.0 | 49.0 |
| `payment:delay=280` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 291.0 | 291.0 | 291.0 | 291.0 |
| `payment:delay=281` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 296.0 | 296.0 | 296.0 | 296.0 |
| `payment:delay=282` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 292.0 | 292.0 | 292.0 | 292.0 |
| `payment:delay=287` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 305.0 | 305.0 | 305.0 | 305.0 |
| `payment:delay=29` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 44.0 | 46.0 | 46.0 | 46.0 |
| `payment:delay=292` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 305.0 | 305.0 | 305.0 | 305.0 |
| `payment:delay=296` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 314.0 | 314.0 | 314.0 | 314.0 |
| `payment:delay=298` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 309.0 | 312.0 | 312.0 | 312.0 |
| `payment:delay=30` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 44.0 | 47.0 | 47.0 | 47.0 |
| `payment:delay=300` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 311.0 | 311.0 | 311.0 | 311.0 |
| `payment:delay=31` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 47.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=313` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 325.0 | 325.0 | 325.0 | 325.0 |
| `payment:delay=315` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 329.0 | 329.0 | 329.0 | 329.0 |
| `payment:delay=32` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 47.0 | 50.0 | 50.0 | 50.0 |
| `payment:delay=322` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 335.0 | 335.0 | 335.0 | 335.0 |
| `payment:delay=323` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 333.0 | 333.0 | 333.0 | 333.0 |
| `payment:delay=33` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 49.0 | 52.0 | 52.0 | 52.0 |
| `payment:delay=335` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 331.0 | 331.0 | 331.0 | 331.0 |
| `payment:delay=338` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 337.0 | 337.0 | 337.0 | 337.0 |
| `payment:delay=34` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 49.0 | 57.0 | 57.0 | 57.0 |
| `payment:delay=346` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 334.0 | 334.0 | 334.0 | 334.0 |
| `payment:delay=35` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 44.0 | 63.0 | 63.0 | 63.0 |
| `payment:delay=355` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 332.0 | 332.0 | 332.0 | 332.0 |
| `payment:delay=358` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 333.0 | 333.0 | 333.0 | 333.0 |
| `payment:delay=36` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 51.0 | 54.0 | 54.0 | 54.0 |
| `payment:delay=365` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 335.0 | 335.0 | 335.0 | 335.0 |
| `payment:delay=366` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 338.0 | 338.0 | 338.0 | 338.0 |
| `payment:delay=37` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 52.0 | 54.0 | 54.0 | 54.0 |
| `payment:delay=373` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 331.0 | 331.0 | 331.0 | 331.0 |
| `payment:delay=374` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 331.0 | 331.0 | 331.0 | 331.0 |
| `payment:delay=38` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 53.0 | 57.0 | 57.0 | 57.0 |
| `payment:delay=39` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 55.0 | 59.0 | 59.0 | 59.0 |
| `payment:delay=40` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 55.0 | 59.0 | 59.0 | 59.0 |
| `payment:delay=400` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 332.0 | 332.0 | 332.0 | 332.0 |
| `payment:delay=407` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 329.0 | 329.0 | 329.0 | 329.0 |
| `payment:delay=41` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 54.0 | 60.0 | 60.0 | 60.0 |
| `payment:delay=42` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 57.0 | 63.0 | 63.0 | 63.0 |
| `payment:delay=420` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 330.0 | 330.0 | 330.0 | 330.0 |
| `payment:delay=427` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 333.0 | 333.0 | 333.0 | 333.0 |
| `payment:delay=43` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 59.0 | 70.0 | 70.0 | 70.0 |
| `payment:delay=44` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 60.0 | 63.0 | 63.0 | 63.0 |
| `payment:delay=45` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 60.0 | 64.0 | 64.0 | 64.0 |
| `payment:delay=46` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 60.0 | 64.0 | 64.0 | 64.0 |
| `payment:delay=469` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 335.0 | 335.0 | 335.0 | 335.0 |
| `payment:delay=47` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 62.0 | 66.0 | 66.0 | 66.0 |
| `payment:delay=48` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 64.0 | 70.0 | 70.0 | 70.0 |
| `payment:delay=483` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 334.0 | 334.0 | 334.0 | 334.0 |
| `payment:delay=49` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 65.0 | 72.0 | 72.0 | 72.0 |
| `payment:delay=50` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 65.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=51` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 67.0 | 70.0 | 70.0 | 70.0 |
| `payment:delay=52` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 67.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=523` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 337.0 | 337.0 | 337.0 | 337.0 |
| `payment:delay=53` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 68.0 | 72.0 | 72.0 | 72.0 |
| `payment:delay=54` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 67.0 | 73.0 | 73.0 | 73.0 |
| `payment:delay=55` | 14 | `completed | COMPLETED | RESERVED | APPROVED` | 14 | 71.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=56` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 73.0 | 76.0 | 76.0 | 76.0 |
| `payment:delay=57` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 70.0 | 73.0 | 73.0 | 73.0 |
| `payment:delay=58` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 74.0 | 81.0 | 81.0 | 81.0 |
| `payment:delay=59` | 19 | `completed | COMPLETED | RESERVED | APPROVED` | 19 | 73.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=60` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 74.0 | 77.0 | 77.0 | 77.0 |
| `payment:delay=61` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 75.0 | 82.0 | 82.0 | 82.0 |
| `payment:delay=62` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 76.0 | 79.0 | 79.0 | 79.0 |
| `payment:delay=622` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 335.0 | 335.0 | 335.0 | 335.0 |
| `payment:delay=63` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 79.0 | 86.0 | 86.0 | 86.0 |
| `payment:delay=64` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 80.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=65` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 78.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=66` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 81.0 | 83.0 | 83.0 | 83.0 |
| `payment:delay=663` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 332.0 | 332.0 | 332.0 | 332.0 |
| `payment:delay=67` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 83.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=68` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 85.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=69` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 84.0 | 96.0 | 96.0 | 96.0 |
| `payment:delay=70` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 86.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=71` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 86.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=72` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 85.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=73` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 88.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=74` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 89.0 | 94.0 | 94.0 | 94.0 |
| `payment:delay=75` | 20 | `completed | COMPLETED | RESERVED | APPROVED` | 20 | 90.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=76` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 92.0 | 95.0 | 95.0 | 95.0 |
| `payment:delay=77` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 94.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=78` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 92.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=79` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 94.0 | 96.0 | 96.0 | 96.0 |
| `payment:delay=80` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 94.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=81` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 94.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=82` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 98.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=83` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 97.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=84` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 99.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=85` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 100.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=86` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 101.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=87` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 101.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=88` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 102.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=89` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 101.0 | 112.0 | 112.0 | 112.0 |
| `payment:delay=9` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 27.0 | 27.0 | 27.0 | 27.0 |
| `payment:delay=90` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 108.0 | 109.0 | 109.0 | 109.0 |
| `payment:delay=91` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 103.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=92` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 106.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=93` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 109.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=94` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 108.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=95` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 111.0 | 114.0 | 114.0 | 114.0 |
| `payment:delay=96` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 111.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=97` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 112.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=98` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 114.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=99` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 113.0 | 119.0 | 119.0 | 119.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 1200 | 94.0 | 241.0 | 332.0 | 338.0 |
| completed | 1180 | 93.0 | 218.0 | 292.0 | 329.0 |
| failed | 0 | - | - | - | - |
| unknown | 20 | 333.0 | 337.0 | 338.0 | 338.0 |
