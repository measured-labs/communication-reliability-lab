# 분석 보고 — false-timeout-20261126T060433-r3

- 정답 1200줄, requestId 1200개 (계획 1200)
- 응답으로 가름: completed 1200, failed 0, unknown 0
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 0
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/false-timeout-20261126T060433-r3/expect.json`
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
| `payment:delay=100` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 111.0 | 130.0 | 130.0 | 130.0 |
| `payment:delay=101` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 115.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=102` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 114.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=103` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 116.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=104` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 117.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=105` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 116.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=106` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 119.0 | 121.0 | 121.0 | 121.0 |
| `payment:delay=107` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 122.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=108` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 121.0 | 123.0 | 123.0 | 123.0 |
| `payment:delay=109` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 122.0 | 123.0 | 123.0 | 123.0 |
| `payment:delay=110` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 122.0 | 124.0 | 124.0 | 124.0 |
| `payment:delay=111` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 124.0 | 127.0 | 127.0 | 127.0 |
| `payment:delay=112` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 126.0 | 127.0 | 127.0 | 127.0 |
| `payment:delay=113` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 126.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=114` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 129.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=115` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 126.0 | 130.0 | 130.0 | 130.0 |
| `payment:delay=116` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 131.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=117` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 130.0 | 134.0 | 134.0 | 134.0 |
| `payment:delay=118` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 132.0 | 132.0 | 132.0 | 132.0 |
| `payment:delay=119` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 132.0 | 132.0 | 132.0 | 132.0 |
| `payment:delay=120` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 133.0 | 135.0 | 135.0 | 135.0 |
| `payment:delay=121` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 135.0 | 136.0 | 136.0 | 136.0 |
| `payment:delay=122` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 136.0 | 136.0 | 136.0 | 136.0 |
| `payment:delay=123` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 136.0 | 138.0 | 138.0 | 138.0 |
| `payment:delay=124` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 137.0 | 138.0 | 138.0 | 138.0 |
| `payment:delay=125` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 139.0 | 140.0 | 140.0 | 140.0 |
| `payment:delay=126` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 139.0 | 141.0 | 141.0 | 141.0 |
| `payment:delay=127` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 143.0 | 143.0 | 143.0 | 143.0 |
| `payment:delay=128` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 142.0 | 143.0 | 143.0 | 143.0 |
| `payment:delay=129` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 143.0 | 144.0 | 144.0 | 144.0 |
| `payment:delay=13` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 26.0 | 26.0 | 26.0 | 26.0 |
| `payment:delay=130` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 145.0 | 145.0 | 145.0 | 145.0 |
| `payment:delay=131` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 145.0 | 149.0 | 149.0 | 149.0 |
| `payment:delay=132` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 145.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=133` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 141.0 | 147.0 | 147.0 | 147.0 |
| `payment:delay=134` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 143.0 | 147.0 | 147.0 | 147.0 |
| `payment:delay=135` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 147.0 | 149.0 | 149.0 | 149.0 |
| `payment:delay=136` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 147.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=137` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 151.0 | 152.0 | 152.0 | 152.0 |
| `payment:delay=138` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 152.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=139` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 153.0 | 154.0 | 154.0 | 154.0 |
| `payment:delay=14` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 29.0 | 30.0 | 30.0 | 30.0 |
| `payment:delay=140` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 154.0 | 156.0 | 156.0 | 156.0 |
| `payment:delay=141` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 155.0 | 161.0 | 161.0 | 161.0 |
| `payment:delay=142` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 155.0 | 157.0 | 157.0 | 157.0 |
| `payment:delay=143` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 153.0 | 157.0 | 157.0 | 157.0 |
| `payment:delay=144` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 156.0 | 157.0 | 157.0 | 157.0 |
| `payment:delay=145` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 161.0 | 161.0 | 161.0 | 161.0 |
| `payment:delay=146` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 158.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=147` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 157.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=148` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 159.0 | 161.0 | 161.0 | 161.0 |
| `payment:delay=149` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 163.0 | 163.0 | 163.0 | 163.0 |
| `payment:delay=15` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 31.0 | 31.0 | 31.0 | 31.0 |
| `payment:delay=150` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 164.0 | 164.0 | 164.0 | 164.0 |
| `payment:delay=151` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 161.0 | 162.0 | 162.0 | 162.0 |
| `payment:delay=152` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 164.0 | 167.0 | 167.0 | 167.0 |
| `payment:delay=153` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 165.0 | 165.0 | 165.0 | 165.0 |
| `payment:delay=154` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 167.0 | 169.0 | 169.0 | 169.0 |
| `payment:delay=155` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 166.0 | 168.0 | 168.0 | 168.0 |
| `payment:delay=156` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 166.0 | 170.0 | 170.0 | 170.0 |
| `payment:delay=157` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 168.0 | 172.0 | 172.0 | 172.0 |
| `payment:delay=158` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 168.0 | 169.0 | 169.0 | 169.0 |
| `payment:delay=159` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 173.0 | 178.0 | 178.0 | 178.0 |
| `payment:delay=16` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 25.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=160` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 174.0 | 174.0 | 174.0 | 174.0 |
| `payment:delay=161` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 172.0 | 174.0 | 174.0 | 174.0 |
| `payment:delay=162` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 173.0 | 175.0 | 175.0 | 175.0 |
| `payment:delay=163` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 176.0 | 177.0 | 177.0 | 177.0 |
| `payment:delay=164` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 179.0 | 180.0 | 180.0 | 180.0 |
| `payment:delay=165` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 174.0 | 181.0 | 181.0 | 181.0 |
| `payment:delay=167` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 181.0 | 181.0 | 181.0 | 181.0 |
| `payment:delay=168` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 182.0 | 183.0 | 183.0 | 183.0 |
| `payment:delay=17` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 32.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=170` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 184.0 | 185.0 | 185.0 | 185.0 |
| `payment:delay=171` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 185.0 | 186.0 | 186.0 | 186.0 |
| `payment:delay=172` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 186.0 | 187.0 | 187.0 | 187.0 |
| `payment:delay=173` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 186.0 | 187.0 | 187.0 | 187.0 |
| `payment:delay=174` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 187.0 | 188.0 | 188.0 | 188.0 |
| `payment:delay=175` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 187.0 | 190.0 | 190.0 | 190.0 |
| `payment:delay=176` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 185.0 | 185.0 | 185.0 | 185.0 |
| `payment:delay=179` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 189.0 | 191.0 | 191.0 | 191.0 |
| `payment:delay=18` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 32.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=180` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 191.0 | 191.0 | 191.0 | 191.0 |
| `payment:delay=181` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 192.0 | 192.0 | 192.0 | 192.0 |
| `payment:delay=182` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 196.0 | 196.0 | 196.0 | 196.0 |
| `payment:delay=185` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 200.0 | 200.0 | 200.0 | 200.0 |
| `payment:delay=187` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 200.0 | 201.0 | 201.0 | 201.0 |
| `payment:delay=19` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 32.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=191` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 197.0 | 197.0 | 197.0 | 197.0 |
| `payment:delay=192` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 199.0 | 199.0 | 199.0 | 199.0 |
| `payment:delay=193` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 204.0 | 208.0 | 208.0 | 208.0 |
| `payment:delay=194` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 206.0 | 208.0 | 208.0 | 208.0 |
| `payment:delay=196` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 202.0 | 210.0 | 210.0 | 210.0 |
| `payment:delay=197` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 213.0 | 213.0 | 213.0 | 213.0 |
| `payment:delay=198` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 213.0 | 213.0 | 213.0 | 213.0 |
| `payment:delay=199` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 212.0 | 214.0 | 214.0 | 214.0 |
| `payment:delay=20` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 34.0 | 35.0 | 35.0 | 35.0 |
| `payment:delay=200` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 213.0 | 213.0 | 213.0 | 213.0 |
| `payment:delay=201` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 214.0 | 214.0 | 214.0 | 214.0 |
| `payment:delay=202` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 213.0 | 217.0 | 217.0 | 217.0 |
| `payment:delay=203` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 216.0 | 216.0 | 216.0 | 216.0 |
| `payment:delay=204` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 219.0 | 222.0 | 222.0 | 222.0 |
| `payment:delay=205` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 215.0 | 219.0 | 219.0 | 219.0 |
| `payment:delay=206` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 219.0 | 220.0 | 220.0 | 220.0 |
| `payment:delay=207` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 213.0 | 216.0 | 216.0 | 216.0 |
| `payment:delay=209` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 219.0 | 219.0 | 219.0 | 219.0 |
| `payment:delay=21` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 34.0 | 36.0 | 36.0 | 36.0 |
| `payment:delay=211` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 220.0 | 226.0 | 226.0 | 226.0 |
| `payment:delay=212` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 227.0 | 227.0 | 227.0 | 227.0 |
| `payment:delay=213` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 223.0 | 223.0 | 223.0 | 223.0 |
| `payment:delay=214` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 223.0 | 228.0 | 228.0 | 228.0 |
| `payment:delay=216` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 231.0 | 231.0 | 231.0 | 231.0 |
| `payment:delay=218` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 227.0 | 227.0 | 227.0 | 227.0 |
| `payment:delay=219` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 229.0 | 232.0 | 232.0 | 232.0 |
| `payment:delay=22` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 35.0 | 37.0 | 37.0 | 37.0 |
| `payment:delay=221` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 232.0 | 237.0 | 237.0 | 237.0 |
| `payment:delay=223` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 236.0 | 236.0 | 236.0 | 236.0 |
| `payment:delay=224` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 238.0 | 239.0 | 239.0 | 239.0 |
| `payment:delay=225` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 238.0 | 238.0 | 238.0 | 238.0 |
| `payment:delay=226` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 240.0 | 241.0 | 241.0 | 241.0 |
| `payment:delay=227` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 240.0 | 240.0 | 240.0 | 240.0 |
| `payment:delay=23` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 34.0 | 37.0 | 37.0 | 37.0 |
| `payment:delay=234` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 247.0 | 248.0 | 248.0 | 248.0 |
| `payment:delay=235` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 248.0 | 248.0 | 248.0 | 248.0 |
| `payment:delay=236` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 249.0 | 250.0 | 250.0 | 250.0 |
| `payment:delay=24` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 34.0 | 38.0 | 38.0 | 38.0 |
| `payment:delay=243` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 254.0 | 254.0 | 254.0 | 254.0 |
| `payment:delay=244` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 255.0 | 255.0 | 255.0 | 255.0 |
| `payment:delay=245` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 257.0 | 257.0 | 257.0 | 257.0 |
| `payment:delay=248` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 259.0 | 259.0 | 259.0 | 259.0 |
| `payment:delay=25` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 38.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=250` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 265.0 | 265.0 | 265.0 | 265.0 |
| `payment:delay=254` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 270.0 | 270.0 | 270.0 | 270.0 |
| `payment:delay=255` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 265.0 | 268.0 | 268.0 | 268.0 |
| `payment:delay=258` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 273.0 | 273.0 | 273.0 | 273.0 |
| `payment:delay=259` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 271.0 | 271.0 | 271.0 | 271.0 |
| `payment:delay=26` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 40.0 | 49.0 | 49.0 | 49.0 |
| `payment:delay=268` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 278.0 | 283.0 | 283.0 | 283.0 |
| `payment:delay=269` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 283.0 | 284.0 | 284.0 | 284.0 |
| `payment:delay=27` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 40.0 | 41.0 | 41.0 | 41.0 |
| `payment:delay=272` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 286.0 | 286.0 | 286.0 | 286.0 |
| `payment:delay=273` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 285.0 | 285.0 | 285.0 | 285.0 |
| `payment:delay=276` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 289.0 | 289.0 | 289.0 | 289.0 |
| `payment:delay=278` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 289.0 | 289.0 | 289.0 | 289.0 |
| `payment:delay=279` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 289.0 | 289.0 | 289.0 | 289.0 |
| `payment:delay=28` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 40.0 | 42.0 | 42.0 | 42.0 |
| `payment:delay=280` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 293.0 | 293.0 | 293.0 | 293.0 |
| `payment:delay=281` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 294.0 | 294.0 | 294.0 | 294.0 |
| `payment:delay=282` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 291.0 | 291.0 | 291.0 | 291.0 |
| `payment:delay=287` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 300.0 | 300.0 | 300.0 | 300.0 |
| `payment:delay=29` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 43.0 | 46.0 | 46.0 | 46.0 |
| `payment:delay=292` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 305.0 | 305.0 | 305.0 | 305.0 |
| `payment:delay=296` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 307.0 | 307.0 | 307.0 | 307.0 |
| `payment:delay=298` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 306.0 | 312.0 | 312.0 | 312.0 |
| `payment:delay=30` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 43.0 | 44.0 | 44.0 | 44.0 |
| `payment:delay=300` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 313.0 | 313.0 | 313.0 | 313.0 |
| `payment:delay=31` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 44.0 | 47.0 | 47.0 | 47.0 |
| `payment:delay=313` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 326.0 | 326.0 | 326.0 | 326.0 |
| `payment:delay=315` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 328.0 | 330.0 | 330.0 | 330.0 |
| `payment:delay=32` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 45.0 | 46.0 | 46.0 | 46.0 |
| `payment:delay=322` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 330.0 | 330.0 | 330.0 | 330.0 |
| `payment:delay=323` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 334.0 | 334.0 | 334.0 | 334.0 |
| `payment:delay=33` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 46.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=335` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 346.0 | 346.0 | 346.0 | 346.0 |
| `payment:delay=338` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 350.0 | 350.0 | 350.0 | 350.0 |
| `payment:delay=34` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 48.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=346` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 360.0 | 360.0 | 360.0 | 360.0 |
| `payment:delay=35` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 48.0 | 49.0 | 49.0 | 49.0 |
| `payment:delay=355` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 365.0 | 365.0 | 365.0 | 365.0 |
| `payment:delay=358` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 377.0 | 377.0 | 377.0 | 377.0 |
| `payment:delay=36` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 50.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=365` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 381.0 | 381.0 | 381.0 | 381.0 |
| `payment:delay=366` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 381.0 | 381.0 | 381.0 | 381.0 |
| `payment:delay=37` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 51.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=373` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 383.0 | 383.0 | 383.0 | 383.0 |
| `payment:delay=374` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 387.0 | 387.0 | 387.0 | 387.0 |
| `payment:delay=38` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 52.0 | 54.0 | 54.0 | 54.0 |
| `payment:delay=39` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 53.0 | 54.0 | 54.0 | 54.0 |
| `payment:delay=40` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 52.0 | 56.0 | 56.0 | 56.0 |
| `payment:delay=400` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 413.0 | 413.0 | 413.0 | 413.0 |
| `payment:delay=407` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 416.0 | 416.0 | 416.0 | 416.0 |
| `payment:delay=41` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 55.0 | 56.0 | 56.0 | 56.0 |
| `payment:delay=42` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 56.0 | 57.0 | 57.0 | 57.0 |
| `payment:delay=420` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 438.0 | 438.0 | 438.0 | 438.0 |
| `payment:delay=427` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 437.0 | 437.0 | 437.0 | 437.0 |
| `payment:delay=43` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 56.0 | 58.0 | 58.0 | 58.0 |
| `payment:delay=44` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 58.0 | 59.0 | 59.0 | 59.0 |
| `payment:delay=45` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 59.0 | 60.0 | 60.0 | 60.0 |
| `payment:delay=46` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 60.0 | 63.0 | 63.0 | 63.0 |
| `payment:delay=469` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 483.0 | 483.0 | 483.0 | 483.0 |
| `payment:delay=47` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 60.0 | 62.0 | 62.0 | 62.0 |
| `payment:delay=48` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 61.0 | 64.0 | 64.0 | 64.0 |
| `payment:delay=483` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 498.0 | 498.0 | 498.0 | 498.0 |
| `payment:delay=49` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 63.0 | 64.0 | 64.0 | 64.0 |
| `payment:delay=50` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 62.0 | 66.0 | 66.0 | 66.0 |
| `payment:delay=51` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 65.0 | 66.0 | 66.0 | 66.0 |
| `payment:delay=52` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 66.0 | 67.0 | 67.0 | 67.0 |
| `payment:delay=523` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 534.0 | 534.0 | 534.0 | 534.0 |
| `payment:delay=53` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 66.0 | 67.0 | 67.0 | 67.0 |
| `payment:delay=54` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 67.0 | 72.0 | 72.0 | 72.0 |
| `payment:delay=55` | 14 | `completed | COMPLETED | RESERVED | APPROVED` | 14 | 66.0 | 70.0 | 70.0 | 70.0 |
| `payment:delay=56` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 70.0 | 71.0 | 71.0 | 71.0 |
| `payment:delay=57` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 70.0 | 73.0 | 73.0 | 73.0 |
| `payment:delay=58` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 72.0 | 76.0 | 76.0 | 76.0 |
| `payment:delay=59` | 19 | `completed | COMPLETED | RESERVED | APPROVED` | 19 | 72.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=60` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 73.0 | 75.0 | 75.0 | 75.0 |
| `payment:delay=61` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 75.0 | 77.0 | 77.0 | 77.0 |
| `payment:delay=62` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 75.0 | 77.0 | 77.0 | 77.0 |
| `payment:delay=622` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 638.0 | 638.0 | 638.0 | 638.0 |
| `payment:delay=63` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 76.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=64` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 77.0 | 79.0 | 79.0 | 79.0 |
| `payment:delay=65` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 78.0 | 81.0 | 81.0 | 81.0 |
| `payment:delay=66` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 79.0 | 82.0 | 82.0 | 82.0 |
| `payment:delay=663` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 678.0 | 678.0 | 678.0 | 678.0 |
| `payment:delay=67` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 81.0 | 83.0 | 83.0 | 83.0 |
| `payment:delay=68` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 82.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=69` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 81.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=70` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 83.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=71` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 83.0 | 86.0 | 86.0 | 86.0 |
| `payment:delay=72` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 84.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=73` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 86.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=74` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 86.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=75` | 20 | `completed | COMPLETED | RESERVED | APPROVED` | 20 | 87.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=76` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 89.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=77` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 90.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=78` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 91.0 | 94.0 | 94.0 | 94.0 |
| `payment:delay=79` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 90.0 | 94.0 | 94.0 | 94.0 |
| `payment:delay=80` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 92.0 | 94.0 | 94.0 | 94.0 |
| `payment:delay=81` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 94.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=82` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 95.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=83` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 98.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=84` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 97.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=85` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 98.0 | 99.0 | 99.0 | 99.0 |
| `payment:delay=86` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 99.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=87` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 100.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=88` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 101.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=89` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 100.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=9` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 25.0 | 25.0 | 25.0 | 25.0 |
| `payment:delay=90` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 104.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=91` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 102.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=92` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 102.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=93` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 106.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=94` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 107.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=95` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 108.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=96` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 110.0 | 115.0 | 115.0 | 115.0 |
| `payment:delay=97` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 111.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=98` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 112.0 | 114.0 | 114.0 | 114.0 |
| `payment:delay=99` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 112.0 | 113.0 | 113.0 | 113.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 1200 | 91.0 | 238.0 | 381.0 | 678.0 |
| completed | 1200 | 91.0 | 238.0 | 381.0 | 678.0 |
| failed | 0 | - | - | - | - |
| unknown | 0 | - | - | - | - |
