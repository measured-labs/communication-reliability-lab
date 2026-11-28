# 분석 보고 — false-timeout-20261126T055825-r2

- 정답 1200줄, requestId 1200개 (계획 1200)
- 응답으로 가름: completed 1200, failed 0, unknown 0
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 0
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/false-timeout-20261126T055825-r2/expect.json`
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
| `payment:delay=100` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 114.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=101` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 112.0 | 125.0 | 125.0 | 125.0 |
| `payment:delay=102` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 116.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=103` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 115.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=104` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 117.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=105` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 118.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=106` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 117.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=107` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 121.0 | 123.0 | 123.0 | 123.0 |
| `payment:delay=108` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 120.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=109` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 120.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=110` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 122.0 | 124.0 | 124.0 | 124.0 |
| `payment:delay=111` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 125.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=112` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 125.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=113` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 126.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=114` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 130.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=115` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 128.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=116` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 131.0 | 141.0 | 141.0 | 141.0 |
| `payment:delay=117` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 129.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=118` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 132.0 | 133.0 | 133.0 | 133.0 |
| `payment:delay=119` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 133.0 | 133.0 | 133.0 | 133.0 |
| `payment:delay=120` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 134.0 | 134.0 | 134.0 | 134.0 |
| `payment:delay=121` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 133.0 | 141.0 | 141.0 | 141.0 |
| `payment:delay=122` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 135.0 | 135.0 | 135.0 | 135.0 |
| `payment:delay=123` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 137.0 | 138.0 | 138.0 | 138.0 |
| `payment:delay=124` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 135.0 | 141.0 | 141.0 | 141.0 |
| `payment:delay=125` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 139.0 | 145.0 | 145.0 | 145.0 |
| `payment:delay=126` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 139.0 | 140.0 | 140.0 | 140.0 |
| `payment:delay=127` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 142.0 | 142.0 | 142.0 | 142.0 |
| `payment:delay=128` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 141.0 | 142.0 | 142.0 | 142.0 |
| `payment:delay=129` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 142.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=13` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 27.0 | 27.0 | 27.0 | 27.0 |
| `payment:delay=130` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 145.0 | 145.0 | 145.0 | 145.0 |
| `payment:delay=131` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 145.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=132` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 146.0 | 147.0 | 147.0 | 147.0 |
| `payment:delay=133` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 142.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=134` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 144.0 | 148.0 | 148.0 | 148.0 |
| `payment:delay=135` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 148.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=136` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 148.0 | 151.0 | 151.0 | 151.0 |
| `payment:delay=137` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 152.0 | 153.0 | 153.0 | 153.0 |
| `payment:delay=138` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 152.0 | 152.0 | 152.0 | 152.0 |
| `payment:delay=139` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 154.0 | 154.0 | 154.0 | 154.0 |
| `payment:delay=14` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 30.0 | 31.0 | 31.0 | 31.0 |
| `payment:delay=140` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 153.0 | 155.0 | 155.0 | 155.0 |
| `payment:delay=141` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 152.0 | 164.0 | 164.0 | 164.0 |
| `payment:delay=142` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 153.0 | 158.0 | 158.0 | 158.0 |
| `payment:delay=143` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 156.0 | 157.0 | 157.0 | 157.0 |
| `payment:delay=144` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 158.0 | 166.0 | 166.0 | 166.0 |
| `payment:delay=145` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 158.0 | 158.0 | 158.0 | 158.0 |
| `payment:delay=146` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 160.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=147` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 158.0 | 161.0 | 161.0 | 161.0 |
| `payment:delay=148` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 159.0 | 171.0 | 171.0 | 171.0 |
| `payment:delay=149` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 162.0 | 163.0 | 163.0 | 163.0 |
| `payment:delay=15` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 30.0 | 30.0 | 30.0 | 30.0 |
| `payment:delay=150` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 165.0 | 165.0 | 165.0 | 165.0 |
| `payment:delay=151` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 164.0 | 165.0 | 165.0 | 165.0 |
| `payment:delay=152` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 165.0 | 167.0 | 167.0 | 167.0 |
| `payment:delay=153` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 166.0 | 166.0 | 166.0 | 166.0 |
| `payment:delay=154` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 168.0 | 169.0 | 169.0 | 169.0 |
| `payment:delay=155` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 169.0 | 170.0 | 170.0 | 170.0 |
| `payment:delay=156` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 167.0 | 172.0 | 172.0 | 172.0 |
| `payment:delay=157` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 169.0 | 172.0 | 172.0 | 172.0 |
| `payment:delay=158` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 167.0 | 171.0 | 171.0 | 171.0 |
| `payment:delay=159` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 172.0 | 173.0 | 173.0 | 173.0 |
| `payment:delay=16` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 25.0 | 31.0 | 31.0 | 31.0 |
| `payment:delay=160` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 173.0 | 173.0 | 173.0 | 173.0 |
| `payment:delay=161` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 175.0 | 175.0 | 175.0 | 175.0 |
| `payment:delay=162` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 176.0 | 176.0 | 176.0 | 176.0 |
| `payment:delay=163` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 175.0 | 179.0 | 179.0 | 179.0 |
| `payment:delay=164` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 178.0 | 194.0 | 194.0 | 194.0 |
| `payment:delay=165` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 177.0 | 180.0 | 180.0 | 180.0 |
| `payment:delay=167` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 182.0 | 182.0 | 182.0 | 182.0 |
| `payment:delay=168` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 182.0 | 182.0 | 182.0 | 182.0 |
| `payment:delay=17` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 31.0 | 31.0 | 31.0 | 31.0 |
| `payment:delay=170` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 183.0 | 186.0 | 186.0 | 186.0 |
| `payment:delay=171` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 184.0 | 186.0 | 186.0 | 186.0 |
| `payment:delay=172` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 185.0 | 188.0 | 188.0 | 188.0 |
| `payment:delay=173` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 182.0 | 187.0 | 187.0 | 187.0 |
| `payment:delay=174` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 185.0 | 193.0 | 193.0 | 193.0 |
| `payment:delay=175` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 188.0 | 188.0 | 188.0 | 188.0 |
| `payment:delay=176` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 186.0 | 186.0 | 186.0 | 186.0 |
| `payment:delay=179` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 188.0 | 193.0 | 193.0 | 193.0 |
| `payment:delay=18` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 32.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=180` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 191.0 | 191.0 | 191.0 | 191.0 |
| `payment:delay=181` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 191.0 | 191.0 | 191.0 | 191.0 |
| `payment:delay=182` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 195.0 | 196.0 | 196.0 | 196.0 |
| `payment:delay=185` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 200.0 | 200.0 | 200.0 | 200.0 |
| `payment:delay=187` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 198.0 | 200.0 | 200.0 | 200.0 |
| `payment:delay=19` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 34.0 | 34.0 | 34.0 | 34.0 |
| `payment:delay=191` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 197.0 | 197.0 | 197.0 | 197.0 |
| `payment:delay=192` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 203.0 | 203.0 | 203.0 | 203.0 |
| `payment:delay=193` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 207.0 | 208.0 | 208.0 | 208.0 |
| `payment:delay=194` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 208.0 | 208.0 | 208.0 | 208.0 |
| `payment:delay=196` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 206.0 | 210.0 | 210.0 | 210.0 |
| `payment:delay=197` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 209.0 | 209.0 | 209.0 | 209.0 |
| `payment:delay=198` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 212.0 | 212.0 | 212.0 | 212.0 |
| `payment:delay=199` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 211.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=20` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 34.0 | 35.0 | 35.0 | 35.0 |
| `payment:delay=200` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 212.0 | 212.0 | 212.0 | 212.0 |
| `payment:delay=201` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 212.0 | 212.0 | 212.0 | 212.0 |
| `payment:delay=202` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 212.0 | 215.0 | 215.0 | 215.0 |
| `payment:delay=203` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 217.0 | 217.0 | 217.0 | 217.0 |
| `payment:delay=204` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 217.0 | 218.0 | 218.0 | 218.0 |
| `payment:delay=205` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 216.0 | 220.0 | 220.0 | 220.0 |
| `payment:delay=206` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 218.0 | 219.0 | 219.0 | 219.0 |
| `payment:delay=207` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 216.0 | 219.0 | 219.0 | 219.0 |
| `payment:delay=209` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 220.0 | 220.0 | 220.0 | 220.0 |
| `payment:delay=21` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 35.0 | 36.0 | 36.0 | 36.0 |
| `payment:delay=211` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 217.0 | 226.0 | 226.0 | 226.0 |
| `payment:delay=212` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 225.0 | 225.0 | 225.0 | 225.0 |
| `payment:delay=213` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 227.0 | 227.0 | 227.0 | 227.0 |
| `payment:delay=214` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 225.0 | 228.0 | 228.0 | 228.0 |
| `payment:delay=216` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 229.0 | 229.0 | 229.0 | 229.0 |
| `payment:delay=218` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 227.0 | 227.0 | 227.0 | 227.0 |
| `payment:delay=219` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 230.0 | 231.0 | 231.0 | 231.0 |
| `payment:delay=22` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 35.0 | 37.0 | 37.0 | 37.0 |
| `payment:delay=221` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 234.0 | 236.0 | 236.0 | 236.0 |
| `payment:delay=223` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 237.0 | 237.0 | 237.0 | 237.0 |
| `payment:delay=224` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 235.0 | 238.0 | 238.0 | 238.0 |
| `payment:delay=225` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 236.0 | 236.0 | 236.0 | 236.0 |
| `payment:delay=226` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 238.0 | 240.0 | 240.0 | 240.0 |
| `payment:delay=227` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 240.0 | 240.0 | 240.0 | 240.0 |
| `payment:delay=23` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 30.0 | 37.0 | 37.0 | 37.0 |
| `payment:delay=234` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 248.0 | 248.0 | 248.0 | 248.0 |
| `payment:delay=235` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 243.0 | 243.0 | 243.0 | 243.0 |
| `payment:delay=236` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 251.0 | 251.0 | 251.0 | 251.0 |
| `payment:delay=24` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 34.0 | 37.0 | 37.0 | 37.0 |
| `payment:delay=243` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 253.0 | 253.0 | 253.0 | 253.0 |
| `payment:delay=244` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 259.0 | 259.0 | 259.0 | 259.0 |
| `payment:delay=245` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 259.0 | 259.0 | 259.0 | 259.0 |
| `payment:delay=248` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 259.0 | 259.0 | 259.0 | 259.0 |
| `payment:delay=25` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 38.0 | 40.0 | 40.0 | 40.0 |
| `payment:delay=250` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 263.0 | 263.0 | 263.0 | 263.0 |
| `payment:delay=254` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 266.0 | 266.0 | 266.0 | 266.0 |
| `payment:delay=255` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 264.0 | 269.0 | 269.0 | 269.0 |
| `payment:delay=258` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 272.0 | 272.0 | 272.0 | 272.0 |
| `payment:delay=259` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 272.0 | 272.0 | 272.0 | 272.0 |
| `payment:delay=26` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 39.0 | 41.0 | 41.0 | 41.0 |
| `payment:delay=268` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 278.0 | 282.0 | 282.0 | 282.0 |
| `payment:delay=269` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 283.0 | 284.0 | 284.0 | 284.0 |
| `payment:delay=27` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 41.0 | 42.0 | 42.0 | 42.0 |
| `payment:delay=272` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 287.0 | 287.0 | 287.0 | 287.0 |
| `payment:delay=273` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 282.0 | 282.0 | 282.0 | 282.0 |
| `payment:delay=276` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 286.0 | 286.0 | 286.0 | 286.0 |
| `payment:delay=278` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 285.0 | 285.0 | 285.0 | 285.0 |
| `payment:delay=279` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 284.0 | 284.0 | 284.0 | 284.0 |
| `payment:delay=28` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 41.0 | 42.0 | 42.0 | 42.0 |
| `payment:delay=280` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 293.0 | 293.0 | 293.0 | 293.0 |
| `payment:delay=281` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 295.0 | 295.0 | 295.0 | 295.0 |
| `payment:delay=282` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 295.0 | 295.0 | 295.0 | 295.0 |
| `payment:delay=287` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 300.0 | 300.0 | 300.0 | 300.0 |
| `payment:delay=29` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 43.0 | 43.0 | 43.0 | 43.0 |
| `payment:delay=292` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 305.0 | 305.0 | 305.0 | 305.0 |
| `payment:delay=296` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 309.0 | 309.0 | 309.0 | 309.0 |
| `payment:delay=298` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 308.0 | 309.0 | 309.0 | 309.0 |
| `payment:delay=30` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 44.0 | 45.0 | 45.0 | 45.0 |
| `payment:delay=300` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 311.0 | 311.0 | 311.0 | 311.0 |
| `payment:delay=31` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 45.0 | 46.0 | 46.0 | 46.0 |
| `payment:delay=313` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 327.0 | 327.0 | 327.0 | 327.0 |
| `payment:delay=315` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 327.0 | 329.0 | 329.0 | 329.0 |
| `payment:delay=32` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 45.0 | 46.0 | 46.0 | 46.0 |
| `payment:delay=322` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 334.0 | 334.0 | 334.0 | 334.0 |
| `payment:delay=323` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 334.0 | 334.0 | 334.0 | 334.0 |
| `payment:delay=33` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 47.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=335` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 348.0 | 348.0 | 348.0 | 348.0 |
| `payment:delay=338` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 349.0 | 349.0 | 349.0 | 349.0 |
| `payment:delay=34` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 48.0 | 49.0 | 49.0 | 49.0 |
| `payment:delay=346` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 357.0 | 357.0 | 357.0 | 357.0 |
| `payment:delay=35` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 48.0 | 54.0 | 54.0 | 54.0 |
| `payment:delay=355` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 366.0 | 366.0 | 366.0 | 366.0 |
| `payment:delay=358` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 373.0 | 373.0 | 373.0 | 373.0 |
| `payment:delay=36` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 50.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=365` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 381.0 | 381.0 | 381.0 | 381.0 |
| `payment:delay=366` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 377.0 | 377.0 | 377.0 | 377.0 |
| `payment:delay=37` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 50.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=373` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 383.0 | 383.0 | 383.0 | 383.0 |
| `payment:delay=374` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 387.0 | 387.0 | 387.0 | 387.0 |
| `payment:delay=38` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 51.0 | 54.0 | 54.0 | 54.0 |
| `payment:delay=39` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 52.0 | 59.0 | 59.0 | 59.0 |
| `payment:delay=40` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 53.0 | 55.0 | 55.0 | 55.0 |
| `payment:delay=400` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 414.0 | 414.0 | 414.0 | 414.0 |
| `payment:delay=407` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 417.0 | 417.0 | 417.0 | 417.0 |
| `payment:delay=41` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 55.0 | 58.0 | 58.0 | 58.0 |
| `payment:delay=42` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 55.0 | 57.0 | 57.0 | 57.0 |
| `payment:delay=420` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 433.0 | 433.0 | 433.0 | 433.0 |
| `payment:delay=427` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 438.0 | 438.0 | 438.0 | 438.0 |
| `payment:delay=43` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 56.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=44` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 58.0 | 65.0 | 65.0 | 65.0 |
| `payment:delay=45` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 59.0 | 60.0 | 60.0 | 60.0 |
| `payment:delay=46` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 59.0 | 63.0 | 63.0 | 63.0 |
| `payment:delay=469` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 481.0 | 481.0 | 481.0 | 481.0 |
| `payment:delay=47` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 59.0 | 63.0 | 63.0 | 63.0 |
| `payment:delay=48` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 61.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=483` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 494.0 | 494.0 | 494.0 | 494.0 |
| `payment:delay=49` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 62.0 | 64.0 | 64.0 | 64.0 |
| `payment:delay=50` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 63.0 | 66.0 | 66.0 | 66.0 |
| `payment:delay=51` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 64.0 | 65.0 | 65.0 | 65.0 |
| `payment:delay=52` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 65.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=523` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 534.0 | 534.0 | 534.0 | 534.0 |
| `payment:delay=53` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 66.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=54` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 67.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=55` | 14 | `completed | COMPLETED | RESERVED | APPROVED` | 14 | 68.0 | 76.0 | 76.0 | 76.0 |
| `payment:delay=56` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 70.0 | 77.0 | 77.0 | 77.0 |
| `payment:delay=57` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 70.0 | 72.0 | 72.0 | 72.0 |
| `payment:delay=58` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 71.0 | 73.0 | 73.0 | 73.0 |
| `payment:delay=59` | 19 | `completed | COMPLETED | RESERVED | APPROVED` | 19 | 72.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=60` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 73.0 | 75.0 | 75.0 | 75.0 |
| `payment:delay=61` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 74.0 | 79.0 | 79.0 | 79.0 |
| `payment:delay=62` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 75.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=622` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 636.0 | 636.0 | 636.0 | 636.0 |
| `payment:delay=63` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 76.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=64` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 77.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=65` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 80.0 | 82.0 | 82.0 | 82.0 |
| `payment:delay=66` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 80.0 | 81.0 | 81.0 | 81.0 |
| `payment:delay=663` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 676.0 | 676.0 | 676.0 | 676.0 |
| `payment:delay=67` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 81.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=68` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 82.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=69` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 83.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=70` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 84.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=71` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 84.0 | 86.0 | 86.0 | 86.0 |
| `payment:delay=72` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 83.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=73` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 87.0 | 88.0 | 88.0 | 88.0 |
| `payment:delay=74` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 87.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=75` | 20 | `completed | COMPLETED | RESERVED | APPROVED` | 20 | 88.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=76` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 89.0 | 96.0 | 96.0 | 96.0 |
| `payment:delay=77` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 90.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=78` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 92.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=79` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 88.0 | 94.0 | 94.0 | 94.0 |
| `payment:delay=80` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 94.0 | 98.0 | 98.0 | 98.0 |
| `payment:delay=81` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 94.0 | 96.0 | 96.0 | 96.0 |
| `payment:delay=82` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 96.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=83` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 96.0 | 98.0 | 98.0 | 98.0 |
| `payment:delay=84` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 97.0 | 99.0 | 99.0 | 99.0 |
| `payment:delay=85` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 99.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=86` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 100.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=87` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 100.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=88` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 101.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=89` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 102.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=9` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 23.0 | 23.0 | 23.0 | 23.0 |
| `payment:delay=90` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 104.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=91` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 104.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=92` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 106.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=93` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 107.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=94` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 108.0 | 114.0 | 114.0 | 114.0 |
| `payment:delay=95` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 109.0 | 114.0 | 114.0 | 114.0 |
| `payment:delay=96` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 109.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=97` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 110.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=98` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 112.0 | 113.0 | 113.0 | 113.0 |
| `payment:delay=99` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 113.0 | 114.0 | 114.0 | 114.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 1200 | 91.0 | 237.0 | 377.0 | 676.0 |
| completed | 1200 | 91.0 | 237.0 | 377.0 | 676.0 |
| failed | 0 | - | - | - | - |
| unknown | 0 | - | - | - | - |
