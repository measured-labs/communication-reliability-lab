# 분석 보고 — false-timeout-20261126T055527-r0

- 정답 1200줄, requestId 1200개 (계획 1200)
- 응답으로 가름: completed 1180, failed 0, unknown 20
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 20
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/false-timeout-20261126T055527-r0/expect.json`
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
| `payment:delay=101` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 117.0 | 122.0 | 122.0 | 122.0 |
| `payment:delay=102` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 116.0 | 119.0 | 119.0 | 119.0 |
| `payment:delay=103` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 116.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=104` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 119.0 | 124.0 | 124.0 | 124.0 |
| `payment:delay=105` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 115.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=106` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 121.0 | 123.0 | 123.0 | 123.0 |
| `payment:delay=107` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 122.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=108` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 123.0 | 125.0 | 125.0 | 125.0 |
| `payment:delay=109` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 123.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=110` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 125.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=111` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 126.0 | 130.0 | 130.0 | 130.0 |
| `payment:delay=112` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 127.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=113` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 127.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=114` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 130.0 | 135.0 | 135.0 | 135.0 |
| `payment:delay=115` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 127.0 | 133.0 | 133.0 | 133.0 |
| `payment:delay=116` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 131.0 | 154.0 | 154.0 | 154.0 |
| `payment:delay=117` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 133.0 | 136.0 | 136.0 | 136.0 |
| `payment:delay=118` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 133.0 | 134.0 | 134.0 | 134.0 |
| `payment:delay=119` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 135.0 | 135.0 | 135.0 | 135.0 |
| `payment:delay=120` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 136.0 | 136.0 | 136.0 | 136.0 |
| `payment:delay=121` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 135.0 | 142.0 | 142.0 | 142.0 |
| `payment:delay=122` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 136.0 | 138.0 | 138.0 | 138.0 |
| `payment:delay=123` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 139.0 | 141.0 | 141.0 | 141.0 |
| `payment:delay=124` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 139.0 | 144.0 | 144.0 | 144.0 |
| `payment:delay=125` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 140.0 | 144.0 | 144.0 | 144.0 |
| `payment:delay=126` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 138.0 | 143.0 | 143.0 | 143.0 |
| `payment:delay=127` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 145.0 | 145.0 | 145.0 | 145.0 |
| `payment:delay=128` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 142.0 | 147.0 | 147.0 | 147.0 |
| `payment:delay=129` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 143.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=13` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 28.0 | 28.0 | 28.0 | 28.0 |
| `payment:delay=130` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 141.0 | 141.0 | 141.0 | 141.0 |
| `payment:delay=131` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 146.0 | 148.0 | 148.0 | 148.0 |
| `payment:delay=132` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 146.0 | 149.0 | 149.0 | 149.0 |
| `payment:delay=133` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 141.0 | 147.0 | 147.0 | 147.0 |
| `payment:delay=134` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 147.0 | 149.0 | 149.0 | 149.0 |
| `payment:delay=135` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 146.0 | 147.0 | 147.0 | 147.0 |
| `payment:delay=136` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 154.0 | 156.0 | 156.0 | 156.0 |
| `payment:delay=137` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 151.0 | 153.0 | 153.0 | 153.0 |
| `payment:delay=138` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 151.0 | 152.0 | 152.0 | 152.0 |
| `payment:delay=139` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 154.0 | 157.0 | 157.0 | 157.0 |
| `payment:delay=14` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 28.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=140` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 155.0 | 158.0 | 158.0 | 158.0 |
| `payment:delay=141` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 156.0 | 169.0 | 169.0 | 169.0 |
| `payment:delay=142` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 154.0 | 159.0 | 159.0 | 159.0 |
| `payment:delay=143` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 159.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=144` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 159.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=145` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 158.0 | 158.0 | 158.0 | 158.0 |
| `payment:delay=146` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 161.0 | 161.0 | 161.0 | 161.0 |
| `payment:delay=147` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 161.0 | 162.0 | 162.0 | 162.0 |
| `payment:delay=148` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 162.0 | 163.0 | 163.0 | 163.0 |
| `payment:delay=149` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 159.0 | 163.0 | 163.0 | 163.0 |
| `payment:delay=15` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 24.0 | 24.0 | 24.0 | 24.0 |
| `payment:delay=150` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 167.0 | 167.0 | 167.0 | 167.0 |
| `payment:delay=151` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 162.0 | 164.0 | 164.0 | 164.0 |
| `payment:delay=152` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 165.0 | 168.0 | 168.0 | 168.0 |
| `payment:delay=153` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 167.0 | 167.0 | 167.0 | 167.0 |
| `payment:delay=154` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 165.0 | 170.0 | 170.0 | 170.0 |
| `payment:delay=155` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 170.0 | 173.0 | 173.0 | 173.0 |
| `payment:delay=156` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 167.0 | 174.0 | 174.0 | 174.0 |
| `payment:delay=157` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 172.0 | 174.0 | 174.0 | 174.0 |
| `payment:delay=158` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 169.0 | 170.0 | 170.0 | 170.0 |
| `payment:delay=159` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 174.0 | 176.0 | 176.0 | 176.0 |
| `payment:delay=16` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 25.0 | 35.0 | 35.0 | 35.0 |
| `payment:delay=160` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 175.0 | 175.0 | 175.0 | 175.0 |
| `payment:delay=161` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 177.0 | 177.0 | 177.0 | 177.0 |
| `payment:delay=162` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 178.0 | 182.0 | 182.0 | 182.0 |
| `payment:delay=163` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 178.0 | 180.0 | 180.0 | 180.0 |
| `payment:delay=164` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 175.0 | 181.0 | 181.0 | 181.0 |
| `payment:delay=165` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 178.0 | 181.0 | 181.0 | 181.0 |
| `payment:delay=167` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 179.0 | 179.0 | 179.0 | 179.0 |
| `payment:delay=168` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 182.0 | 183.0 | 183.0 | 183.0 |
| `payment:delay=17` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 32.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=170` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 185.0 | 187.0 | 187.0 | 187.0 |
| `payment:delay=171` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 182.0 | 184.0 | 184.0 | 184.0 |
| `payment:delay=172` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 187.0 | 195.0 | 195.0 | 195.0 |
| `payment:delay=173` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 186.0 | 190.0 | 190.0 | 190.0 |
| `payment:delay=174` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 188.0 | 189.0 | 189.0 | 189.0 |
| `payment:delay=175` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 190.0 | 192.0 | 192.0 | 192.0 |
| `payment:delay=176` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 190.0 | 190.0 | 190.0 | 190.0 |
| `payment:delay=179` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 194.0 | 195.0 | 195.0 | 195.0 |
| `payment:delay=18` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 33.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=180` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 198.0 | 198.0 | 198.0 | 198.0 |
| `payment:delay=181` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 195.0 | 195.0 | 195.0 | 195.0 |
| `payment:delay=182` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 194.0 | 198.0 | 198.0 | 198.0 |
| `payment:delay=185` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 203.0 | 203.0 | 203.0 | 203.0 |
| `payment:delay=187` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 202.0 | 204.0 | 204.0 | 204.0 |
| `payment:delay=19` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 35.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=191` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 207.0 | 207.0 | 207.0 | 207.0 |
| `payment:delay=192` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 207.0 | 207.0 | 207.0 | 207.0 |
| `payment:delay=193` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 207.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=194` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 207.0 | 208.0 | 208.0 | 208.0 |
| `payment:delay=196` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 204.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=197` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 211.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=198` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 216.0 | 216.0 | 216.0 | 216.0 |
| `payment:delay=199` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 212.0 | 215.0 | 215.0 | 215.0 |
| `payment:delay=20` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 29.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=200` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 214.0 | 214.0 | 214.0 | 214.0 |
| `payment:delay=201` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 213.0 | 213.0 | 213.0 | 213.0 |
| `payment:delay=202` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 215.0 | 217.0 | 217.0 | 217.0 |
| `payment:delay=203` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 216.0 | 216.0 | 216.0 | 216.0 |
| `payment:delay=204` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 216.0 | 221.0 | 221.0 | 221.0 |
| `payment:delay=205` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 220.0 | 224.0 | 224.0 | 224.0 |
| `payment:delay=206` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 219.0 | 222.0 | 222.0 | 222.0 |
| `payment:delay=207` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 217.0 | 221.0 | 221.0 | 221.0 |
| `payment:delay=209` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 220.0 | 220.0 | 220.0 | 220.0 |
| `payment:delay=21` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 36.0 | 38.0 | 38.0 | 38.0 |
| `payment:delay=211` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 224.0 | 228.0 | 228.0 | 228.0 |
| `payment:delay=212` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 228.0 | 228.0 | 228.0 | 228.0 |
| `payment:delay=213` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 228.0 | 228.0 | 228.0 | 228.0 |
| `payment:delay=214` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 221.0 | 227.0 | 227.0 | 227.0 |
| `payment:delay=216` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 230.0 | 230.0 | 230.0 | 230.0 |
| `payment:delay=218` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 233.0 | 233.0 | 233.0 | 233.0 |
| `payment:delay=219` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 234.0 | 235.0 | 235.0 | 235.0 |
| `payment:delay=22` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 38.0 | 38.0 | 38.0 | 38.0 |
| `payment:delay=221` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 230.0 | 246.0 | 246.0 | 246.0 |
| `payment:delay=223` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 236.0 | 236.0 | 236.0 | 236.0 |
| `payment:delay=224` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 239.0 | 243.0 | 243.0 | 243.0 |
| `payment:delay=225` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 240.0 | 240.0 | 240.0 | 240.0 |
| `payment:delay=226` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 245.0 | 246.0 | 246.0 | 246.0 |
| `payment:delay=227` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 235.0 | 235.0 | 235.0 | 235.0 |
| `payment:delay=23` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 37.0 | 42.0 | 42.0 | 42.0 |
| `payment:delay=234` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 246.0 | 249.0 | 249.0 | 249.0 |
| `payment:delay=235` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 250.0 | 250.0 | 250.0 | 250.0 |
| `payment:delay=236` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 252.0 | 259.0 | 259.0 | 259.0 |
| `payment:delay=24` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 36.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=243` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 260.0 | 260.0 | 260.0 | 260.0 |
| `payment:delay=244` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 259.0 | 259.0 | 259.0 | 259.0 |
| `payment:delay=245` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 262.0 | 262.0 | 262.0 | 262.0 |
| `payment:delay=248` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 264.0 | 264.0 | 264.0 | 264.0 |
| `payment:delay=25` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 39.0 | 43.0 | 43.0 | 43.0 |
| `payment:delay=250` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 265.0 | 265.0 | 265.0 | 265.0 |
| `payment:delay=254` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 272.0 | 272.0 | 272.0 | 272.0 |
| `payment:delay=255` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 268.0 | 271.0 | 271.0 | 271.0 |
| `payment:delay=258` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 269.0 | 269.0 | 269.0 | 269.0 |
| `payment:delay=259` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 273.0 | 273.0 | 273.0 | 273.0 |
| `payment:delay=26` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 41.0 | 45.0 | 45.0 | 45.0 |
| `payment:delay=268` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 281.0 | 288.0 | 288.0 | 288.0 |
| `payment:delay=269` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 280.0 | 285.0 | 285.0 | 285.0 |
| `payment:delay=27` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 43.0 | 45.0 | 45.0 | 45.0 |
| `payment:delay=272` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 289.0 | 289.0 | 289.0 | 289.0 |
| `payment:delay=273` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 284.0 | 284.0 | 284.0 | 284.0 |
| `payment:delay=276` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 290.0 | 290.0 | 290.0 | 290.0 |
| `payment:delay=278` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 294.0 | 294.0 | 294.0 | 294.0 |
| `payment:delay=279` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 293.0 | 293.0 | 293.0 | 293.0 |
| `payment:delay=28` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 41.0 | 45.0 | 45.0 | 45.0 |
| `payment:delay=280` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 291.0 | 291.0 | 291.0 | 291.0 |
| `payment:delay=281` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 295.0 | 295.0 | 295.0 | 295.0 |
| `payment:delay=282` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 297.0 | 297.0 | 297.0 | 297.0 |
| `payment:delay=287` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 302.0 | 302.0 | 302.0 | 302.0 |
| `payment:delay=29` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 36.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=292` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 305.0 | 305.0 | 305.0 | 305.0 |
| `payment:delay=296` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 315.0 | 315.0 | 315.0 | 315.0 |
| `payment:delay=298` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 308.0 | 310.0 | 310.0 | 310.0 |
| `payment:delay=30` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 45.0 | 47.0 | 47.0 | 47.0 |
| `payment:delay=300` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 315.0 | 315.0 | 315.0 | 315.0 |
| `payment:delay=31` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 46.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=313` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 327.0 | 327.0 | 327.0 | 327.0 |
| `payment:delay=315` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 329.0 | 332.0 | 332.0 | 332.0 |
| `payment:delay=32` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 46.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=322` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 334.0 | 334.0 | 334.0 | 334.0 |
| `payment:delay=323` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 332.0 | 332.0 | 332.0 | 332.0 |
| `payment:delay=33` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 48.0 | 52.0 | 52.0 | 52.0 |
| `payment:delay=335` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 335.0 | 335.0 | 335.0 | 335.0 |
| `payment:delay=338` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 337.0 | 337.0 | 337.0 | 337.0 |
| `payment:delay=34` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 48.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=346` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 330.0 | 330.0 | 330.0 | 330.0 |
| `payment:delay=35` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 50.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=355` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 333.0 | 333.0 | 333.0 | 333.0 |
| `payment:delay=358` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 333.0 | 333.0 | 333.0 | 333.0 |
| `payment:delay=36` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 48.0 | 53.0 | 53.0 | 53.0 |
| `payment:delay=365` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 342.0 | 342.0 | 342.0 | 342.0 |
| `payment:delay=366` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 333.0 | 333.0 | 333.0 | 333.0 |
| `payment:delay=37` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 50.0 | 53.0 | 53.0 | 53.0 |
| `payment:delay=373` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 333.0 | 333.0 | 333.0 | 333.0 |
| `payment:delay=374` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 335.0 | 335.0 | 335.0 | 335.0 |
| `payment:delay=38` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 54.0 | 57.0 | 57.0 | 57.0 |
| `payment:delay=39` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 54.0 | 58.0 | 58.0 | 58.0 |
| `payment:delay=40` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 56.0 | 59.0 | 59.0 | 59.0 |
| `payment:delay=400` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 335.0 | 335.0 | 335.0 | 335.0 |
| `payment:delay=407` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 333.0 | 333.0 | 333.0 | 333.0 |
| `payment:delay=41` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 56.0 | 70.0 | 70.0 | 70.0 |
| `payment:delay=42` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 57.0 | 60.0 | 60.0 | 60.0 |
| `payment:delay=420` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 334.0 | 334.0 | 334.0 | 334.0 |
| `payment:delay=427` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 335.0 | 335.0 | 335.0 | 335.0 |
| `payment:delay=43` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 58.0 | 63.0 | 63.0 | 63.0 |
| `payment:delay=44` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 62.0 | 63.0 | 63.0 | 63.0 |
| `payment:delay=45` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 60.0 | 66.0 | 66.0 | 66.0 |
| `payment:delay=46` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 60.0 | 65.0 | 65.0 | 65.0 |
| `payment:delay=469` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 335.0 | 335.0 | 335.0 | 335.0 |
| `payment:delay=47` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 62.0 | 70.0 | 70.0 | 70.0 |
| `payment:delay=48` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 64.0 | 66.0 | 66.0 | 66.0 |
| `payment:delay=483` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 338.0 | 338.0 | 338.0 | 338.0 |
| `payment:delay=49` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 64.0 | 75.0 | 75.0 | 75.0 |
| `payment:delay=50` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 65.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=51` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 67.0 | 69.0 | 69.0 | 69.0 |
| `payment:delay=52` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 67.0 | 70.0 | 70.0 | 70.0 |
| `payment:delay=523` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 334.0 | 334.0 | 334.0 | 334.0 |
| `payment:delay=53` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 68.0 | 71.0 | 71.0 | 71.0 |
| `payment:delay=54` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 69.0 | 73.0 | 73.0 | 73.0 |
| `payment:delay=55` | 14 | `completed | COMPLETED | RESERVED | APPROVED` | 14 | 70.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=56` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 71.0 | 75.0 | 75.0 | 75.0 |
| `payment:delay=57` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 72.0 | 74.0 | 74.0 | 74.0 |
| `payment:delay=58` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 73.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=59` | 19 | `completed | COMPLETED | RESERVED | APPROVED` | 19 | 73.0 | 79.0 | 79.0 | 79.0 |
| `payment:delay=60` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 75.0 | 79.0 | 79.0 | 79.0 |
| `payment:delay=61` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 75.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=62` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 77.0 | 83.0 | 83.0 | 83.0 |
| `payment:delay=622` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 334.0 | 334.0 | 334.0 | 334.0 |
| `payment:delay=63` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 78.0 | 80.0 | 80.0 | 80.0 |
| `payment:delay=64` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 79.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=65` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 80.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=66` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 79.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=663` | 1 | `unknown | UNKNOWN | UNKNOWN | APPROVED` | 1 | 333.0 | 333.0 | 333.0 | 333.0 |
| `payment:delay=67` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 84.0 | 86.0 | 86.0 | 86.0 |
| `payment:delay=68` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 84.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=69` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 82.0 | 87.0 | 87.0 | 87.0 |
| `payment:delay=70` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 84.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=71` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 87.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=72` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 86.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=73` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 88.0 | 89.0 | 89.0 | 89.0 |
| `payment:delay=74` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 88.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=75` | 20 | `completed | COMPLETED | RESERVED | APPROVED` | 20 | 89.0 | 94.0 | 95.0 | 95.0 |
| `payment:delay=76` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 90.0 | 94.0 | 94.0 | 94.0 |
| `payment:delay=77` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 93.0 | 95.0 | 95.0 | 95.0 |
| `payment:delay=78` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 92.0 | 96.0 | 96.0 | 96.0 |
| `payment:delay=79` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 92.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=80` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 94.0 | 99.0 | 99.0 | 99.0 |
| `payment:delay=81` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 95.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=82` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 97.0 | 113.0 | 113.0 | 113.0 |
| `payment:delay=83` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 97.0 | 106.0 | 106.0 | 106.0 |
| `payment:delay=84` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 99.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=85` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 100.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=86` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 100.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=87` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 100.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=88` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 102.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=89` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 104.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=9` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 27.0 | 27.0 | 27.0 | 27.0 |
| `payment:delay=90` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 107.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=91` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 106.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=92` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 108.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=93` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 109.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=94` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 107.0 | 115.0 | 115.0 | 115.0 |
| `payment:delay=95` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 111.0 | 113.0 | 113.0 | 113.0 |
| `payment:delay=96` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 110.0 | 113.0 | 113.0 | 113.0 |
| `payment:delay=97` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 112.0 | 113.0 | 113.0 | 113.0 |
| `payment:delay=98` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 113.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=99` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 113.0 | 118.0 | 118.0 | 118.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 1200 | 93.0 | 240.0 | 333.0 | 342.0 |
| completed | 1180 | 92.0 | 220.0 | 294.0 | 332.0 |
| failed | 0 | - | - | - | - |
| unknown | 20 | 334.0 | 338.0 | 342.0 | 342.0 |
