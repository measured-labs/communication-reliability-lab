# 분석 보고 — false-timeout-20261126T060336-r0

- 정답 1200줄, requestId 1200개 (계획 1200)
- 응답으로 가름: completed 1200, failed 0, unknown 0
- **잃음 0 · 두 번 처리됨 0** · 층 사이 처리 수가 다른 requestId 0
- 금액이 보낸 금액과 다른 처리 행 0 · 정답에 없는 행 0 · PENDING 행 0 (셋은 늘 0이어야 한다)
- 장애 수(넣은 수 = 서비스가 센 수): 일치
- 기대 파일 `/runs/false-timeout-20261126T060336-r0/expect.json`
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
| `payment:delay=100` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 114.0 | 118.0 | 118.0 | 118.0 |
| `payment:delay=101` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 116.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=102` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 116.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=103` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 118.0 | 123.0 | 123.0 | 123.0 |
| `payment:delay=104` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 119.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=105` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 116.0 | 120.0 | 120.0 | 120.0 |
| `payment:delay=106` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 121.0 | 127.0 | 127.0 | 127.0 |
| `payment:delay=107` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 122.0 | 125.0 | 125.0 | 125.0 |
| `payment:delay=108` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 123.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=109` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 124.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=110` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 124.0 | 126.0 | 126.0 | 126.0 |
| `payment:delay=111` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 125.0 | 128.0 | 128.0 | 128.0 |
| `payment:delay=112` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 127.0 | 129.0 | 129.0 | 129.0 |
| `payment:delay=113` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 128.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=114` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 134.0 | 139.0 | 139.0 | 139.0 |
| `payment:delay=115` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 130.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=116` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 130.0 | 153.0 | 153.0 | 153.0 |
| `payment:delay=117` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 133.0 | 135.0 | 135.0 | 135.0 |
| `payment:delay=118` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 132.0 | 134.0 | 134.0 | 134.0 |
| `payment:delay=119` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 131.0 | 131.0 | 131.0 | 131.0 |
| `payment:delay=120` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 136.0 | 137.0 | 137.0 | 137.0 |
| `payment:delay=121` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 132.0 | 143.0 | 143.0 | 143.0 |
| `payment:delay=122` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 137.0 | 138.0 | 138.0 | 138.0 |
| `payment:delay=123` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 138.0 | 138.0 | 138.0 | 138.0 |
| `payment:delay=124` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 140.0 | 143.0 | 143.0 | 143.0 |
| `payment:delay=125` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 140.0 | 142.0 | 142.0 | 142.0 |
| `payment:delay=126` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 139.0 | 145.0 | 145.0 | 145.0 |
| `payment:delay=127` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 141.0 | 141.0 | 141.0 | 141.0 |
| `payment:delay=128` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 142.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=129` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 143.0 | 145.0 | 145.0 | 145.0 |
| `payment:delay=13` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 28.0 | 31.0 | 31.0 | 31.0 |
| `payment:delay=130` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 146.0 | 146.0 | 146.0 | 146.0 |
| `payment:delay=131` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 143.0 | 147.0 | 147.0 | 147.0 |
| `payment:delay=132` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 147.0 | 149.0 | 149.0 | 149.0 |
| `payment:delay=133` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 148.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=134` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 145.0 | 163.0 | 163.0 | 163.0 |
| `payment:delay=135` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 142.0 | 150.0 | 150.0 | 150.0 |
| `payment:delay=136` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 156.0 | 157.0 | 157.0 | 157.0 |
| `payment:delay=137` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 153.0 | 155.0 | 155.0 | 155.0 |
| `payment:delay=138` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 152.0 | 153.0 | 153.0 | 153.0 |
| `payment:delay=139` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 154.0 | 157.0 | 157.0 | 157.0 |
| `payment:delay=14` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 24.0 | 26.0 | 26.0 | 26.0 |
| `payment:delay=140` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 151.0 | 157.0 | 157.0 | 157.0 |
| `payment:delay=141` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 156.0 | 163.0 | 163.0 | 163.0 |
| `payment:delay=142` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 157.0 | 158.0 | 158.0 | 158.0 |
| `payment:delay=143` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 153.0 | 159.0 | 159.0 | 159.0 |
| `payment:delay=144` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 158.0 | 159.0 | 159.0 | 159.0 |
| `payment:delay=145` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 160.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=146` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 161.0 | 161.0 | 161.0 | 161.0 |
| `payment:delay=147` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 161.0 | 162.0 | 162.0 | 162.0 |
| `payment:delay=148` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 163.0 | 167.0 | 167.0 | 167.0 |
| `payment:delay=149` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 159.0 | 164.0 | 164.0 | 164.0 |
| `payment:delay=15` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 33.0 | 33.0 | 33.0 | 33.0 |
| `payment:delay=150` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 168.0 | 168.0 | 168.0 | 168.0 |
| `payment:delay=151` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 158.0 | 165.0 | 165.0 | 165.0 |
| `payment:delay=152` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 167.0 | 169.0 | 169.0 | 169.0 |
| `payment:delay=153` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 160.0 | 160.0 | 160.0 | 160.0 |
| `payment:delay=154` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 168.0 | 170.0 | 170.0 | 170.0 |
| `payment:delay=155` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 171.0 | 176.0 | 176.0 | 176.0 |
| `payment:delay=156` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 170.0 | 174.0 | 174.0 | 174.0 |
| `payment:delay=157` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 171.0 | 172.0 | 172.0 | 172.0 |
| `payment:delay=158` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 168.0 | 169.0 | 169.0 | 169.0 |
| `payment:delay=159` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 170.0 | 175.0 | 175.0 | 175.0 |
| `payment:delay=16` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 26.0 | 34.0 | 34.0 | 34.0 |
| `payment:delay=160` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 175.0 | 175.0 | 175.0 | 175.0 |
| `payment:delay=161` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 175.0 | 176.0 | 176.0 | 176.0 |
| `payment:delay=162` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 177.0 | 177.0 | 177.0 | 177.0 |
| `payment:delay=163` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 179.0 | 183.0 | 183.0 | 183.0 |
| `payment:delay=164` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 177.0 | 179.0 | 179.0 | 179.0 |
| `payment:delay=165` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 179.0 | 180.0 | 180.0 | 180.0 |
| `payment:delay=167` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 182.0 | 182.0 | 182.0 | 182.0 |
| `payment:delay=168` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 183.0 | 184.0 | 184.0 | 184.0 |
| `payment:delay=17` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 36.0 | 36.0 | 36.0 | 36.0 |
| `payment:delay=170` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 185.0 | 189.0 | 189.0 | 189.0 |
| `payment:delay=171` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 183.0 | 190.0 | 190.0 | 190.0 |
| `payment:delay=172` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 187.0 | 190.0 | 190.0 | 190.0 |
| `payment:delay=173` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 182.0 | 193.0 | 193.0 | 193.0 |
| `payment:delay=174` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 189.0 | 190.0 | 190.0 | 190.0 |
| `payment:delay=175` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 191.0 | 191.0 | 191.0 | 191.0 |
| `payment:delay=176` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 189.0 | 189.0 | 189.0 | 189.0 |
| `payment:delay=179` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 193.0 | 194.0 | 194.0 | 194.0 |
| `payment:delay=18` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 32.0 | 32.0 | 32.0 | 32.0 |
| `payment:delay=180` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 198.0 | 198.0 | 198.0 | 198.0 |
| `payment:delay=181` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 194.0 | 194.0 | 194.0 | 194.0 |
| `payment:delay=182` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 195.0 | 199.0 | 199.0 | 199.0 |
| `payment:delay=185` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 202.0 | 202.0 | 202.0 | 202.0 |
| `payment:delay=187` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 197.0 | 203.0 | 203.0 | 203.0 |
| `payment:delay=19` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 35.0 | 40.0 | 40.0 | 40.0 |
| `payment:delay=191` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 207.0 | 207.0 | 207.0 | 207.0 |
| `payment:delay=192` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 207.0 | 207.0 | 207.0 | 207.0 |
| `payment:delay=193` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 206.0 | 215.0 | 215.0 | 215.0 |
| `payment:delay=194` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 209.0 | 211.0 | 211.0 | 211.0 |
| `payment:delay=196` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 211.0 | 214.0 | 214.0 | 214.0 |
| `payment:delay=197` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 210.0 | 210.0 | 210.0 | 210.0 |
| `payment:delay=198` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 215.0 | 215.0 | 215.0 | 215.0 |
| `payment:delay=199` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 213.0 | 218.0 | 218.0 | 218.0 |
| `payment:delay=20` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 27.0 | 38.0 | 38.0 | 38.0 |
| `payment:delay=200` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 214.0 | 214.0 | 214.0 | 214.0 |
| `payment:delay=201` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 216.0 | 216.0 | 216.0 | 216.0 |
| `payment:delay=202` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 215.0 | 221.0 | 221.0 | 221.0 |
| `payment:delay=203` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 213.0 | 213.0 | 213.0 | 213.0 |
| `payment:delay=204` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 220.0 | 223.0 | 223.0 | 223.0 |
| `payment:delay=205` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 216.0 | 216.0 | 216.0 | 216.0 |
| `payment:delay=206` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 220.0 | 221.0 | 221.0 | 221.0 |
| `payment:delay=207` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 222.0 | 222.0 | 222.0 | 222.0 |
| `payment:delay=209` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 224.0 | 224.0 | 224.0 | 224.0 |
| `payment:delay=21` | 4 | `completed | COMPLETED | RESERVED | APPROVED` | 4 | 35.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=211` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 222.0 | 228.0 | 228.0 | 228.0 |
| `payment:delay=212` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 231.0 | 231.0 | 231.0 | 231.0 |
| `payment:delay=213` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 229.0 | 229.0 | 229.0 | 229.0 |
| `payment:delay=214` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 229.0 | 231.0 | 231.0 | 231.0 |
| `payment:delay=216` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 230.0 | 230.0 | 230.0 | 230.0 |
| `payment:delay=218` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 233.0 | 233.0 | 233.0 | 233.0 |
| `payment:delay=219` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 234.0 | 235.0 | 235.0 | 235.0 |
| `payment:delay=22` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 36.0 | 38.0 | 38.0 | 38.0 |
| `payment:delay=221` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 239.0 | 249.0 | 249.0 | 249.0 |
| `payment:delay=223` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 242.0 | 242.0 | 242.0 | 242.0 |
| `payment:delay=224` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 238.0 | 242.0 | 242.0 | 242.0 |
| `payment:delay=225` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 240.0 | 240.0 | 240.0 | 240.0 |
| `payment:delay=226` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 239.0 | 244.0 | 244.0 | 244.0 |
| `payment:delay=227` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 241.0 | 241.0 | 241.0 | 241.0 |
| `payment:delay=23` | 3 | `completed | COMPLETED | RESERVED | APPROVED` | 3 | 37.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=234` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 250.0 | 250.0 | 250.0 | 250.0 |
| `payment:delay=235` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 245.0 | 245.0 | 245.0 | 245.0 |
| `payment:delay=236` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 252.0 | 254.0 | 254.0 | 254.0 |
| `payment:delay=24` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 38.0 | 39.0 | 39.0 | 39.0 |
| `payment:delay=243` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 255.0 | 255.0 | 255.0 | 255.0 |
| `payment:delay=244` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 260.0 | 260.0 | 260.0 | 260.0 |
| `payment:delay=245` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 260.0 | 260.0 | 260.0 | 260.0 |
| `payment:delay=248` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 266.0 | 266.0 | 266.0 | 266.0 |
| `payment:delay=25` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 40.0 | 42.0 | 42.0 | 42.0 |
| `payment:delay=250` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 261.0 | 261.0 | 261.0 | 261.0 |
| `payment:delay=254` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 272.0 | 272.0 | 272.0 | 272.0 |
| `payment:delay=255` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 270.0 | 270.0 | 270.0 | 270.0 |
| `payment:delay=258` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 271.0 | 271.0 | 271.0 | 271.0 |
| `payment:delay=259` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 274.0 | 274.0 | 274.0 | 274.0 |
| `payment:delay=26` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 42.0 | 45.0 | 45.0 | 45.0 |
| `payment:delay=268` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 283.0 | 284.0 | 284.0 | 284.0 |
| `payment:delay=269` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 284.0 | 288.0 | 288.0 | 288.0 |
| `payment:delay=27` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 43.0 | 44.0 | 44.0 | 44.0 |
| `payment:delay=272` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 291.0 | 291.0 | 291.0 | 291.0 |
| `payment:delay=273` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 288.0 | 288.0 | 288.0 | 288.0 |
| `payment:delay=276` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 290.0 | 290.0 | 290.0 | 290.0 |
| `payment:delay=278` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 293.0 | 293.0 | 293.0 | 293.0 |
| `payment:delay=279` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 293.0 | 293.0 | 293.0 | 293.0 |
| `payment:delay=28` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 43.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=280` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 295.0 | 295.0 | 295.0 | 295.0 |
| `payment:delay=281` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 296.0 | 296.0 | 296.0 | 296.0 |
| `payment:delay=282` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 298.0 | 298.0 | 298.0 | 298.0 |
| `payment:delay=287` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 302.0 | 302.0 | 302.0 | 302.0 |
| `payment:delay=29` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 42.0 | 43.0 | 43.0 | 43.0 |
| `payment:delay=292` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 303.0 | 303.0 | 303.0 | 303.0 |
| `payment:delay=296` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 310.0 | 310.0 | 310.0 | 310.0 |
| `payment:delay=298` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 310.0 | 311.0 | 311.0 | 311.0 |
| `payment:delay=30` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 45.0 | 53.0 | 53.0 | 53.0 |
| `payment:delay=300` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 312.0 | 312.0 | 312.0 | 312.0 |
| `payment:delay=31` | 5 | `completed | COMPLETED | RESERVED | APPROVED` | 5 | 46.0 | 47.0 | 47.0 | 47.0 |
| `payment:delay=313` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 328.0 | 328.0 | 328.0 | 328.0 |
| `payment:delay=315` | 2 | `completed | COMPLETED | RESERVED | APPROVED` | 2 | 323.0 | 332.0 | 332.0 | 332.0 |
| `payment:delay=32` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 46.0 | 48.0 | 48.0 | 48.0 |
| `payment:delay=322` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 333.0 | 333.0 | 333.0 | 333.0 |
| `payment:delay=323` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 333.0 | 333.0 | 333.0 | 333.0 |
| `payment:delay=33` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 47.0 | 49.0 | 49.0 | 49.0 |
| `payment:delay=335` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 350.0 | 350.0 | 350.0 | 350.0 |
| `payment:delay=338` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 355.0 | 355.0 | 355.0 | 355.0 |
| `payment:delay=34` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 49.0 | 55.0 | 55.0 | 55.0 |
| `payment:delay=346` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 361.0 | 361.0 | 361.0 | 361.0 |
| `payment:delay=35` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 50.0 | 51.0 | 51.0 | 51.0 |
| `payment:delay=355` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 371.0 | 371.0 | 371.0 | 371.0 |
| `payment:delay=358` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 373.0 | 373.0 | 373.0 | 373.0 |
| `payment:delay=36` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 51.0 | 54.0 | 54.0 | 54.0 |
| `payment:delay=365` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 385.0 | 385.0 | 385.0 | 385.0 |
| `payment:delay=366` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 386.0 | 386.0 | 386.0 | 386.0 |
| `payment:delay=37` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 51.0 | 53.0 | 53.0 | 53.0 |
| `payment:delay=373` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 387.0 | 387.0 | 387.0 | 387.0 |
| `payment:delay=374` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 384.0 | 384.0 | 384.0 | 384.0 |
| `payment:delay=38` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 52.0 | 66.0 | 66.0 | 66.0 |
| `payment:delay=39` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 55.0 | 58.0 | 58.0 | 58.0 |
| `payment:delay=40` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 55.0 | 59.0 | 59.0 | 59.0 |
| `payment:delay=400` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 414.0 | 414.0 | 414.0 | 414.0 |
| `payment:delay=407` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 422.0 | 422.0 | 422.0 | 422.0 |
| `payment:delay=41` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 56.0 | 60.0 | 60.0 | 60.0 |
| `payment:delay=42` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 57.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=420` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 442.0 | 442.0 | 442.0 | 442.0 |
| `payment:delay=427` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 442.0 | 442.0 | 442.0 | 442.0 |
| `payment:delay=43` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 58.0 | 62.0 | 62.0 | 62.0 |
| `payment:delay=44` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 61.0 | 62.0 | 62.0 | 62.0 |
| `payment:delay=45` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 60.0 | 61.0 | 61.0 | 61.0 |
| `payment:delay=46` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 61.0 | 64.0 | 64.0 | 64.0 |
| `payment:delay=469` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 480.0 | 480.0 | 480.0 | 480.0 |
| `payment:delay=47` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 61.0 | 66.0 | 66.0 | 66.0 |
| `payment:delay=48` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 63.0 | 76.0 | 76.0 | 76.0 |
| `payment:delay=483` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 509.0 | 509.0 | 509.0 | 509.0 |
| `payment:delay=49` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 64.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=50` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 64.0 | 68.0 | 68.0 | 68.0 |
| `payment:delay=51` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 66.0 | 70.0 | 70.0 | 70.0 |
| `payment:delay=52` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 67.0 | 72.0 | 72.0 | 72.0 |
| `payment:delay=523` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 541.0 | 541.0 | 541.0 | 541.0 |
| `payment:delay=53` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 67.0 | 78.0 | 78.0 | 78.0 |
| `payment:delay=54` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 68.0 | 72.0 | 72.0 | 72.0 |
| `payment:delay=55` | 14 | `completed | COMPLETED | RESERVED | APPROVED` | 14 | 70.0 | 80.0 | 80.0 | 80.0 |
| `payment:delay=56` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 71.0 | 82.0 | 82.0 | 82.0 |
| `payment:delay=57` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 71.0 | 73.0 | 73.0 | 73.0 |
| `payment:delay=58` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 74.0 | 79.0 | 79.0 | 79.0 |
| `payment:delay=59` | 19 | `completed | COMPLETED | RESERVED | APPROVED` | 19 | 73.0 | 77.0 | 77.0 | 77.0 |
| `payment:delay=60` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 74.0 | 79.0 | 79.0 | 79.0 |
| `payment:delay=61` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 75.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=62` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 76.0 | 80.0 | 80.0 | 80.0 |
| `payment:delay=622` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 637.0 | 637.0 | 637.0 | 637.0 |
| `payment:delay=63` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 78.0 | 83.0 | 83.0 | 83.0 |
| `payment:delay=64` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 79.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=65` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 79.0 | 84.0 | 84.0 | 84.0 |
| `payment:delay=66` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 80.0 | 83.0 | 83.0 | 83.0 |
| `payment:delay=663` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 677.0 | 677.0 | 677.0 | 677.0 |
| `payment:delay=67` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 83.0 | 85.0 | 85.0 | 85.0 |
| `payment:delay=68` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 82.0 | 94.0 | 94.0 | 94.0 |
| `payment:delay=69` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 81.0 | 90.0 | 90.0 | 90.0 |
| `payment:delay=70` | 16 | `completed | COMPLETED | RESERVED | APPROVED` | 16 | 85.0 | 99.0 | 99.0 | 99.0 |
| `payment:delay=71` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 85.0 | 93.0 | 93.0 | 93.0 |
| `payment:delay=72` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 85.0 | 91.0 | 91.0 | 91.0 |
| `payment:delay=73` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 87.0 | 92.0 | 92.0 | 92.0 |
| `payment:delay=74` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 89.0 | 98.0 | 98.0 | 98.0 |
| `payment:delay=75` | 20 | `completed | COMPLETED | RESERVED | APPROVED` | 20 | 90.0 | 94.0 | 95.0 | 95.0 |
| `payment:delay=76` | 13 | `completed | COMPLETED | RESERVED | APPROVED` | 13 | 90.0 | 94.0 | 94.0 | 94.0 |
| `payment:delay=77` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 94.0 | 103.0 | 103.0 | 103.0 |
| `payment:delay=78` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 93.0 | 102.0 | 102.0 | 102.0 |
| `payment:delay=79` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 94.0 | 95.0 | 95.0 | 95.0 |
| `payment:delay=80` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 94.0 | 97.0 | 97.0 | 97.0 |
| `payment:delay=81` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 96.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=82` | 12 | `completed | COMPLETED | RESERVED | APPROVED` | 12 | 97.0 | 100.0 | 100.0 | 100.0 |
| `payment:delay=83` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 98.0 | 107.0 | 107.0 | 107.0 |
| `payment:delay=84` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 100.0 | 105.0 | 105.0 | 105.0 |
| `payment:delay=85` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 100.0 | 112.0 | 112.0 | 112.0 |
| `payment:delay=86` | 7 | `completed | COMPLETED | RESERVED | APPROVED` | 7 | 98.0 | 101.0 | 101.0 | 101.0 |
| `payment:delay=87` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 101.0 | 114.0 | 114.0 | 114.0 |
| `payment:delay=88` | 10 | `completed | COMPLETED | RESERVED | APPROVED` | 10 | 103.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=89` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 104.0 | 116.0 | 116.0 | 116.0 |
| `payment:delay=9` | 1 | `completed | COMPLETED | RESERVED | APPROVED` | 1 | 28.0 | 28.0 | 28.0 | 28.0 |
| `payment:delay=90` | 15 | `completed | COMPLETED | RESERVED | APPROVED` | 15 | 105.0 | 108.0 | 108.0 | 108.0 |
| `payment:delay=91` | 6 | `completed | COMPLETED | RESERVED | APPROVED` | 6 | 107.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=92` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 107.0 | 111.0 | 111.0 | 111.0 |
| `payment:delay=93` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 107.0 | 110.0 | 110.0 | 110.0 |
| `payment:delay=94` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 109.0 | 122.0 | 122.0 | 122.0 |
| `payment:delay=95` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 110.0 | 114.0 | 114.0 | 114.0 |
| `payment:delay=96` | 8 | `completed | COMPLETED | RESERVED | APPROVED` | 8 | 112.0 | 115.0 | 115.0 | 115.0 |
| `payment:delay=97` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 112.0 | 114.0 | 114.0 | 114.0 |
| `payment:delay=98` | 11 | `completed | COMPLETED | RESERVED | APPROVED` | 11 | 113.0 | 117.0 | 117.0 | 117.0 |
| `payment:delay=99` | 9 | `completed | COMPLETED | RESERVED | APPROVED` | 9 | 114.0 | 117.0 | 117.0 | 117.0 |

## 처리 시간 (k6가 잰 요청마다의 경과, ms)

| 묶음 | 건수 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| all | 1200 | 94.0 | 241.0 | 384.0 | 677.0 |
| completed | 1200 | 94.0 | 241.0 | 384.0 | 677.0 |
| failed | 0 | - | - | - | - |
| unknown | 0 | - | - | - | - |
