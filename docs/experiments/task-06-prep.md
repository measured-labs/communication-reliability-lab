# task 06 준비 — 실험 조건의 기대 파일 후보 (R04-9)

> 리뷰 지적 R04-9: 실험 시나리오(task 02~05의 실험 실행)에는 기대 파일이 없어, "넣은 섞음과 규칙이 정한 값"과의 대조를 사람이 한다. 이 문서는 **섞음(`load/chain.js`)과 서비스의 규칙만으로 정해지는 건수**를 조건마다 고른 목록과 안이다.
> 이번에는 목록과 안만 적는다. **기대 파일은 서버 배치가 정해진 뒤 만든다**(7절). 보정(`cal-*`·`calibration`·`duplicate`)은 이미 기대 파일이 있어 대상이 아니다.
> 기대 파일의 꼴은 기존 분석기의 `--expect` 형식을 그대로 쓴다(0절). 분석기·실행 스크립트는 바꾸지 않는다는 전제다(7절에서 바꿔야 하는 곳 하나를 적는다).
> 실제 값은 커밋된 보고(`runs/<실행>/report.json`, `metrics*.tsv`)에서 읽었다 — 대상 75개 조건(조건 × 시나리오), 잰 실행 241개(버리는 실행 `r0`과 색인에 "대체됨"인 실행은 뺐다). 241개 모두 종료 코드 0이다. 원시 묶음(`raw.tar.gz`)은 풀지 않았다.

## 0. 기대 파일의 꼴 — 분석마다 받는 것

| 분석 | 기대 파일의 꼴(정본) | 견주는 키 | 넘기는 환경 변수(`scripts/run.sh`) | 주지 않으면 |
|---|---|---|---|---|
| 대조 분석 | `docs/measurement.md` 5-2 — 키 가운데 적은 것만 정확히 견준다 | `answers` · `lostTotal` · `doubledTotal` · `layerMismatch` · `amountMismatch` · `orphanRows` · `pendingRows`(정수) · `faultCountsMatch`(불) · `labels`(라벨 → 서명 → requestId 수, **꼴 전체**를 견준다 — 적지 않은 서명은 0으로 본다) | `RECON_EXPECT`(121·453줄) | `load/expectations/<시나리오>.json`이 있으면 그것(456줄) |
| `timing` | 7-4 — `{"<metrics.tsv의 이름>": {"min": 수, "max": 수}}` | `metrics.tsv`의 이름(7-3) | `TIMING_EXPECT`(124줄) | `<시나리오>.timing.json`이 있으면 그것. `TIMING=yes`인 시나리오(task 02의 것 — `false-timeout`·`slow-layer`·`split-timeout`)만 돈다 |
| `retry` | 8-3 → 7-4와 같은 꼴 | `metrics-retry.tsv`의 이름(8-4) | `RETRY_EXPECT`(114줄) | `<시나리오>.retry.json`이 있으면 그것. `RETRY=yes`인 시나리오(task 03, `idem-retry-mix`, task 04-b)만 |
| `idem` | 9-3 → 7-4와 같은 꼴 | `metrics-idem.tsv`의 이름(9-4) | `IDEM_EXPECT`(122줄) | 없음(기본 파일을 찾지 않는다) |
| `overload` | 10-4 → 7-4와 같은 꼴 | `metrics-overload.tsv`의 이름(10-3) | `OVERLOAD_EXPECT`(95줄) | `<시나리오>.overload.json`이 있으면 그것 |
| `transport` | 11-3 → 7-4와 같은 꼴 | `metrics-transport.tsv`의 이름(11-3 — 차이 줄의 이름이 아니라 보고의 키) | `TRANSPORT_EXPECT`(85줄) | 없음 |

- 하위 명령의 기대는 범위다. 정해지는 값은 `min = max`로 적는다. 하한만 정해지는 값은 `min`만 정하고 `max`를 그 실행의 상한(보낸 수 등)으로 둔다 — 아래 표에 "하한"으로 따로 적었다.
- 대조 분석에는 판정마다의 수(완료·실패·결과 모름)와 층마다의 처리 행 수의 키가 없다. 그것은 `labels`로만 견준다 — 그래서 **어느 한 서명이라도 정해지지 않으면 그 조건의 `labels`는 쓸 수 없다**(꼴 전체를 견주므로). 그런 조건은 하위 명령의 `clients.*`·`layers.*` 키로 대신한다.
- 늘 견주는 것은 기대 파일 없이도 분석기가 본다 — 아래 표에서 뺐다: 대조 분석의 `orphanRows`·`pendingRows`·`amountMismatch`(5-3), `timing`의 표본 간격(7-4), `retry`의 `clients.notReachedServer`·넣은 실패의 대조·`attemptsNotArrived`(8-5), `idem`의 끊긴 수 대조·키 행과 업무 행의 짝(9-5), `overload`의 구간 앞 거절·`otherStatus`·구간 안 거절 > 0(10-4), `transport`의 ①~⑦(11-3).

## 1. "정해진다"의 기준

- 값이 섞음과 규칙(타임아웃·재시도 횟수와 간격·멱등 키·한도·기한)에서 **유도될 때만** 정해진다고 쓴다. 지난 실행에서 반복마다 같았다는 것은 근거로 쓰지 않는다(같았지만 정해지지 않는 값이 여럿 있다 — 각 절의 "정해지지 않음").
- **타임아웃과 넣은 시간을 견주는 것**: 넣은 시간(지연·재시도 간격의 합)이 타임아웃보다 길어 "끊긴다"를 말하는 쪽은, 처리·전달 몫이 늘어도 방향이 그대로라 여유가 작아도 정해진다. "끊기지 않는다"를 말하는 쪽은 처리·전달 몫이 여유보다 작아야 한다 — 이 문서는 **여유 100ms 이상**만 정해진다고 둔다(보정의 기대가 쓴 여유는 300~500ms). 서버를 나누면 층마다 전달 몫이 늘 수 있다(8절).
- **경합**(다른 호출·동시 요청 사이의 차례)에 기대는 값은 정해지지 않는다. 다만 어느 차례든 같은 값이 나오는 것은 정해진다 — 그때는 유도에 두 차례를 모두 적는다.
- **자원**(스레드·큐가 차는 것, 미끄러지는 창 안의 정확한 도착 시각)에 기대는 값은 정해지지 않는다.
- 분석기가 정답에서 다시 세는 값(`answers`, `spike.sent`, `injectedCalleeDelayAtLeastTimeout`, `mix.<라벨>.count`, `slow.slowRequests`)은 서버와 무관하다 — 부하 도구가 섞음대로 보냈는지의 검사로만 뜻이 있다. 표에 "(정답에서 셈)"으로 적는다.

## 2. task 02 — 타임아웃 (`runs/task-02-index.tsv`)

섞음: `false-timeout` = 결제 지연 로그정규(`load/chain.js` 58~82줄, 씨앗 고정), `slow-layer` = 요청 600~1799에 `payment:delay=5000`(84~87·295줄), `split-timeout` = `[없음, 500, 1500, 2500][i % 4]`(90·296줄). 부하: `scripts/run.sh` 42~45줄. 재시도 없음, requestId 유일(`load/chain.js` 293줄).

**모든 조건에 공통**(대조 분석): `lostTotal` 0 · `doubledTotal` 0 · `faultCountsMatch` true. 유도: 재시도가 없고 requestId가 겹치지 않아 한 층에 처리 행이 둘일 수 없다(`docs/measurement.md` 0절). 주문은 재고가 201일 때만 `COMPLETED`·201이고 재고는 결제가 201일 때만 `RESERVED`라, 클라이언트가 완료인데 처리 행이 없는 층이 생기지 않는다. 지시는 요청마다 결제 하나이고 모든 요청이 결제에 닿는다(결제는 위가 끊어도 멈추지 않는다 — HTTP). 지난 실행: 30개 모두 같은 값.

| 조건 | 정해지는 지표 = 값 | 근거 | 지난 실행 |
|---|---|---|---|
| ga-t80 · ga-t173 · ga-t323 | 대조 `answers` 1200 · timing `edges.inventory->payment.calls` 1200 · `edges.order->inventory.timeouts` 0 · `waste.order.wasted` 0 · `waste.inventory.wasted` 0 · `clients.noResponse` 0 · `edges.inventory->payment.injectedCalleeDelayAtLeastTimeout` 592 / 125 / 19(정답에서 셈) · **하한**: `edges.inventory->payment.timeouts`·`falseTimeouts` ≥ 592 / 125 / 19(max 1200) | 주문→재고 5000ms인데 재고는 늦어도 T(≤ 323ms) + 처리에 답한다 → 주문은 끊지 않고 위가 포기하지 않으니 주문·재고의 헛일 0. 주문은 늘 5s 안에 답해 k6(10s)가 응답을 못 받는 일이 없다. 넣은 지연 ≥ T면 결제의 응답은 T보다 늦어 반드시 끊긴다(방향이 안전한 쪽 — 하한), 결제는 끊겨도 처리하므로 거짓 타임아웃 = 타임아웃. 592·125·19는 같은 함수를 다시 돌려 센 수와 같다 | `report.json`·`metrics.tsv` r1~r3 같음. 타임아웃은 682/678/689, 137/136/137, 21/21/21 |
| ga-t2000 | 위의 공통 + `edges.inventory->payment.timeouts` 0 · `falseTimeouts` 0 · `belowDelay.timeouts` 0 · `waste.payment.wasted` 0 · `injectedCalleeDelayAtLeastTimeout` 0 · 대조 `layerMismatch` 0 | 1200개 지연의 최대 663ms(씨앗으로 다시 셈) — 2000ms와의 여유 1337ms | r1~r3 같음 |
| da-inside(다-1: 2000/1000) | 대조 `answers` 800 · `layerMismatch` 400 · `labels` 전체: `""`·`payment:delay=500` → `completed \| COMPLETED \| RESERVED \| APPROVED` 각 200, `payment:delay=1500`·`2500` → `unknown \| UNKNOWN \| UNKNOWN \| APPROVED` 각 200 · timing `edges.inventory->payment.timeouts` 400 · `falseTimeouts` 400 · `injectedCalleeDelayAtLeastTimeout` 400 · `belowDelay.timeouts` 0 · `edges.order->inventory.timeouts` 0 · `waste.payment.wasted` 400 · `wastedProcessed` 400 · `waste.inventory.wasted` 0 · `waste.order.wasted` 0 · `waste.<L>.startedAfterGiveUp` 0(셋) · `clients.noResponse` 0 | 지연과 타임아웃의 여유가 모두 500ms(500 < 1000, 1500·2500 > 1000). 재고는 1000에 포기하고 504 → 주문은 2000 안에 받아 `UNKNOWN`, 결제는 깨어나 커밋 → 헛일·처리 행. 재고가 늦어도 ≈ 1000에 답해 주문의 2000과 여유 1000 | r1~r3 같음 |
| da-longer(다-2: 1000/2000) | 대조 `answers` 800 · `layerMismatch` 400 · `labels` 전체: `""`·`500` → `completed \| COMPLETED \| RESERVED \| APPROVED` 각 200, `1500` → `unknown \| UNKNOWN \| RESERVED \| APPROVED` 200, `2500` → `unknown \| UNKNOWN \| UNKNOWN \| APPROVED` 200 · timing `edges.order->inventory.timeouts` 400 · `falseTimeouts` 200 · `belowDelay.timeouts` 400 · `edges.inventory->payment.timeouts` 200 · `falseTimeouts` 200 · `injectedCalleeDelayAtLeastTimeout` 200 · `waste.inventory.wasted` 400 · `wastedProcessed` 200 · `waste.payment.wasted` 400 · `wastedProcessed` 400 · `waste.order.wasted` 0 · `startedAfterGiveUp` 0(셋) · `clients.noResponse` 0 | 주문이 1000에 포기(1500·2500 모두 여유 500). 재고는 1500짜리를 2000 안에 받아 `RESERVED`(여유 500), 2500짜리는 2000에 포기(여유 500). 넣은 지연은 결제에만 있어 주문→재고의 `belowDelay`는 끊긴 400 전부 | r1~r3 같음 |
| na-none-platform · na-none-virtual | 대조 `answers` 3000 · timing `slow.slowRequests` 1200(정답에서 셈) · `slow.slowChain.count` 1200 · `edges.order->inventory.calls` 3000 | 섞음의 몫(요청 600~1799). 그 밖은 아래 "정해지지 않음" | r1~r3 같음 |
| na-split-platform · na-split-virtual | 위 셋 + `slow.slowChain.unknown` 1200 · `slow.slowChain.completed` 0 · `edges.inventory->payment.injectedCalleeDelayAtLeastTimeout` 1200 · `edges.order->inventory.timeouts` 0 · `clients.noResponse` 0 · **하한** `edges.inventory->payment.timeouts` ≥ 1200 | 느린 요청은 5000 > 재고의 1000 → 반드시 끊기고(큐에서 늦어져도 방향이 같다) 재고 504 → 주문 `UNKNOWN`. 주문은 요청 하나를 많아야 ≈ 2000ms 쥐어(재고가 1000에 답함) 60/s × 2s = 120 < 200 스레드 — 주문은 차지 않아 k6(20s)는 늘 응답을 받는다 | r1~r3 같음. split-platform의 타임아웃은 1774/1771/1769(정상 요청까지 끊김), split-virtual은 1200 |

**정해지지 않음(넣으면 안 된다)**:
- ga-t80·t173·t323의 `timeouts`·`falseTimeouts`·`layerMismatch`·`labels`·`waste.payment.wasted`: 지연이 T 가까이(±30ms)인 요청이 끊기는지는 결제의 처리 몫에 달렸다(t323도 경계에 12건). t323이 세 번 21이었던 것은 근거가 아니다. ga-t80의 `waste.payment.startedAfterGiveUp` 0도 여유가 80ms라 넣지 않는다.
- na의 정상 요청의 판정(`labels`의 `""`, `slow.normalChain.*`)·`timeouts` 정확한 수·회복 시간·표본(`samples.*`)·조회(`slow.lookups.*`): 실험 나가 재는 것 자체가 스레드·큐가 차는 정도다. split-virtual의 정상 1800 완료도 자원에 기댄다. `slow.normalChain.before/after.count`(600/1200)는 보낸 시각의 차례가 요청 번호와 같다는 데 기대어 넣지 않는다.
- na-none의 `clients.noResponse`·`timeouts`(60000ms가 걸리는지는 큐의 길이), 모든 조건의 분위수·`wastedMsSum`·`callMs`·`serviceMs`.

## 3. task 03 — 재시도 (`runs/task-03-index.tsv`)

섞음: `retry-mix` = u(씨앗 20261125)로 고른 일곱 칸(`load/chain.js` 94~111줄 — 800건에서 정상 393, `fail-first=1` 165, `=2` 76, `=3` 40, `=4` 47, `=9` 36, `slow-first=1@1500` 43 — `docs/experiments/task-03-design.md` 2절, 분석기의 `mix.<라벨>.count`와 같다), `retry-outage-burst` = 500ms마다 20건 + 결제 구간 장애 [10s, 20s)(113~118·425~428줄), `retry-budget` = 고른 40/s + [10s, 30s). 재시도 규칙 `docs/retry.md` 2~4절(최대 시도는 처음을 센다, 간격 식, 예산). `X-Fault`가 없어 모든 조건에서 `faultCountsMatch` true(넣은 0 = 센 0), 장애가 처리 전 500이라 `lostTotal` 0.

| 조건 | 정해지는 지표 = 값 | 근거(유도) | 지난 실행 |
|---|---|---|---|
| ga-max1 / max2 / max3 / max5 | 대조 `doubledTotal` 0/43/43/43 · `layerMismatch` 43 · `labels` 전체(max1: `completed \| COMPLETED \| RESERVED \| APPROVED` 393, `failed \| FAILED \| FAILED \| -` 364, `unknown \| UNKNOWN \| UNKNOWN \| APPROVED` 43 / max2·3·5: `… APPROVED` 558·634·721, `… APPROVED+APPROVED` 43, `failed …` 199·123·36) · retry `clients.completed` 393/601/677/764 · `clients.failed` 364/199/123/36 · `clients.unknown` 43/0/0/0 · `layers.payment.arrivals` 800/1207/1406/1612 · `layers.inventory.arrivals` 800 · `edges.inventory->payment.retries` 0/407/606/812 · `attemptFaults.payment.fail-first.counted` 364/563/686/805 · `slow-first.counted` 43 · `mix.<라벨>.completed`·`paymentArrivals` | 시도가 m이면 `fail-first=n`은 n < m일 때 성공하고 결제에 min(n+1, m)번 든다. `slow-first`: 첫 시도 1500 > 재고 1000 → 재시도(m ≥ 2)가 처리하고 첫 시도도 1500에 커밋 → 결제 2행(여유 500). 주문→재고 5000이 가장 긴 재시도(≈ 1.5s)를 품는다(여유 3.5s) | 모든 칸 r1~r3 같음 |
| na-none · na-exponential · na-fixed500 | 대조 `labels` 전체 · `doubledTotal` 0 · `layerMismatch` 0 · retry `clients.completed` 800/820/860 · `clients.failed` 400/380/340 · `layers.payment.arrivals` 2400/2400/2340 · `edges.inventory->payment.retries` 1200/1200/1140 · `outage.requestsSentDuring` 400 · `outage.completedSentDuring` 0/20/60 · `outage.calleeArrivalsDuring` = `attemptFaults.payment.outage.counted` 1600/1580/1480 | 무리 k는 시작 + (k+1)·500ms에 보낸다 → 무리 19~38(400건)의 첫 시도가 구간 안(무리 19는 정확히 10000에 보내고 결제에는 그 뒤에 든다). 구간 끝 쪽 무리의 시도 n은 "보낸 때 + 앞 간격의 합" **이후**에 든다: none은 네 시도가 ≈ 60ms 안 → 모두 실패(여유 440). exponential(100·200·400)은 무리 38의 넷째가 19500 + 700 이후 → 성공, 셋째는 ≈ 19850(여유 150), 무리 37의 넷째 ≈ 19750(여유 250). fixed500은 무리 36·37·38의 넷째·셋째·둘째가 간격만으로 20000 이후 → 성공(방향 안전), 무리 35의 넷째 ≈ 19540 | r1~r3 같음 |
| na-fixed | 대조 `doubledTotal` 0 · retry `clients.completed` 820 · `clients.failed` 380 · `outage.requestsSentDuring` 400 · `outage.completedSentDuring` 20 | 무리 38의 넷째(19500 + 600 이후)가 성공, 무리 37의 넷째 ≈ 19640(여유 360) | r1~r3 같음 |
| na-jitter | 대조 `doubledTotal` 0 · retry `layers.payment.arrivals` 2400 · `edges.inventory->payment.retries` 1200 · `outage.requestsSentDuring` 400 · **하한·상한** `clients.completed` 800~820 | 간격은 [0, 100)·[0, 200)·[0, 400): 무리 38의 셋째는 늦어도 ≈ 19850, 무리 37의 넷째는 늦어도 ≈ 19750 → 구간 안 요청은 넷째까지 모두 시도한다. 무리 38만 넷째가 구간 뒤일 수 있다 | arrivals r1~r3 같음, 완료 804/802/805 |
| da-1L · da-2 · da-3L | 대조 `doubledTotal` 43 · `layerMismatch` 43 · `labels` 전체(da-1L = ga-max3과 같다. da-2: `COMPLETED \| RESERVED \| APPROVED` 393, `FAILED+RESERVED` 165, `FAILED+FAILED+RESERVED` 76, `RESERVED+UNKNOWN \| APPROVED+APPROVED` 43, `failed \| FAILED \| FAILED+FAILED+FAILED \| -` 123. da-3L: 634 · `FAILED+RESERVED` 87 · `APPROVED+APPROVED` 43 · `failed … FAILED+FAILED+FAILED` 36) · retry `clients.completed` 677/677/764 · `clients.failed` 123/123/36 · `layers.payment.arrivals` 1406/1406/1756 · `layers.inventory.arrivals` 800/1406/959 · 두 구간의 `retries`·`decisions.*` · `attemptFaults.payment.fail-first.counted` 686/686/949 | 주문→재고 3000이 재고의 재시도(≈ 1.1s)를 품어 층의 호출이 차례로만 돈다(경합 없음). 주문만 재시도(da-2)면 재고가 결제를 한 번씩 불러 `fail-first`의 든 횟수가 주문의 시도마다 1 는다. 둘 다(da-3L)면 재고가 502를 낸 요청(=3·=4·=9)에 주문이 다시 불러 결제의 든 횟수가 이어진다 → =3·=4는 둘째 주문 시도에서 산다, =9는 3 × 3 = 9번 | r1~r3 같음 |
| da-1S | 대조 `doubledTotal` 43 · `layerMismatch` 119 · `labels` 전체(`completed …` 558, `unknown \| UNKNOWN \| FAILED \| -` 123, `unknown \| UNKNOWN \| RESERVED \| APPROVED` 76, `… APPROVED+APPROVED` 43) · retry `clients.completed` 558 · `clients.unknown` 242 · `layers.payment.arrivals` 1406 · `layers.inventory.processedNotCompleted` 119 · `layers.payment.processedNotCompleted` 162 | 주문 250ms. 실패 둘 이상이면 재고의 셋째 시도가 간격만으로 300ms 뒤 → 주문은 반드시 먼저 포기(방향 안전). 실패 하나는 ≈ 115ms에 끝나 여유 ≈ 135. 재고·결제는 주문이 포기해도 끝까지 간다 | r1~r3 같음 |
| da-3S | retry `clients.completed` 764 · `clients.unknown` 36 · `clients.failed` 0 · `layers.order.arrivals` 800 · `mix.<라벨>.*`(`none`·`fail-first=1`·`=2`·`=4`·`=9`·`slow-first`의 `completed`·`unknown`·`paymentArrivals`·`inventoryArrivals`) | 아래 "정해지지 않음"의 `fail-first=3` 40건만 경합이다. =2는 두 차례 모두 결제 2행·재고 2행, =4는 두 차례 모두 결제 5번·재고 `FAILED+RESERVED`, =9는 재고 호출마다 간격만으로 300ms > 250이라 주문 시도 셋이 모두 끊긴다(결제 9번, 결과 모름), `slow-first`는 주문의 둘째 시도(≈ 350)와 재고의 둘째 시도(≈ 1100)의 여유가 750 | r1~r3 같음 |
| ra-off | 대조 `doubledTotal` 0 · `layerMismatch` 0 · retry `layers.order.arrivals`·`layers.inventory.arrivals` 1600 · `edges.inventory->payment.calls` 1600 · `budgetDenied` 0 | 예산 꺼짐. 시도가 실패하면 처리 행이 없다 | r1~r3 같음 |
| ra-on | 위의 대조 둘 · retry `edges.inventory->payment.calls` 1600 · **상한** `edges.inventory->payment.retries` ≤ 170 | 재시도 ≤ K + r × 호출 = 10 + 0.1 × 1600(`docs/retry.md` 4절). 실제 89 | r1~r3 같음(89) |

**정해지지 않음**:
- **da-3S의 `fail-first=3` 40건**(그래서 `doubledTotal`·`labels`·`layers.*.arrivals`·`processedRows`·`extraProcessedRows`·`mix.payment:fail-first=3.*`): 재고의 첫 호출의 셋째 시도(≈ 330ms에 결제에 듦)와 주문의 둘째 시도가 부른 재고 호출(≈ 360ms에 듦)이 결제의 든 횟수를 다툰다. 지금 차례(첫 호출이 먼저)면 첫 호출 `FAILED`·둘째 호출 `RESERVED`, 거꾸로면 첫 호출의 셋째 시도가 넷째 듦으로 처리되어 둘 다 처리된다(두 번 처리됨 +40). 여유 ≈ 20~30ms — 결과 문서(`docs/results/task-03-retries.md` 146줄)는 이 조건의 119를 규칙이 정한 값으로 읽었다.
- na-fixed의 `layers.payment.arrivals`·`retries`·`outage.calleeArrivalsDuring`·`outage.counted`: 무리 38의 셋째 시도가 19500 + 400 + 처리 몫(≈ 50ms)에 들어 구간 끝과의 여유가 ≈ 50ms다. 셋째가 구간 뒤면 2400 → 2380.
- na-jitter의 `clients.completed`·`calleeArrivalsDuring`(무작위 간격 — 범위만), 모든 na의 칸 최대(`peakPer50ms`·`retriesPeakPer50ms`·`retryBinsDuring`)·`retryTailMs`·`clientRecoveryMs`(칸 경계와 ms 시각).
- ra-off의 `clients.completed`(828)·`layers.payment.arrivals`(3983/3984)·`outage.completedSentDuring`: 고르게 오는 요청의 재시도가 구간 끝을 넘는지는 25ms 간격과 처리 몫의 견줌. ra-on의 `retries`(89)·`budgetDenied`·`clients.completed`(토큰이 찬 시각과 실패의 차례). ra 둘의 `outage.requestsSentDuring`(800)은 경계의 요청(i = 400·1200)이 예정 시각보다 먼저 나가지 않는다는 데 기대어 넣지 않는다.
- 모든 조건의 지연 분위수·`gapMs`·`delayMs`(지터)·`elapsedMs`.

## 4. task 04 — 멱등 키 (`runs/task-04-index.tsv`)

섞음: `idem-retry-mix` = task 03 `retry-mix`의 칸에서 u ∈ [0.40, 0.50)를 `payment:drop-after-commit=1`로(`load/chain.js` 166~170줄 — 정상 314, 끊김 79, 나머지는 task 03과 같다), `idem-dup-seq`·`idem-dup-conc` = 같은 요청을 잇달아·동시에 두 번(163·164·340~355줄), `idem-plain` = 장애 없음, `idem-reconcile` = 칸 i % 10(151~158줄) + 맞추기 단계. 키 규칙 `docs/idempotency.md` 3절(다시 온 키: 금액 다름 422 · `COMPLETED`/`FAILED` 저장한 응답 · `UNKNOWN` 이어 하기 · `IN_PROGRESS` 409), 3-1절(409를 결과 모름으로). 모든 조건에서 `faultCountsMatch` true, **키를 켠 조건은 `doubledTotal` 0이 차례와 무관하게 정해진다**(키 행이 유일하고 업무 행과 같은 트랜잭션 — 진 쪽은 되돌린다).

| 조건 | 정해지는 지표 = 값 | 근거(유도) | 지난 실행 |
|---|---|---|---|
| ga-1-off | 대조 `doubledTotal` 122 · `layerMismatch` 122 · `labels` 전체(`… APPROVED` 555, `… APPROVED+APPROVED` 122, `failed \| FAILED \| FAILED \| -` 123) · retry `clients.completed` 677 · `clients.failed` 123 · `layers.payment.arrivals` 1485 · `edges.inventory->payment.retries` 685 · idem `dropFaults.payment.counted` 79 · `layers.payment.extraProcessedRows` 122 | task 03 da-1L에 끊김 79를 더한 것. 끊긴 결제는 커밋한 뒤라 재고가 `Unknown:IO_…`로 재시도하면 다시 처리된다(끊김은 첫 듦만) → 122 = 79 + `slow-first` 43. 재시도 685 = 165 + 76·2 + 123·2 + 43 + 79 | r1~r3 같음 |
| ga-1-on | 대조 `doubledTotal` 0 · `layerMismatch` 0 · `labels` 전체(`… APPROVED` 677, `failed …` 123) · retry 위와 같은 `clients.*`·`arrivals`·`retries` · idem `layers.payment.counts.replayed` 122 · `layers.payment.counts.new` 677 · `layers.<order/inventory>.keyRows` 800 · `layers.payment.keyRows` 677 · `layers.<order/inventory>.keyStates.COMPLETED` 677 · `keyStates.FAILED` 123 · `dropFaults.payment.counted` 79 | 같은 재시도가 결제에서 저장한 응답을 받는다. `slow-first`는 둘째 시도(≈ 1100)가 먼저 커밋하고 1500에 깬 첫 시도가 저장한 응답(여유 400). 결제는 실패한 시도에 키를 남기지 않는다 | r1~r3 같음 |
| ga-2-off | retry `clients.completed` 764 · `clients.unknown` 36 · `clients.failed` 0 · idem `dropFaults.payment.counted` 79 · `mix.<라벨>.*`(`fail-first=3` 밖) | da-3S와 같은 유도(끊김 79는 재고의 둘째 시도로 ≈ 110ms에 끝나 주문 250 안) | r1~r3 같음 |
| ga-2-on | 대조 `doubledTotal` 0 · retry `layers.payment.arrivals` 1485 · `edges.inventory->payment.retries` 685 · idem `layers.payment.processedRows` 677 · `layers.payment.keyRows` 677 · `layers.payment.counts.replayed` 122 · `layers.<order/inventory>.counts.new` 800 · `layers.inventory.keyStates.COMPLETED` 677 · `FAILED` 123 · `dropFaults.payment.counted` 79 | 재고는 키마다 결제를 한 묶음(최대 3시도)으로만 부른다 — 주문의 재시도는 재고에서 409나 저장한 응답으로 끝나 결제·재고의 수는 주문의 차례와 무관하다 | r1~r3 같음 |
| ga-2-on-409u | 대조 `doubledTotal` 0 · `layerMismatch` 43 · `labels` 전체(`completed … APPROVED` 634, `failed \| FAILED \| FAILED \| -` 123, `unknown \| UNKNOWN \| RESERVED \| APPROVED` 43) · idem `clients.status.201` 634 · `502` 123 · `504` 43 · `layers.order.keyStates.COMPLETED` 634 · `FAILED` 123 · `UNKNOWN` 43 · `layers.payment.processedClientUnknown` 43 · ga-2-on의 결제·재고 쪽 값 | 주문의 둘째 시도(≈ 350)가 409를 받든 저장한 응답을 받든 셋째 시도(≈ 550 이후)에서 재고는 이미 끝나 있다(`fail-first=2`는 ≈ 330에 끝남 — 여유 220) → 판정이 같다. `slow-first`는 재고가 ≈ 1100에 끝나 주문의 세 시도(≈ 555까지)가 모두 409 → 504(여유 545) | r1~r3 같음 |
| na-seq-off | 대조 `doubledTotal` 400 · `labels` 전체(`completed+completed \| COMPLETED+COMPLETED \| RESERVED+RESERVED \| APPROVED+APPROVED` 400) · idem `clients.status.201` 800 · `duplicates.pairs.201+201` 400 · `duplicates.doubled` 400 · `layers.payment.arrivals` 800 | 키가 없고 유일 제약이 없다 — 두 번 모두 처리 | r1~r3 같음 |
| na-seq-on | 대조 `doubledTotal` 0 · `labels` 전체(`completed+completed \| COMPLETED \| RESERVED \| APPROVED` 400) · idem `clients.replayed` 400 · `clients.status.201` 800 · `layers.order.counts.replayed` 400 · `layers.payment.arrivals` 400 · `keyRows` 400(셋) | 둘째는 첫째의 응답을 받은 뒤라 주문이 저장한 응답을 돌려준다 | r1~r3 같음 |
| na-conc-off | na-seq-off와 같은 값 | 동시여도 키가 없으면 둘 다 처리 | r1~r3 같음(`laterArrivalStatus`는 다름) |
| na-conc-on | 대조 `doubledTotal` 0 · idem `layers.payment.arrivals` 400 · `keyRows` 400(셋) · `duplicates.requestIds` 400 · `duplicates.doubled` 0 | 둘째는 주문에서 409나 저장한 응답으로 끝나 재고로 가지 않는다 — 어느 쪽이든 행은 하나 | r1~r3 같음 |
| ra-off | 대조 `doubledTotal` 400 · `layerMismatch` 320 · `labels` 전체(`completed …` 400, `unknown \| COMPLETED+COMPLETED \| RESERVED+RESERVED \| APPROVED+APPROVED` 80, `unknown \| COMPLETED+UNKNOWN \| RESERVED+RESERVED \| APPROVED+APPROVED` 160, `unknown \| COMPLETED+UNKNOWN \| RESERVED+UNKNOWN \| APPROVED+APPROVED` 160) · idem `clients.status.201` 400 · `504` 320 · `0` 80 · `dropFaults.<셋>.counted` 80 · `reconcile.candidates` 400 · `resent` 400 · `completed` 400 · `finalDoubled` 400 · `finalOnce` 0 · `layers.payment.arrivals` 1200 | 칸 5·9는 지연 2500 > 2000, 1500 > 1000(여유 500), 6·7·8은 끊김. 결과 모름 400을 가라앉은 뒤 다시 보내면 키가 없어 모든 층이 새로 처리 | r1~r3 같음 |
| ra-on | 대조 `doubledTotal` 0 · `layerMismatch` 0 · `labels` 전체(`completed …` 400, `unknown \| COMPLETED \| RESERVED \| APPROVED` 400) · idem `reconcile.resent` 400 · `completed` 400 · `replayed` 80 · `finalOnce` 400 · `finalDoubled` 0 · `layers.payment.arrivals` 960 · `keyRows` 800(셋) · `layers.<셋>.processedClientUnknown` 400 | 다시 보낸 400: 칸 6(주문 끊김)은 주문이 저장한 응답(80), 나머지 320은 이어 하기. 결제까지 다시 가는 것은 결제의 결과를 재고가 모른 칸 8·9(160) → 960 | r1~r3 같음 |
| da-off · da-on | 대조 `answers` 1200 · `doubledTotal` 0 · `labels` 전체(`completed …` 1200) · idem `clients.status.201` 1200 · `keyRows` 0 / 1200(셋) · `layers.payment.arrivals` 1200 | 장애 없음, 넣은 지연 없음(타임아웃 2000·1000과 처리 몫 수 ms) | r1~r6 같음 |

**정해지지 않음**:
- ga-2-off의 `fail-first=3` 몫(da-3S와 같은 경합) — 그래서 `doubledTotal` 198·`labels`·`layers.payment.arrivals` 1954·`extraProcessedRows`. 결과 문서(`docs/results/task-04-idempotency.md` 67줄)는 가-2 끔을 규칙이 정한 값으로 읽었다.
- ga-2-on의 판정(634/634/632)·`layerMismatch`·주문→재고 `retries`·재고의 409·`processedClientFailed`(43/43/45): `fail-first=2`의 재고 끝(≈ 330)과 주문의 둘째 시도(≈ 350)의 경합(설계 5절이 이미 적음).
- ga-2-on-409u의 주문→재고 `retries`(408/409)·재고의 `arrivals`·`counts.in-progress`·`responses.409`·`counts.replayed`: 같은 경합이 시도 수만 바꾼다(판정은 위처럼 같다).
- na-conc-on의 `duplicates.pairs.*`·`clients.status.409`·`clients.replayed`(399~400): 두 요청이 주문에 드는 차와 사슬을 도는 시간의 견줌. na-conc의 `laterArrivalStatus`·`arrivalTies`.
- da의 `selfMs`·`serviceMs`·`elapsedMs`(실험 다가 재는 것).

## 5. task 04-b — 한도 (`runs/task-04b-index.tsv`)

섞음: 장애 헤더 없음. 평소 `RATE` × 35s + 10s부터 5s 동안 +75/s(`scripts/run.sh` 65~68줄 — `overload-spike` 25/s, `overload-low`·`overload-burst` 15/s(burst는 2s마다 30건 — `load/chain.js` 203~205·408~411줄), `overload-edge` 17/s). 결제의 한도 50/1000ms 미끄러지는 창(`docs/measurement.md` 10-1).

**모든 조건(22)에 공통**: 대조 `answers` 1250 / 900 / 970 / 900(spike·low·edge·burst) · `doubledTotal` 0 · `lostTotal` 0 · `faultCountsMatch` true · overload `spike.sent` 375 · `spike.baseSent` 875 / 525 / 595 / 525(정답에서 셈) · `clients.unknown` 0 · (예산이 꺼진 조건) retry `edges.inventory->payment.budgetDenied` 0. 유도: 거절은 처리 전 503이고 재시도는 그 503에만 일어나 처리 행이 둘일 수 없다. 넣은 지연이 없어 타임아웃(5000·1000ms)이 울릴 일이 없다. 지난 실행: 76개 모두 같은 값.

| 조건 | 정해지는 지표 = 값 | 근거 | 지난 실행 |
|---|---|---|---|
| ga-off(재시도 없음) | 공통 + overload `payment.arrivals` 1250 | 요청마다 결제에 한 번 | r1~r3 같음 |
| pl-off(재시도 없음, 세 시나리오) | 공통 + `payment.arrivals` 900 / 970 / 900 | 같음 | 같음 |
| 그 밖(ga-none·fixed·exp·jitter, na-on·off, pl-none·fixed·exp·jitter) | 공통만 | — | — |

- ga-off는 보정 `cal-overload`와 부하·설정이 같다(`load/chain.js`에서 두 시나리오의 섞음이 같고 `scripts/run.sh` 65~68줄의 부하 모양도 같다, 조건 값도 같다 — `runs/task-05-recheck-index.tsv`의 `cal-overload` 줄). 그래서 정의에서 손으로 계산한 범위(`load/expectations/cal-overload.overload.json` — 거절 470~500 등)를 그대로 견줄 수 있다. 지난 실행의 ga-off는 거절 483.

**정해지지 않음**: 거절 수(`payment.rejected*`)·완료/실패 수·재시도 조건의 `payment.arrivals`·`retries`·`budgetDenied`(na-on)·회복 시간(`*.recoveryMs`·`rejectionsStopMs`)·`tieGroups`·`maxWindowCount`·`inventory.callOutcomes.*`. 미끄러지는 창 안의 수가 μs 단위 도착 시각과 재시도 간격에 달렸다. 여러 조건이 반복마다 같았지만(ga-fixed·exp·jitter·na-off 모두 284/966, pl-*-burst 485/415) 유도할 수 있는 값이 아니고, pl-*-edge는 반복 사이에 크게 흔들렸다(pl-exp 완료 215~402).

## 6. task 05 — gRPC 기한·취소 (`runs/task-05-index.tsv`)

섞음: task 02 `split-timeout`과 같다. 조건 규칙 `docs/experiments/task-05-design.md` 2·3절(G1 = 기한을 넘기지 않음·구간마다 자기 값, G2 = 받은 기한·취소를 넘김, G3 = 넘김 + 처리 코드가 지연 중 취소를 봄, G1o = 넘기지 않음 + 봄).

**모든 조건(14)에 공통**: 대조 `answers` 800 · `doubledTotal` 0 · `lostTotal` 0 · `faultCountsMatch` true(지연은 기다리기 시작할 때 센다) · timing `waste.<셋>.startedAfterGiveUp` 0 · `clients.noResponse` 0(지금 `task05-reach.timing.json`이 이미 견준다). 지난 실행: 42개 모두 같은 값.

| 조건 | 정해지는 지표 = 값 | 근거 | 지난 실행 |
|---|---|---|---|
| P1-H · P1-G1 · P1-G2(exp, exp2의 P1-G1) | task 02 da-inside의 행 전부(`labels`·`layerMismatch` 400·타임아웃·헛일) · G1·G2: timing `waste.payment.cancelSeen` 400 · transport `deadline.paymentAfterInventoryOverSlack` 0 | P1에서는 결제가 받은 기한(재고가 건 1000)이 재고가 받은 기한(2000)보다 일러 G2 = G1(설계 7절). 결제는 받은 기한 1000이 지나는 것을 자는 중에 안다(1500·2500, 여유 500) | r1~r3 같음 |
| P1-G3 · P1-G1o(exp2) | 대조 `layerMismatch` 0 · `labels` 전체(`""`·`500` → `completed … APPROVED` 각 200, `1500`·`2500` → `unknown \| UNKNOWN \| UNKNOWN \| -` 각 200) · timing `edges.inventory->payment.timeouts` 400 · `falseTimeouts` 0 · `waste.payment.wastedProcessed` 0 · `layers.payment.stopped.cancelled-in-delay` 400 · transport `records.payment.stopped` 400 | 결제가 받은 기한(1000)이 자는 중에 지나 멈춘다 — 행 없음(여유 500). G1o도 결제가 자기가 받은 기한을 보므로 같다 | r1~r3 같음 |
| P2-H · P2-G1(exp, exp2) | task 02 da-longer의 행 전부 · G1: timing `waste.inventory.cancelSeen` 400 · `waste.payment.cancelSeen` 200 · transport `deadline.paymentAfterInventoryOverSlack` 800 | G1은 결제의 기한 = 재고가 받은 때 + 2000, 재고의 기한 = 주문이 부른 때 + 1000 → 모든 결제 호출이 ≈ 1000ms 늦다(> 50). 2500짜리만 결제가 받은 기한(2000)이 자는 중에 지난다 | r1~r3 같음 |
| P2-G2 | 대조 `layerMismatch` 400 · `labels` 전체(`1500`·`2500` → `unknown \| UNKNOWN \| UNKNOWN \| APPROVED` 각 200, 나머지 `completed …` 400) · timing `edges.order->inventory.timeouts` 400 · `falseTimeouts` 0 · `edges.inventory->payment.timeouts` 400 · `falseTimeouts` 400 · `belowDelay.timeouts` 200 · `waste.payment.wasted` 400 · `wastedProcessed` 400 · `waste.payment.cancelSeen` 400 · transport `deadline.paymentAfterInventoryOverSlack` 0 | 주문이 1000에 포기하면 재고의 결제 호출도 받은 기한으로 끊긴다 → 재고 `UNKNOWN`(1500짜리도 — 여유 500). 결제는 취소를 보지 않아 끝까지 커밋 | r1~r3 같음 |
| P2-G3 | 대조 `layerMismatch` 0 · `labels`는 P1-G3과 같다 · timing `edges.order->inventory.timeouts` 400 · `edges.inventory->payment.timeouts` 400 · `belowDelay.timeouts` 200 · `falseTimeouts` 0(두 구간) · `waste.payment.wastedProcessed` 0 · `waste.inventory.wastedProcessed` 0 · `layers.payment.stopped.cancelled-in-delay` 400 · transport `records.payment.stopped` 400 | 넘겨받은 기한(≈ 1000)이 결제가 자는 중에 지나 멈춘다 | r1~r3 같음 |
| P2-G1o(exp2) | 대조 `layerMismatch` 200 · `labels` 전체(`1500` → `unknown \| UNKNOWN \| RESERVED \| APPROVED` 200, `2500` → `unknown \| UNKNOWN \| UNKNOWN \| -` 200, 나머지 `completed …` 400) · timing `edges.order->inventory.timeouts` 400 · `falseTimeouts` 200 · `edges.inventory->payment.timeouts` 200 · `falseTimeouts` 0 · `waste.inventory.wasted` 400 · `wastedProcessed` 200 · `waste.payment.wasted` 400 · `wastedProcessed` 200 · `waste.payment.stopped` 200 · `waste.payment.cancelSeen` 200 · `waste.inventory.cancelSeen` 400 · `layers.payment.stopped.cancelled-in-delay` 200 · transport `records.payment.stopped` 200 · `deadline.paymentAfterInventoryOverSlack` 800 | 넘기지 않아 결제의 기한은 재고가 건 2000. 1500짜리는 그 안에 끝나 처리, 2500짜리는 2000에 멈춤. 멈춘 시각(≈ 2000)이 주문이 포기한 때(1000)보다 1000 늦어 헛일 건수가 경합이 아니다 | r1~r3 같음 |

**정해지지 않음**:
- G3·P1-G1o의 `waste.payment.wasted`·`stopped`·`cancelSeen`(P1-G3 346/334/349, P2-G3 395/398/396): 결제가 멈춘 때와 위가 포기한 때가 같은 기한에서 나와 μs 단위로 다툰다(설계 7절 "재는 값"). P2-G2·G3의 `waste.inventory.wasted`·`cancelSeen`(399/400)도 같은 까닭.
- 모든 `*Ms` 값(`wastedMsSum`·`cancelAfterGiveUpMs`·`endAfterCancelMs`·`cancelMinusDeadlineMs`·`paymentAfterInventoryMs`), `waste.*.cancelBeforeDeadline`.
- `counts.<L>.cancelledBeforeHandler.grpc`(지난 실행 모두 0): 서버 실행기의 큐에서 기다리는 동안 취소되는지 — 부하와 자원에 달렸다.

## 7. 서버에서 다시 잴 때 — 기대 파일을 만드는 차례(안)

1. **서버 배치를 정하고 보정부터 통과시킨다**(기존 기대 파일 그대로). 그 다음에 실험 조건의 기대 파일을 만든다 — 보정이 통과하지 않은 배치에서 실험의 기대를 맞추지 않는다.
2. **이름**: `load/expectations/<task>-<조건>[.<분석>].json`(예 `t02-da-inside.json`, `t02-da-inside.timing.json`, `t03-ga-max2.retry.json`, `t04-ra-on.idem.json`, `t05-P2-G1o.transport.json`). 까닭 둘: ① `scripts/run.sh`는 환경 변수가 없으면 `load/expectations/<시나리오>.json`·`.timing.json`·`.retry.json`·`.overload.json`을 **스스로 찾아 쓴다**(97·116·126·456줄) — 시나리오 이름으로 두면 같은 시나리오의 다른 조건(예 `false-timeout`의 T 넷, `retry-mix`의 아홉 조건)에 조용히 걸린다. ② 조건 이름이 task 사이에 겹친다(`ga-*`·`na-*`·`ra-on`이 task 02~04-b에 모두 있다).
3. **넘기는 법**: 조건 값과 함께 `RECON_EXPECT`·`TIMING_EXPECT`·`RETRY_EXPECT`·`IDEM_EXPECT`·`OVERLOAD_EXPECT`·`TRANSPORT_EXPECT`를 준다 — `scripts/task0*-all.sh`가 보정에 쓰는 꼴 그대로(예 `scripts/task04-all.sh` 42줄 `RECON_EXPECT=$X/cal-idem-off.json IDEM_EXPECT=…`). **막히는 곳**: `scripts/task02.sh`(19줄)는 `TIMING_EXPECT`만, `scripts/task03.sh`(19줄)는 `RETRY_EXPECT`·`TIMING_EXPECT`만 받아 `RECON_EXPECT`를 주면 "모르는 조건"으로 멈춘다. `scripts/task04.sh`(24줄)는 모두 받지만 `VIRTUAL_THREADS`를 받지 않는다(task 02 실험 나). task 06에서 두 스크립트의 허용 목록에 `RECON_EXPECT`를 더하는 것이 가장 작은 변경이다(실행물 변경 — 그때 따로 합의).
4. **task 05의 timing 기대**: 지금 실험은 `TIMING_EXPECT=task05-reach.timing.json`(`scripts/task05-all.sh` 36줄)을 준다. 조건마다의 timing 기대를 주면 이것을 대신하므로 `clients.notReachedServer`·`clients.noResponse` 두 키를 조건마다의 파일에 함께 넣는다.
5. **차례**: 조건마다 위 표의 "정해지는 지표"만 옮긴다 — 값은 `min = max`, 하한·상한은 표에 적은 대로. `labels`는 표에 "전체"라고 적은 조건만. 만든 뒤 **지난 노트북 실행(커밋된 보고)에 분석기를 다시 돌려**(`scripts/task0*-recompute.sh` 꼴 — 원시 묶음을 푸는 일이라 측정이 쉬는 때에) 모두 통과하는지 본다. 통과하지 않는 줄이 있으면 그 지표가 정말 정해지는지부터 다시 본다(값을 실행에 맞추지 않는다).
6. **고장 내기**: 조건 하나의 기대 파일에서 정해진 값 하나를 1 바꿔 그 조건을 다시 계산하면 종료 2와 그 차이 줄이 나와야 한다 — 기대 파일이 실제로 걸렸다는 확인.

## 8. 확인하지 못한 것

- **경합의 여유**(da-3S·ga-2-off의 `fail-first=3`, ga-2-on의 `fail-first=2`, na-fixed의 무리 38 셋째 시도)는 설계 문서에 적힌 시각(실패한 시도 ≈ 13ms, 첫 듦 ≈ 22~29ms)으로 셌다. 원시 기록(시각 기록·시도 기록)에서 요청마다의 여유를 보지 않았다. 서버를 나누면 층마다 전달 몫이 늘어 이 여유가 줄거나(경합이 실제로 뒤집힘) 다른 곳의 100ms 여유가 모자랄 수 있다.
- "끊기지 않는다" 쪽의 여유 100ms 기준은 이 문서가 정한 것이다. 서버 배치의 층 사이 왕복 시간을 잰 뒤 다시 본다.
- `false-timeout` 지연의 최대(663ms)와 592·125·19는 `load/chain.js`의 함수를 Python으로 옮겨 다시 센 값이다(분석기의 `injectedCalleeDelayAtLeastTimeout`과 같았다). k6(JavaScript)의 `Math.log`·`Math.cos`와 마지막 자리가 다를 수 있으나 경계의 수에는 닿지 않았다.
- 고르게 오는 부하(ra)에서 k6가 경계의 반복을 예정 시각보다 먼저 보내지 않는지(그래서 `outage.requestsSentDuring` 800이 정해지는지)는 k6의 코드를 보지 않아 넣지 않았다.
- task 04의 첫 62개 실행의 보고는 결과 뒤에 고친 분석기로 다시 만든 것이다(`docs/measurement.md` 9절 머리) — 실제 값은 그 다시 만든 보고에서 읽었다.
- task 02 ga-t2000의 `labels`는 지연 값마다의 라벨(수백 개)이 모두 `completed …`라 섞음에서 정해지지만, 라벨 목록을 만드는 일은 하지 않았다(만들려면 `load/chain.js`의 함수로 생성).
