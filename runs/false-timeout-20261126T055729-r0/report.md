# 분석 보고 — false-timeout-20261126T055729-r0

- 정답 1200줄, requestId 1200개 (계획 1200)
- 응답으로 가름: completed 1200, failed 0, unknown 0
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 0
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/false-timeout-20261126T055729-r0/expect.json`
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
| `payment:delay=100` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 113.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=101` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 115.0 | 121.0 | 121.0 | 121.0 |
| `payment:delay=102` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 115.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=103` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 118.0 | 132.0 | 132.0 | 132.0 |
| `payment:delay=104` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 122.0 | 123.0 | 123.0 | 123.0 |
| `payment:delay=105` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 116.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=106` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 121.0 | 125.0 | 125.0 | 125.0 |
| `payment:delay=107` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 121.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=108` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 123.0 | 139.0 | 139.0 | 139.0 |
| `payment:delay=109` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 123.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=110` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 122.0 | 137.0 | 137.0 | 137.0 |
| `payment:delay=111` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 124.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=112` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 127.0 | 130.0 | 130.0 | 130.0 |
| `payment:delay=113` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 126.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=114` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 132.0 | 134.0 | 134.0 | 134.0 |
| `payment:delay=115` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 129.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=116` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 131.0 | 149.0 | 149.0 | 149.0 |
| `payment:delay=117` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 130.0 | 137.0 | 137.0 | 137.0 |
| `payment:delay=118` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 131.0 | 145.0 | 145.0 | 145.0 |
| `payment:delay=119` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 133.0 | 133.0 | 133.0 | 133.0 |
| `payment:delay=120` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 135.0 | 137.0 | 137.0 | 137.0 |
| `payment:delay=121` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 131.0 | 138.0 | 138.0 | 138.0 |
| `payment:delay=122` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 133.0 | 137.0 | 137.0 | 137.0 |
| `payment:delay=123` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 139.0 | 141.0 | 141.0 | 141.0 |
| `payment:delay=124` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 138.0 | 144.0 | 144.0 | 144.0 |
| `payment:delay=125` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 140.0 | 143.0 | 143.0 | 143.0 |
| `payment:delay=126` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 140.0 | 144.0 | 144.0 | 144.0 |
| `payment:delay=127` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 144.0 | 144.0 | 144.0 | 144.0 |
| `payment:delay=128` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 141.0 | 145.0 | 145.0 | 145.0 |
| `payment:delay=129` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 143.0 | 145.0 | 145.0 | 145.0 |
| `payment:delay=13` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 28.0 | 31.0 | 31.0 | 31.0 |
| `payment:delay=130` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 146.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=131` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 146.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=132` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 147.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=133` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 146.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=134` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 149.0 | 149.0 | 149.0 | 149.0 |
| `payment:delay=135` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 148.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=136` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 147.0 | 156.0 | 156.0 | 156.0 |
| `payment:delay=137` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 153.0 | 155.0 | 155.0 | 155.0 |
| `payment:delay=138` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 153.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=139` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 156.0 | 156.0 | 156.0 | 156.0 |
| `payment:delay=14` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 28.0 | 30.0 | 30.0 | 30.0 |
| `payment:delay=140` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 154.0 | 161.0 | 161.0 | 161.0 |
| `payment:delay=141` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 156.0 | 164.0 | 164.0 | 164.0 |
| `payment:delay=142` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 152.0 | 159.0 | 159.0 | 159.0 |
| `payment:delay=143` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 157.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=144` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 160.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=145` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 159.0 | 159.0 | 159.0 | 159.0 |
| `payment:delay=146` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 158.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=147` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 161.0 | 161.0 | 161.0 | 161.0 |
| `payment:delay=148` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 164.0 | 168.0 | 168.0 | 168.0 |
| `payment:delay=149` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 159.0 | 164.0 | 164.0 | 164.0 |
| `payment:delay=15` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 34.0 | 34.0 | 34.0 | 34.0 |
| `payment:delay=150` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 168.0 | 168.0 | 168.0 | 168.0 |
| `payment:delay=151` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 160.0 | 165.0 | 165.0 | 165.0 |
| `payment:delay=152` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 167.0 | 171.0 | 171.0 | 171.0 |
| `payment:delay=153` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 168.0 | 168.0 | 168.0 | 168.0 |
| `payment:delay=154` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 168.0 | 170.0 | 170.0 | 170.0 |
| `payment:delay=155` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 170.0 | 172.0 | 172.0 | 172.0 |
| `payment:delay=156` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 167.0 | 171.0 | 171.0 | 171.0 |
| `payment:delay=157` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 171.0 | 175.0 | 175.0 | 175.0 |
| `payment:delay=158` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 169.0 | 169.0 | 169.0 | 169.0 |
| `payment:delay=159` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 171.0 | 175.0 | 175.0 | 175.0 |
| `payment:delay=16` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 25.0 | 25.0 | 25.0 | 25.0 |
| `payment:delay=160` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 171.0 | 171.0 | 171.0 | 171.0 |
| `payment:delay=161` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 173.0 | 177.0 | 177.0 | 177.0 |
| `payment:delay=162` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 177.0 | 181.0 | 181.0 | 181.0 |
| `payment:delay=163` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 178.0 | 179.0 | 179.0 | 179.0 |
| `payment:delay=164` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 180.0 | 182.0 | 182.0 | 182.0 |
| `payment:delay=165` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 176.0 | 183.0 | 183.0 | 183.0 |
| `payment:delay=167` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 178.0 | 178.0 | 178.0 | 178.0 |
| `payment:delay=168` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 182.0 | 184.0 | 184.0 | 184.0 |
| `payment:delay=17` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 34.0 | 34.0 | 34.0 | 34.0 |
| `payment:delay=170` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 185.0 | 188.0 | 188.0 | 188.0 |
| `payment:delay=171` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 181.0 | 189.0 | 189.0 | 189.0 |
| `payment:delay=172` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 185.0 | 189.0 | 189.0 | 189.0 |
| `payment:delay=173` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 188.0 | 191.0 | 191.0 | 191.0 |
| `payment:delay=174` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 187.0 | 189.0 | 189.0 | 189.0 |
| `payment:delay=175` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 190.0 | 192.0 | 192.0 | 192.0 |
| `payment:delay=176` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 189.0 | 189.0 | 189.0 | 189.0 |
| `payment:delay=179` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 192.0 | 193.0 | 193.0 | 193.0 |
| `payment:delay=18` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 32.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=180` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 199.0 | 199.0 | 199.0 | 199.0 |
| `payment:delay=181` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 194.0 | 194.0 | 194.0 | 194.0 |
| `payment:delay=182` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 196.0 | 199.0 | 199.0 | 199.0 |
| `payment:delay=185` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 199.0 | 199.0 | 199.0 | 199.0 |
| `payment:delay=187` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 200.0 | 204.0 | 204.0 | 204.0 |
| `payment:delay=19` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 35.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=191` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 206.0 | 206.0 | 206.0 | 206.0 |
| `payment:delay=192` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 205.0 | 205.0 | 205.0 | 205.0 |
| `payment:delay=193` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 207.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=194` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 207.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=196` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 210.0 | 212.0 | 212.0 | 212.0 |
| `payment:delay=197` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 211.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=198` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 217.0 | 217.0 | 217.0 | 217.0 |
| `payment:delay=199` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 210.0 | 213.0 | 213.0 | 213.0 |
| `payment:delay=20` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 32.0 | 38.0 | 38.0 | 38.0 |
| `payment:delay=200` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 215.0 | 215.0 | 215.0 | 215.0 |
| `payment:delay=201` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 212.0 | 212.0 | 212.0 | 212.0 |
| `payment:delay=202` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 217.0 | 229.0 | 229.0 | 229.0 |
| `payment:delay=203` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 215.0 | 215.0 | 215.0 | 215.0 |
| `payment:delay=204` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 217.0 | 220.0 | 220.0 | 220.0 |
| `payment:delay=205` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 213.0 | 220.0 | 220.0 | 220.0 |
| `payment:delay=206` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 221.0 | 222.0 | 222.0 | 222.0 |
| `payment:delay=207` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 217.0 | 222.0 | 222.0 | 222.0 |
| `payment:delay=209` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 227.0 | 227.0 | 227.0 | 227.0 |
| `payment:delay=21` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 36.0 | 40.0 | 40.0 | 40.0 |
| `payment:delay=211` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 224.0 | 225.0 | 225.0 | 225.0 |
| `payment:delay=212` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 243.0 | 243.0 | 243.0 | 243.0 |
| `payment:delay=213` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 233.0 | 233.0 | 233.0 | 233.0 |
| `payment:delay=214` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 226.0 | 234.0 | 234.0 | 234.0 |
| `payment:delay=216` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 229.0 | 229.0 | 229.0 | 229.0 |
| `payment:delay=218` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 232.0 | 232.0 | 232.0 | 232.0 |
| `payment:delay=219` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 229.0 | 234.0 | 234.0 | 234.0 |
| `payment:delay=22` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 36.0 | 38.0 | 38.0 | 38.0 |
| `payment:delay=221` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 233.0 | 241.0 | 241.0 | 241.0 |
| `payment:delay=223` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 238.0 | 238.0 | 238.0 | 238.0 |
| `payment:delay=224` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 240.0 | 242.0 | 242.0 | 242.0 |
| `payment:delay=225` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 240.0 | 240.0 | 240.0 | 240.0 |
| `payment:delay=226` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 240.0 | 244.0 | 244.0 | 244.0 |
| `payment:delay=227` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 237.0 | 237.0 | 237.0 | 237.0 |
| `payment:delay=23` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 35.0 | 42.0 | 42.0 | 42.0 |
| `payment:delay=234` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 248.0 | 251.0 | 251.0 | 251.0 |
| `payment:delay=235` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 250.0 | 250.0 | 250.0 | 250.0 |
| `payment:delay=236` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 251.0 | 255.0 | 255.0 | 255.0 |
| `payment:delay=24` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 37.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=243` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 259.0 | 259.0 | 259.0 | 259.0 |
| `payment:delay=244` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 259.0 | 259.0 | 259.0 | 259.0 |
| `payment:delay=245` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 258.0 | 258.0 | 258.0 | 258.0 |
| `payment:delay=248` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 265.0 | 265.0 | 265.0 | 265.0 |
| `payment:delay=25` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 39.0 | 42.0 | 42.0 | 42.0 |
| `payment:delay=250` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 265.0 | 265.0 | 265.0 | 265.0 |
| `payment:delay=254` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 272.0 | 272.0 | 272.0 | 272.0 |
| `payment:delay=255` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 266.0 | 268.0 | 268.0 | 268.0 |
| `payment:delay=258` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 268.0 | 268.0 | 268.0 | 268.0 |
| `payment:delay=259` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 273.0 | 273.0 | 273.0 | 273.0 |
| `payment:delay=26` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 40.0 | 44.0 | 44.0 | 44.0 |
| `payment:delay=268` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 283.0 | 285.0 | 285.0 | 285.0 |
| `payment:delay=269` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 285.0 | 287.0 | 287.0 | 287.0 |
| `payment:delay=27` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 39.0 | 45.0 | 45.0 | 45.0 |
| `payment:delay=272` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 286.0 | 286.0 | 286.0 | 286.0 |
| `payment:delay=273` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 290.0 | 290.0 | 290.0 | 290.0 |
| `payment:delay=276` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 291.0 | 291.0 | 291.0 | 291.0 |
| `payment:delay=278` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 293.0 | 293.0 | 293.0 | 293.0 |
| `payment:delay=279` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 289.0 | 289.0 | 289.0 | 289.0 |
| `payment:delay=28` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 43.0 | 50.0 | 50.0 | 50.0 |
| `payment:delay=280` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 292.0 | 292.0 | 292.0 | 292.0 |
| `payment:delay=281` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 296.0 | 296.0 | 296.0 | 296.0 |
| `payment:delay=282` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 298.0 | 298.0 | 298.0 | 298.0 |
| `payment:delay=287` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 302.0 | 302.0 | 302.0 | 302.0 |
| `payment:delay=29` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 39.0 | 42.0 | 42.0 | 42.0 |
| `payment:delay=292` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 301.0 | 301.0 | 301.0 | 301.0 |
| `payment:delay=296` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 315.0 | 315.0 | 315.0 | 315.0 |
| `payment:delay=298` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 310.0 | 311.0 | 311.0 | 311.0 |
| `payment:delay=30` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 45.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=300` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 313.0 | 313.0 | 313.0 | 313.0 |
| `payment:delay=31` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 46.0 | 50.0 | 50.0 | 50.0 |
| `payment:delay=313` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 328.0 | 328.0 | 328.0 | 328.0 |
| `payment:delay=315` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 330.0 | 331.0 | 331.0 | 331.0 |
| `payment:delay=32` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 46.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=322` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 335.0 | 335.0 | 335.0 | 335.0 |
| `payment:delay=323` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 336.0 | 336.0 | 336.0 | 336.0 |
| `payment:delay=33` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 47.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=335` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 350.0 | 350.0 | 350.0 | 350.0 |
| `payment:delay=338` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 355.0 | 355.0 | 355.0 | 355.0 |
| `payment:delay=34` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 48.0 | 52.0 | 52.0 | 52.0 |
| `payment:delay=346` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 360.0 | 360.0 | 360.0 | 360.0 |
| `payment:delay=35` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 50.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=355` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 370.0 | 370.0 | 370.0 | 370.0 |
| `payment:delay=358` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 372.0 | 372.0 | 372.0 | 372.0 |
| `payment:delay=36` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 50.0 | 54.0 | 54.0 | 54.0 |
| `payment:delay=365` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 379.0 | 379.0 | 379.0 | 379.0 |
| `payment:delay=366` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 386.0 | 386.0 | 386.0 | 386.0 |
| `payment:delay=37` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 51.0 | 56.0 | 56.0 | 56.0 |
| `payment:delay=373` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 381.0 | 381.0 | 381.0 | 381.0 |
| `payment:delay=374` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 390.0 | 390.0 | 390.0 | 390.0 |
| `payment:delay=38` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 54.0 | 56.0 | 56.0 | 56.0 |
| `payment:delay=39` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 54.0 | 58.0 | 58.0 | 58.0 |
| `payment:delay=40` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 55.0 | 58.0 | 58.0 | 58.0 |
| `payment:delay=400` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 415.0 | 415.0 | 415.0 | 415.0 |
| `payment:delay=407` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 414.0 | 414.0 | 414.0 | 414.0 |
| `payment:delay=41` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 57.0 | 60.0 | 60.0 | 60.0 |
| `payment:delay=42` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 53.0 | 60.0 | 60.0 | 60.0 |
| `payment:delay=420` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 435.0 | 435.0 | 435.0 | 435.0 |
| `payment:delay=427` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 442.0 | 442.0 | 442.0 | 442.0 |
| `payment:delay=43` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 58.0 | 63.0 | 63.0 | 63.0 |
| `payment:delay=44` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 60.0 | 62.0 | 62.0 | 62.0 |
| `payment:delay=45` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 60.0 | 64.0 | 64.0 | 64.0 |
| `payment:delay=46` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 61.0 | 64.0 | 64.0 | 64.0 |
| `payment:delay=469` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 483.0 | 483.0 | 483.0 | 483.0 |
| `payment:delay=47` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 61.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=48` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 63.0 | 66.0 | 66.0 | 66.0 |
| `payment:delay=483` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 500.0 | 500.0 | 500.0 | 500.0 |
| `payment:delay=49` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 64.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=50` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 65.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=51` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 65.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=52` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 67.0 | 73.0 | 73.0 | 73.0 |
| `payment:delay=523` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 542.0 | 542.0 | 542.0 | 542.0 |
| `payment:delay=53` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 67.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=54` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 68.0 | 70.0 | 70.0 | 70.0 |
| `payment:delay=55` | 14 | `completed | COMPLETED | RESERVED | APPROVED` | 14 | 68.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=56` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 72.0 | 75.0 | 75.0 | 75.0 |
| `payment:delay=57` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 71.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=58` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 73.0 | 76.0 | 76.0 | 76.0 |
| `payment:delay=59` | 19 | `completed | COMPLETED | RESERVED | APPROVED` | 19 | 73.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=60` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 75.0 | 80.0 | 80.0 | 80.0 |
| `payment:delay=61` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 76.0 | 80.0 | 80.0 | 80.0 |
| `payment:delay=62` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 76.0 | 80.0 | 80.0 | 80.0 |
| `payment:delay=622` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 636.0 | 636.0 | 636.0 | 636.0 |
| `payment:delay=63` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 78.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=64` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 78.0 | 83.0 | 83.0 | 83.0 |
| `payment:delay=65` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 78.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=66` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 78.0 | 83.0 | 83.0 | 83.0 |
| `payment:delay=663` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 678.0 | 678.0 | 678.0 | 678.0 |
| `payment:delay=67` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 83.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=68` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 84.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=69` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 82.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=70` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 85.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=71` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 86.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=72` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 85.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=73` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 88.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=74` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 89.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=75` | 20 | `completed | COMPLETED | RESERVED | APPROVED` | 20 | 89.0 | 94.0 | 94.0 | 94.0 |
| `payment:delay=76` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 91.0 | 95.0 | 95.0 | 95.0 |
| `payment:delay=77` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 92.0 | 96.0 | 96.0 | 96.0 |
| `payment:delay=78` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 94.0 | 96.0 | 96.0 | 96.0 |
| `payment:delay=79` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 93.0 | 95.0 | 95.0 | 95.0 |
| `payment:delay=80` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 94.0 | 99.0 | 99.0 | 99.0 |
| `payment:delay=81` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 96.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=82` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 97.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=83` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 97.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=84` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 98.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=85` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 100.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=86` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 99.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=87` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 100.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=88` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 100.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=89` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 104.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=9` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 27.0 | 27.0 | 27.0 | 27.0 |
| `payment:delay=90` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 107.0 | 112.0 | 112.0 | 112.0 |
| `payment:delay=91` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 106.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=92` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 107.0 | 112.0 | 112.0 | 112.0 |
| `payment:delay=93` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 107.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=94` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 106.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=95` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 110.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=96` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 110.0 | 121.0 | 121.0 | 121.0 |
| `payment:delay=97` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 111.0 | 113.0 | 113.0 | 113.0 |
| `payment:delay=98` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 113.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=99` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 112.0 | 117.0 | 117.0 | 117.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 1200 | 94.0 | 240.0 | 379.0 | 678.0 |
| completed | 1200 | 94.0 | 240.0 | 379.0 | 678.0 |
| failed | 0 | - | - | - | - |
| unknown | 0 | - | - | - | - |
