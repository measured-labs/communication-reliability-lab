# 측정 도구 — 입력과 출력의 형식, 판정 규칙

> 이 문서만 보고 분석기의 테스트를 쓸 수 있게 적는다. 코드와 이 문서가 다르면 이 문서가 계약이다(코드를 고친다).
> 흐름: k6 스크립트(`load/chain.js`)가 요청마다 정답 한 줄을 남긴다 → 실행 스크립트(`scripts/run.sh`)가 세 DB의 행과 서비스가 센 장애 수를 내보낸다 → 분석기(`analyzer/`)가 둘을 대조한다.

## 0. 사슬과 장애 헤더 — 행이 어떻게 생기나

요청은 `order`(입구 `POST /orders`) → `inventory`(`POST /reservations`) → `payment`(`POST /payments`)로 간다. 본문은 셋 다 `{"amount": <정수>}`.

| 헤더 | 뜻 |
|---|---|
| `X-Request-Id` | 부하 도구가 만든 id. 없거나 비면 400(행 없음). 세 서비스의 행에 `request_id`로 남고, 아래 층에 그대로 넘어간다 |
| `X-Fault` | 장애 지시. 아래 층에 그대로 넘어간다. 문법이 틀리면 400(행 없음) |

`X-Fault` 문법: `<service>:<kind>[,<service>:<kind>...]`

- `service` ∈ `order` · `inventory` · `payment`
- `kind` ∈ `delay=<ms>`(양의 정수) · `error` · `fail-after-commit`
- 같은 서비스에 같은 `kind`가 두 번이면 틀린 헤더다. 빈 값·공백뿐인 값(또는 헤더 없음) = 장애 없음. 지시마다 앞뒤 공백은 무시한다(`order:error, payment:delay=5`도 받는다).
- 해석하는 코드는 한 곳이다: `fault-header/…/FaultDirectives.kt`(`FaultDirectives.parse`). 서비스(`chain-link`)와 분석기가 같은 코드를 쓴다 — 서비스가 400으로 거절하는 값은 분석기도 입력 오류로 낸다.

층마다의 처리와 행의 `status`:

| 층 | 처리 | 행의 status |
|---|---|---|
| order | `PENDING` 행 커밋 → inventory 호출 → 결과로 상태 커밋 | `COMPLETED`(아래 2xx) · `FAILED`(아래가 504 밖의 오류 응답, 또는 연결 실패) · `UNKNOWN`(요청 타임아웃, 연결이 끊김, 아래가 504) · `PENDING`(처리 중 멈춤) |
| inventory | 같은 꼴로 payment 호출 | `RESERVED` · `FAILED` · `UNKNOWN` · `PENDING` |
| payment | 한 트랜잭션에 행 하나 | `APPROVED` |

- 위 층의 응답: 아래가 처리함 201, 아래가 처리 안 함 502, 모름 504. payment는 201.
- **처리 행**(그 층이 처리했다고 보는 행): order `COMPLETED`, inventory `RESERVED`, payment `APPROVED`.
- 재시도가 없다. `request_id`에 유일 제약이 없다 — 같은 id가 두 번 오면 두 번 처리된다(task 01의 정상 동작, 분석기가 "두 번 처리됨"으로 센다).

장애가 그 서비스를 가리킬 때:

| kind | 언제 | 하는 일 | 그 서비스의 카운터 |
|---|---|---|---|
| `delay=<ms>` | 입구에서, 처리 전 | 그만큼 잔 뒤 처리한다 | `delay` +1 |
| `error` | 입구에서, 처리 전(`delay`가 함께 있으면 잔 뒤) | 처리하지 않고 500. 그 서비스와 아래 층에 행이 생기지 않는다 | `error` +1 |
| `fail-after-commit` | 그 서비스의 마지막 커밋 뒤 | 응답 대신 500(본문 `{"fault":"fail-after-commit",...}`). 행은 남는다 | `fail-after-commit` +1 |

처리 중인 요청 수: `GET /inflight` → `{"inflight":n}` — 업무 입구에 들어와 아직 끝나지 않은 요청 수(`delay`로 자고 있는 것 포함, 400·500으로 끝난 것은 끝난 것). 실행 스크립트가 부하 뒤 이 값으로 가라앉기를 판정한다(6절).

서비스의 카운터: `GET /faults` → `{"delay":n,"error":n,"fail-after-commit":n}`(프로세스가 마지막 `DELETE /faults` 뒤 넣은 수). 요청이 그 서비스에 닿지 않으면(위 층에서 `error` 등) 그 서비스의 지시는 세지 않는다.

## 1. 정답 — `answers.jsonl`

한 줄 = 보낸 HTTP 요청 하나. UTF-8, 줄마다 JSON 객체 하나. 빈 줄(공백뿐인 줄 포함)은 없다.
줄끝은 LF(`\n`)만 받는다 — 파일에 CR(`\r`)이 하나라도 있으면 입력 오류(k6와 실행 스크립트는 LF만 쓴다). 파일 끝의 줄바꿈 하나는 마지막 줄의 끝으로 보고, 그 뒤에 또 줄바꿈이 있으면 빈 줄이다. 줄이 JSON 객체가 아니면(배열, 수, 문자열 등) 입력 오류.

| 필드 | 형 | 필수 | 뜻 |
|---|---|---|---|
| `type` | 문자열 | 예 | 늘 `"answer"` |
| `requestId` | 문자열 | 예 | 비거나 공백뿐이면 입력 오류. 같은 값이 여러 줄에 나올 수 있다(같은 id를 두 번 보낸 경우) |
| `fault` | 문자열 | 예 | 보낸 `X-Fault` 값 그대로. `""` = 장애 없음. 0절의 문법을 따른다 |
| `amount` | 정수 | 예 | 보낸 본문의 금액(`{"amount": n}`). 부하 스크립트는 `1000 + 요청 번호`를 보낸다(요청마다 다르게 — 다른 요청의 금액이 섞여도 드러나게) |
| `status` | 정수 | 예 | 받은 HTTP 상태. `0` = 응답을 받지 못함(k6 타임아웃, 연결 오류). 0이 아니면 100~599 |
| `errorCode` | 정수 | 예 | k6의 `error_code`(0 = 없음, 1050 = 요청 타임아웃). 판정에 쓰지 않고 기록만 |
| `elapsedMs` | 수 | 예 | 보내기 직전부터 응답(또는 타임아웃)까지의 경과(ms, k6 VU 안에서 잼). 0 이상 |
| `iteration`, `sentAt` | — | 아니오 | 기록용. 분석기는 읽지 않는다 |

- **정수** 칸(`status`, `errorCode`, `amount`)은 JSON 정수만 받는다: `201.0`, `2.01e2`, `"201"`은 입력 오류. `elapsedMs`(수)는 정수와 소수를 모두 받는다.
- 필수 필드가 없거나 `null`이면 입력 오류.

그 밖의 필드는 무시한다. 줄의 차례는 뜻이 없다(완료 차례로 쌓인다).

보내는 수: k6 `constant-arrival-rate`는 끝 경계에서 반복을 하나 더 시작할 때가 있어서(30s × 20/s에 601번을 실측), 스크립트가 `iterationInTest >= RATE × DURATION_SECONDS`인 반복은 보내지 않는다(정답 줄도 없음). 모자라면(VU 부족으로 떨어진 반복) 분석기의 `--planned`가 잡는다.

k6는 `--log-output=file=<k6-log.txt> --log-format=raw`로 `console.log`를 파일에 쓰고, 같은 파일에 자기 경고(`Request Failed`)도 쓴다. 실행 스크립트가 `{"type":"answer"`로 시작하는 줄만 골라 `answers.jsonl`을 만든다(0줄이면 실행 실패).

## 2. DB 상태 — `db/orders.csv` · `db/reservations.csv` · `db/payments.csv`

PostgreSQL `\copy ... to stdout with (format csv, header)`의 결과. 첫 줄은 머리줄이고 정확히 아래와 같아야 한다. 행이 0개여도 머리줄은 있다.

| 파일 | 머리줄 | 판정에 쓰는 칸 |
|---|---|---|
| `orders.csv` | `request_id,amount,status` | `request_id`, `amount`, `status` |
| `reservations.csv` | `request_id,status` | `request_id`, `status` |
| `payments.csv` | `request_id,amount,status` | `request_id`, `amount`, `status` |

- 줄끝은 1절과 같다(LF만, CR이 있으면 입력 오류, 끝의 줄바꿈 하나는 받는다).
- 값에 따옴표(`"`)가 든 줄은 받지 않는다(이 실험의 값은 id·상태 낱말·정수뿐이다).
- 칸 수가 머리줄과 다르거나 `request_id`·`status`가 비면(공백뿐이어도) 입력 오류.
- `status`는 그 층의 서비스가 쓰는 낱말만 받는다 — 그 밖의 낱말은 입력 오류(대소문자를 가린다):

| 층 | 받는 `status` |
|---|---|
| order | `PENDING` · `COMPLETED` · `FAILED` · `UNKNOWN` |
| inventory | `PENDING` · `RESERVED` · `FAILED` · `UNKNOWN` |
| payment | `APPROVED` |

- `amount`(order, payment)는 10진 정수(`-?[0-9]+`, 64비트 안)만 받는다. 비었거나 `1000.0`·`abc`면 입력 오류.

## 3. 장애 수 — `fault-counts.json`

`{"order":{"delay":n,"error":n,"fail-after-commit":n},"inventory":{...},"payment":{...}}` — 실행 끝에 세 서비스의 `GET /faults`를 모은 것. 서비스 셋과 종류 셋이 모두 있어야 하고 값은 0 이상의 정수(JSON 정수 — `"0"`·`0.0`은 입력 오류). 그 밖의 키(서비스 셋 밖의 키, 종류 셋 밖의 키)가 있으면 입력 오류.

## 4. 판정

### 4-1. 요청마다(정답 한 줄마다) — 응답으로 가름

| 판정 | 조건 |
|---|---|
| `completed`(완료) | `status` 200~299 |
| `unknown`(결과 모름) | `status` 0 또는 504 |
| `failed`(실패) | 그 밖(4xx, 504 밖의 5xx) |

결과 모름은 성공이나 실패로 세지 않는다(명세 2절). 실제로 처리됐는지는 4-2가 DB로 가른다.

### 4-2. requestId마다 — DB로 가름

같은 `requestId`의 정답 줄을 묶는다(묶음 = requestId 하나). 층 L에서 `processed(L)` = 그 requestId의 행 가운데 L의 처리 행(`COMPLETED`/`RESERVED`/`APPROVED`) 수.

- `hasCompleted` = 묶음에 `completed`가 하나라도 있다
- `allFailed` = 묶음이 모두 `failed`
- `unknownOnly` = `completed`가 없고 `unknown`이 하나라도 있다

| 지표(층 L마다) | 세는 것(requestId 수) |
|---|---|
| `lost`(잃음) | `hasCompleted` 이고 `processed(L) == 0` |
| `doubled`(두 번 처리됨) | `processed(L) >= 2` |
| `failedButProcessed` | `allFailed` 이고 `processed(L) >= 1` |
| `unknownProcessed` | `unknownOnly` 이고 `processed(L) >= 1` |
| `unknownNotProcessed` | `unknownOnly` 이고 `processed(L) == 0` |

| 지표(층마다, 행 수) | 세는 것 |
|---|---|
| `rows` | 그 층의 행 전부 |
| `processedRows` | 처리 행 |
| `pendingRows` | `status == "PENDING"`인 행(처리 중 멈춤) |
| `orphanRows` | `request_id`가 정답 어디에도 없는 행(초기화가 덜 됨, 워밍업 섞임 등) |
| `amountMismatch` | 금액 칸이 있는 층(order, payment)에서, 처리 행 가운데 `amount`가 그 requestId로 보낸 금액(정답 줄들의 `amount`) 어느 것과도 다른 행. 정답에 없는 행은 세지 않는다(`orphanRows`로 센다). inventory는 금액 칸이 없어 `null` |

| 전체 지표 | 세는 것(requestId 수) |
|---|---|
| `lostTotal` | `hasCompleted` 이고 어느 한 층이라도 `processed == 0` |
| `doubledTotal` | 어느 한 층이라도 `processed >= 2` |
| `layerMismatch`(층마다의 건수 일치) | 세 층의 `processed` 값이 모두 같지는 않다 |
| `amountMismatch`(행 수) | 두 층의 `amountMismatch` 합 |

- "잃음"의 기준은 "처리 행이 하나 이상"이다(정답 줄 수만큼이 아니다) — 같은 id를 k번 보내 모두 완료여도 행이 1개면 잃음이 아니다. 멱등 키(task 04)가 들어오면 그것이 맞는 결과이기 때문이다. 같은 id를 두 번 보내 행이 두 개면 `doubled`로 센다.
- 정답에 없는 행은 위 requestId 지표에 들어가지 않고 `orphanRows`로만 센다.

### 4-3. 라벨과 서명 — 종류마다의 건수

- 묶음의 **라벨** = 묶음의 `fault` 값 그대로(모두 같을 때). 서로 다르면 `"(mixed)"` — 그 묶음의 정답 줄은 **모두** `(mixed)` 라벨로 센다(`answers`, `verdicts`, 분위수 모두. 줄마다 자기 `fault`의 라벨로 나누지 않는다). `(mixed)`는 0절의 문법에 맞지 않으므로 실제 `fault` 값과 겹치지 않는다.
- 묶음의 **서명** = `응답 | order | inventory | payment` 네 칸을 `" | "`(공백-세로줄-공백)로 이은 문자열.
  - 응답 칸: 묶음의 판정 낱말(`completed`/`failed`/`unknown`)을 사전순으로 정렬해 `+`로 잇는다. 예: `completed+completed`
  - 층 칸: 그 requestId의 그 층 행의 `status`(처리 행만이 아니라 모든 행)를 사전순으로 정렬해 `+`로 잇는다. 행이 없으면 `-`. 예: `COMPLETED+COMPLETED`, `-`
  - 예: `unknown | UNKNOWN | RESERVED | APPROVED`
- 라벨마다: 정답 줄 수(`answers`), requestId 수, 판정마다의 줄 수(`verdicts`), 서명마다의 requestId 수(`signatures`), `elapsedMs` 분위수.

### 4-4. 장애 수 대조

(서비스, 종류)마다 `injected` = 정답 줄들의 `fault`에 그 지시가 든 줄 수(줄마다 1, 지시의 `delay` 값은 보지 않는다), `counted` = `fault-counts.json`의 값. 아홉 칸 모두 같으면 `faultCountsMatch = true`.
한계: 요청이 그 서비스에 닿지 않으면 서비스는 세지 않으므로, 한 요청에 여러 서비스의 지시를 섞으면(예: `inventory:error,payment:delay=100`) 일치하지 않는 것이 맞다. 보정 실행은 요청마다 지시를 하나만 넣는다.

### 4-5. 처리 시간 분위수

`elapsedMs`로 nearest-rank: 오름차순 정렬한 n개에서 p 분위수 = `ceil(p·n)`번째 값(1부터 셈). 순위는 정수 산술로 구한다: 백분율 `q`(50·95·99)에 대해 `(q·n + 99) / 100`(정수 나눗셈) — 부동소수 곱의 오차로 한 칸 밀리지 않게. `p50`·`p95`·`p99`·`max`와 `count`. 0건이면 값은 `null`. 묶음: 전체(`all`), 판정마다(`completed`·`failed`·`unknown`), 라벨마다.

## 5. 분석기 — 명령줄

```
analyzer --run <실행 폴더> [--expect <기대 파일>] [--planned <n>]
```

- 입력: `<실행 폴더>/answers.jsonl`, `<실행 폴더>/db/{orders,reservations,payments}.csv`, `<실행 폴더>/fault-counts.json`.
- 출력: `<실행 폴더>/report.json`, `<실행 폴더>/report.md`(같은 내용의 사람용 표), 표준 출력에 한 줄 요약, 표준 오류에 불일치 목록.
- 인자는 `--이름 값` 짝. 모르는 인자, 짝이 안 맞음, 같은 인자가 두 번, `--run` 없음, `--planned`가 1 이상의 정수가 아님(10진 숫자만 — `+5`·`1.5`·`abc`는 입력 오류, 앞의 0은 받는다: `0600` = 600) → 종료 코드 1.
- `--run`의 끝 `/`는 받는다(`runs/x/` = `runs/x`). 보고의 `run`은 경로의 마지막 이름이다. 이름이 없는 경로(`/`, 빈 값)는 입력 오류.
- 테스트용 입구: `lab.reliability.analyzer.runAnalyzer(args: Array<String>): Int` — 프로세스를 끝내지 않고 종료 코드를 돌려준다(`main`은 이것을 부르고 `exitProcess`). 첫 인자가 `timing`이면 7절의 시각 분석으로 넘긴다(`runAnalyzer(arrayOf("timing", "--run", …))` = `analyzer timing --run …`).

| 종료 코드 | 뜻 | 보고 파일 |
|---|---|---|
| 0 | 통과(불일치 없음) | 씀 |
| 1 | 입력 오류: 인자(위), 파일 없음, `answers.jsonl`이 0줄이거나 빈 줄·CR·JSON 객체 아님·필드 없음·형이 틀림·`type`이 `answer` 아님·`requestId` 빔·`status` 범위 밖·`elapsedMs` 음수·`fault` 문법 틀림, CSV 머리줄·CR·칸 수·빈 값·따옴표·모르는 `status` 낱말·`amount`가 정수 아님, `fault-counts.json` 꼴·여분 키, 기대 파일이 없음·JSON 아님·빈 객체·모르는 키·값의 형(5-2) | 쓰지 않음. **그 실행 폴더에 이미 있던 `report.json`·`report.md`는 지운다**(옛 보고가 남아 통과로 읽히지 않게) |
| 2 | 불일치: `--planned`와 정답 줄 수가 다름, 늘 0이어야 하는 지표가 0이 아님(5-3), 또는 기대 파일과 다름 | 씀(`differences`에 목록) |

`--planned`가 없고 `--expect`도 없으면 잃음·두 번 처리됨이 있어도 0이다(그것은 결과이지 도구의 오류가 아니다). 다만 5-3의 지표는 늘 견준다.

### 5-1. `report.json`

```json
{
  "run": "<실행 폴더 이름>",
  "expect": "<--expect 값 또는 null>",
  "planned": 600,
  "differences": ["lostTotal: 기대 0, 실제 1", "..."],
  "reconciliation": {
    "answers": 600, "requestIds": 600,
    "verdicts": {"completed": 150, "failed": 300, "unknown": 150},
    "lostTotal": 0, "doubledTotal": 0, "layerMismatch": 200, "amountMismatch": 0,
    "orphanRows": 0, "pendingRows": 0,
    "layers": {
      "order": {"rows": 550, "processedRows": 250, "pendingRows": 0, "orphanRows": 0, "lost": 0, "doubled": 0,
                "failedButProcessed": 50, "unknownProcessed": 50, "unknownNotProcessed": 100, "amountMismatch": 0},
      "inventory": {"...": "같은 꼴, amountMismatch는 null"}, "payment": {"...": "같은 꼴"}
    },
    "faultCounts": [{"service": "order", "kind": "delay", "injected": 50, "counted": 50, "match": true}, "... 9칸"],
    "faultCountsMatch": true,
    "labels": {
      "": {"answers": 50, "requestIds": 50, "verdicts": {"completed": 50, "failed": 0, "unknown": 0},
           "signatures": {"completed | COMPLETED | RESERVED | APPROVED": 50},
           "elapsedMs": {"count": 50, "p50": 37.0, "p95": 47.0, "p99": 124.0, "max": 124.0}}
    },
    "elapsedMs": {"all": {"...": "분위수"}, "completed": {}, "failed": {}, "unknown": {}}
  }
}
```

객체 안 키의 차례는 뜻이 없다. `planned`가 없으면 `null`. `reconciliation.orphanRows`·`pendingRows`는 세 층의 합이다.

### 5-2. 기대 파일(`--expect`)

JSON 객체. 아래 키 가운데 **적은 것만** 견준다(하나 이상 적어야 한다). 모르는 키는 입력 오류.

| 키 | 형 | 견주는 값 |
|---|---|---|
| `answers` | 정수 | 정답 줄 수 |
| `lostTotal`, `doubledTotal`, `layerMismatch`, `amountMismatch` | 정수 | 4-2 |
| `orphanRows`, `pendingRows` | 정수 | 세 층의 합 |
| `faultCountsMatch` | 불 | 4-4 |
| `labels` | 객체: 라벨 → (서명 → requestId 수) | **꼴 전체**를 견준다 — 없는 쪽을 0으로 본다. 그래서 기대에 `0`으로 적은 서명과 적지 않은 서명은 같다(실제에 없으면 둘 다 일치, 실제에 있으면 둘 다 불일치). 기대에 없는 라벨·서명이 실제에 있으면 불일치 |

- 값의 형이 틀리면(정수 키에 0 이상의 JSON 정수가 아닌 값 — 문자열, 소수, 음수, 불 / `faultCountsMatch`에 불이 아닌 값 / `labels`가 객체가 아니거나 서명의 값이 0 이상의 정수가 아님) 차이가 아니라 **입력 오류**(종료 코드 1).

### 5-3. 늘 0이어야 하는 지표 — 기대 파일이 없어도 견준다

`orphanRows`(세 층의 합), `pendingRows`(세 층의 합), `amountMismatch` — 셋 가운데 0이 아닌 것이 있으면 불일치(종료 코드 2)다. 기대 파일이 없어도, `--planned`가 없어도 견준다. 까닭: 셋은 "결과"가 아니라 기록이 틀렸다는 신호다 — 정답에 없는 행은 초기화가 덜 됐거나 다른 실행이 섞인 것, PENDING 행은 처리가 끝나기 전에 내보낸 것(가라앉기 실패), 금액이 다른 처리 행은 서비스가 받은 것과 다른 것을 기록한 것이다. 한 줄 요약에도 세 수를 적는다.

### 5-4. `differences` 줄의 꼴과 차례

| 종류 | 꼴 | 예 |
|---|---|---|
| `--planned` | `planned: 계획 <n>, 정답 <m>줄` | `planned: 계획 600, 정답 601줄` |
| 늘 0(5-3) | `<키>: 늘 0, 실제 <n>` | `orphanRows: 늘 0, 실제 3` |
| 기대 — 정수 | `<키>: 기대 <n>, 실제 <m>` | `lostTotal: 기대 0, 실제 1` |
| 기대 — 불 | `<키>: 기대 <true/false>, 실제 <true/false>` | `faultCountsMatch: 기대 true, 실제 false` |
| 기대 — 라벨 | `labels['<라벨>']['<서명>']: 기대 <n>, 실제 <m>` (없는 쪽은 0) | `labels['']['completed \| COMPLETED \| RESERVED \| -']: 기대 0, 실제 1` |

차례: ① `planned` ② 늘 0 — `orphanRows`, `pendingRows`, `amountMismatch` 차례 ③ 기대의 정수 키 — `answers`, `lostTotal`, `doubledTotal`, `layerMismatch`, `orphanRows`, `pendingRows`, `amountMismatch` 차례(기대 파일에 적은 것만) ④ `faultCountsMatch` ⑤ `labels` — 라벨의 사전순, 한 라벨 안에서 서명의 사전순(UTF-16 코드 단위 차례 — 빈 라벨 `''`이 맨 앞). 같은 지표가 ②와 ③에 모두 걸리면(예: 기대에 `orphanRows: 0`을 적었고 실제가 3) 두 줄이 나온다 — 줄 하나 = 어긴 규칙 하나.

## 6. 실행 폴더 — `runs/<시나리오>-<YYYYMMDDTHHMMSS>-r<번호>/`

| 파일 | 내용 |
|---|---|
| `config.env` | 시나리오, 반복 번호, 시각, git 커밋(`git_commit`)과 작업 트리가 깨끗했는지(`git_dirty` = `no`/`yes`, 깨끗하지 않으면 `git_dirty_paths`에 바뀐 경로) — git을 읽지 못하면 둘 다 `모름`, 도착률·시간·계획한 수, k6 요청 타임아웃, 가라앉기 최대 시간(`settle_timeout_seconds`)과 실제로 걸린 시간(`settled_after_ms`), 워밍업, k6 판, 호스트 CPU·메모리 |
| `image-build.txt` | 실행 앞에 이미지를 지은 출력(바뀐 것이 없으면 캐시) |
| `compose.resolved.yaml` | `docker compose config` — 자원 제한, JVM 옵션(`-Djdk.httpclient.disableRetryConnect=true` 등), 층 사이 타임아웃 |
| `images.txt` | 쓴 이미지와 PostgreSQL 이미지의 다이제스트 |
| `settings-<서비스>.json` | 떠 있는 서비스가 실제로 쓴 설정(`GET /settings`) — 설정 문자열이 아니라 떠 있는 객체에서 읽은 값: `tomcat`(프로토콜 핸들러와 실행기의 종류, 가상 스레드 실행기인지, 최대·최소 유휴 스레드, 큐(accept count), 최대 연결, 연결 타임아웃, keep-alive 타임아웃 — ms), `hikari`(`HikariDataSource`의 풀 크기, 최소 유휴, 연결 대기 ms), `requestThread`(이 요청을 처리한 스레드가 가상 스레드인지), `nextLayer`(아래 층 호출의 HttpClient와 요청에서 읽은 연결·요청 타임아웃, HTTP 판, 리다이렉트 — 맨 아래 층은 "없음"), `jdkSystemProperties`(JDK HttpClient 시스템 속성의 값 — 주지 않았으면 "설정 안 됨(JDK 기본값)"), `runtime`. 읽지 못한 값은 `"읽지 못함: <까닭>"` |
| `warmup<n>-k6-log.txt`, `warmup<n>-k6-stdout.txt` | 워밍업 회차마다(한 회차가 모두 201이 되면 이어진다, 최대 3회차 — 회차마다의 201 수는 `config.env`의 `warmup_round_<n>`) |
| `k6-log.txt`, `k6-stdout.txt`, `k6-summary.json` | k6 원본 출력 |
| `answers.jsonl` | 1절 |
| `db/*.csv` | 2절 |
| `fault-counts.json` | 3절 |
| `expect.json` | 견준 기대 파일의 사본(`load/expectations/<시나리오>.json`) |
| `report.json`, `report.md` | 5절 |
| (task 02) `config.env`에 더한 칸 | `k6_max_vus`, `k6_no_connection_reuse`(부하의 k6가 keep-alive를 껐나 — `true`면 요청마다 새 연결과 `Connection: close`. `slow-layer`만 `true`, 까닭은 `scripts/run.sh` 머리와 `docs/results/task-02-timeouts.md` 실험 나), `lookup_rate_per_second`, `slow_label`, 조건 `order_to_inventory_timeout`·`inventory_to_payment_timeout`·`virtual_threads`, `timing_expect`, `discard_run`(반복 번호 0 = 서비스를 새로 띄운 직후의 버리는 실행 — `yes`) |
| (task 02) `timing/timings-<서비스>.json`, `timing/samples-<서비스>.json`, `lookups.jsonl` | 7-1절 |
| (task 02) `expect.timing.json`, `report-timing.json`, `metrics.tsv` | 7-4절 |

task 02의 조건(`ORDER_TO_INVENTORY_TIMEOUT`, `INVENTORY_TO_PAYMENT_TIMEOUT`, `VIRTUAL_THREADS`)은 환경 변수로 주고, 서비스도 같은 값으로 띄워 둔다(`scripts/up.sh` — `compose.yaml`이 그 변수를 읽는다). 실행 스크립트는 설정 기록 뒤 `settings-<서비스>.json`(떠 있는 객체의 값)에서 주문·재고의 `nextLayer.requestTimeoutMs`와 세 서비스의 `tomcat.executorVirtualThreads`가 그 조건과 같은지 보고, 다르면 실패(종료 코드 1). 조건 하나를 버리는 실행과 반복으로 돌리는 것은 `scripts/task02.sh`, 모든 조건은 `scripts/task02-all.sh`, 결과 표는 `scripts/task02-table.sh`(`runs/task-02-index.tsv`와 `metrics.tsv`에서 다시 계산).

실행 차례(`scripts/run.sh`): 컨테이너 넷이 떠 있고 세 서비스가 준비됐는지 확인(`/actuator/health`가 HTTP 200이고 최상위 `status`가 `UP`) → 이미지를 짓고(분석기 포함 — 지금의 코드로), 떠 있는 세 서비스가 방금 지은 이미지인지 확인(아니면 실패 — `up.sh`를 다시) → 설정 기록 → 워밍업(장애 없음, 모두 201인 회차가 나올 때까지 최대 3회차) → 세 DB의 행을 비우고 카운터를 0으로(남은 행이 있으면 실패) → 부하 → k6가 보내지 못한 반복(`dropped_iterations`)이 있으면 실패 → **가라앉기**: 세 서비스의 `GET /inflight`를 order → inventory → payment 차례로 읽어 한 바퀴에 셋이 모두 0이면 끝(위 층이 0이면 아래 층에 새 호출이 생기지 않는다). `settle_timeout_seconds`(시나리오의 가장 긴 지연보다 길게 — calibration 20s, duplicate 10s) 안에 0이 되지 않으면 **실패**(종료 코드 1, DB를 내보내지 않는다) → 컨테이너가 여전히 떠 있는지 확인 → 내보내기(task 02: 시각 기록과 표본도) → 분석(task 02: 이어서 7절의 시각 분석). 어느 단계든 실패하면 0이 아닌 코드로 멈춘다.

## 7. 시각 지표 (task 02) — `analyzer timing`

> task 02의 실험(`docs/experiments/task-02-design.md`)이 쓰는 지표. 1~6절의 대조 분석은 그대로 돌고, 이 절의 분석이 그 뒤에 따로 돈다(`scripts/run.sh`의 9단계 — 시나리오가 task 02의 것일 때). 1~6절의 형식은 바꾸지 않았다(정답 줄에 `sentAtMs`가 더해졌지만 1절의 분석기는 모르는 필드를 무시한다).

### 7-1. 입력

| 파일 | 만드는 곳 | 형식 |
|---|---|---|
| `answers.jsonl` | k6 | 1절 + **`sentAtMs`**(정수, 보내기 직전의 벽시계 ms). 이 분석에서는 필수 |
| `db/*.csv` | 실행 스크립트 | 2절 그대로 |
| `settings-order.json`, `settings-inventory.json` | 실행 스크립트(`GET /settings`) | `nextLayer.requestTimeoutMs`(정수)를 읽는다 — 구간의 타임아웃 |
| `timing/timings-<서비스>.json` | 서비스 `GET /timings` | JSON 배열. 원소 = 업무 입구에 든 요청 하나: `requestId`(비지 않은 문자열), `startUs`·`endUs`(정수, 벽시계 μs — 입구 필터에 든 때(지연 장애로 자기 전)와 나간 때), `status`(정수, 필터를 나갈 때의 응답 상태), `callStartUs`·`callEndUs`(정수 또는 `null`), `callOutcome`(`"Completed"` · `"Failed:<까닭>"` · `"Unknown:<까닭>"` 또는 `null`) — 아래 층을 불렀으면 셋 다 있고, 안 불렀으면(맨 아래 층, 장애로 처리 전에 끝남) 셋 다 `null` |
| `timing/samples-<서비스>.json` | 서비스 `GET /samples` | `{"intervalMs": n, "overflow": 불, "samples": [...]}`. 표본 = `atMs`(정수, 벽시계 ms), `inflight`(정수 ≥ 0 — `GET /inflight`와 같은 값), `active`·`poolSize`·`maxThreads`·`queue`(Tomcat 실행기가 플랫폼 스레드 풀이면 정수, 아니면 `null` — 가상 스레드), `connections`(정수 또는 `null`). 서비스가 100ms마다 Tomcat 밖의 데몬 스레드에서 뜬다 |
| `lookups.jsonl` | k6(`slow-layer`만) | 줄마다 `{"type":"lookup","sentAtMs":정수,"status":정수,"elapsedMs":수,...}` — 가벼운 조회 `GET /orders/{id}` 하나 |

서비스 쪽 끝점: `GET /timings`·`DELETE /timings`, `GET /samples`·`DELETE /samples`. 실행 스크립트는 상태 초기화(4단계)에서 둘을 비우고 내보내기(7단계)에서 받는다(받지 못하면 실행 실패). 시각 기록과 표본은 메모리에만 있다 — 서비스가 다시 뜨면 사라진다.

### 7-2. 입력 오류 (종료 코드 1, `report-timing.json`·`metrics.tsv`를 쓰지 않고 있던 것은 지운다)

- 1·2절의 입력 오류 전부(같은 읽기 코드를 쓴다) + 정답 줄에 `sentAtMs`가 없거나 정수가 아님.
- **같은 `requestId`가 정답에 두 줄 이상** — 시각 지표는 requestId마다 한 번 보낸 실행에서만 정의된다.
- 파일이 없음·비었음·JSON이 아님·꼴이 틀림(위 표), `endUs < startUs`, call 칸 셋 가운데 일부만 있음, `start ≤ callStart ≤ callEnd ≤ end`가 아님, 맨 아래 층(payment)에 call 칸이 있음, `callOutcome`의 꼴이 틀림.
- **시각 기록이 빠짐**: 어느 층이든 DB 행은 있는데 그 requestId의 시각 기록이 그 층에 없음. 한 층에 한 requestId의 시각 기록이 둘 이상. 정답에 없는 requestId의 시각 기록.
- 표본: `overflow`가 `true`(서비스가 상한 100000개를 넘겨 더 쌓지 않았다), `samples`가 비었음, `atMs`가 앞보다 이름, 표본이 부하 구간을 덮지 않음(첫 표본 > 첫 `sentAtMs` + 2×간격, 또는 마지막 표본 < 정답들의 `sentAtMs + elapsedMs` 최대).
- `--slow-label`을 줬는데 그 라벨로 보낸 정답이 없음, `lookups.jsonl`이 없음·0줄·형식이 틀림, 느린 구간 앞의 주문 표본이 없음.
- 인자: `timing` 뒤에 `--run`(필수), `--slow-label`, `--expect` 짝. 모르는 인자·짝이 안 맞음·두 번 → 1. 기대 파일이 없음·JSON 객체가 아님·빔·값이 `{"min": 수, "max": 수}`(min ≤ max)가 아님 → 1.
- **음수 시각**: 정답의 `sentAtMs`, 시각 기록의 `startUs`(그래서 `endUs`·call 칸도 — 차례 검사로), 표본의 `atMs`, 조회의 `sentAtMs`가 음수 → 1.
- **모르는 층 이름**: 정답의 `fault`가 세 서비스(`order`·`inventory`·`payment`) 밖을 가리키면 1절의 `fault` 문법 오류다(1). 시각 기록·표본 파일의 이름은 세 서비스로 정해져 있다(다른 이름의 파일은 읽지 않는다).
- **`callOutcome`의 꼴**: 정규식 `Completed|(Failed|Unknown):\S+` 전체와 맞아야 한다(`Unknown:REQUEST_TIMEOUT`, `Failed:HTTP_500` 등 — 쌍점 뒤는 공백 없는 한 낱말 이상). 아니면 1.
- **설정 파일의 최소 꼴**: `settings-order.json`·`settings-inventory.json`은 JSON 객체이고 `nextLayer.requestTimeoutMs`가 1 이상의 JSON 정수다. 다른 칸은 읽지 않는다(없어도 된다). `settings-payment.json`은 읽지 않는다.

### 7-3. 지표의 식

분위수: nearest-rank, 천분율 `q`(500·900·990)에 대해 `(q·n + 999) / 1000`번째(정수 나눗셈, 1부터) — 4-5절과 같은 규칙에 p90을 더했다. 꼴 `{"count","p50","p90","p99","max"}`, 0건이면 `count` 0과 나머지 `null`. 시간은 ms(μs ÷ 1000).

| 지표(`report-timing.json`의 자리) | 식 |
|---|---|
| `delays.<서비스>` | 정답의 `fault`에서 그 서비스를 가리키는 `delay=<ms>` 값들의 분위수(넣은 지연) |
| `edges.<U>-><L>.timeoutMs` | `settings-<U>.json`의 `nextLayer.requestTimeoutMs` |
| `edges.<U>-><L>.calls` | U의 시각 기록 가운데 `callOutcome`이 있는 것의 수 |
| `edges.<U>-><L>.timeouts` | 그 가운데 `callOutcome == "Unknown:REQUEST_TIMEOUT"` |
| `clients.noResponse` | 정답 가운데 `status` 0(k6가 응답을 받지 못함 — k6 타임아웃이나 연결 오류) |
| `clients.notReachedServer` | 그 가운데 주문(맨 위 층)의 시각 기록이 없는 것 — **서버의 업무 입구에 닿지 못한 요청**. 실험의 부하가 서버에 다 닿았는지의 검사다(task 02의 실행에서는 0이어야 한다 — 0이 아니면 결과 문서의 표에 그 수와 시각을 적는다). 한 줄 요약의 첫 칸 |
| `clients.notReachedServerFirstSentMs`·`LastSentMs` | 그 요청들의 `sentAtMs − (정답의 첫 sentAtMs)`의 최소·최대(0건이면 `null`) |
| `edges.<U>-><L>.falseTimeouts` | 타임아웃 가운데 L의 DB에 그 requestId의 처리 행(4-2절)이 있는 것. **L 자신의 처리 행만 본다** — 가운데 층(재고)이 L일 때, 재고 행은 없는데 그 아래(결제)에서 처리된 요청은 거짓 타임아웃으로 세지 않는다 |
| `edges.<U>-><L>.falseTimeoutRatio` | `falseTimeouts ÷ calls`(calls가 0이면 `null`) |
| `edges.<U>-><L>.outcomes` | `callOutcome`마다의 수 |
| `edges.<U>-><L>.callMs` | U가 잰 `callEnd − callStart`의 분위수 |
| `edges.<U>-><L>.injectedCalleeDelayAtLeastTimeout` | L에 넣은 지연 가운데 `timeoutMs` 이상인 것의 수(거짓 타임아웃의 아래 한계로 견주려는 것) |
| `edges.<U>-><L>.belowDelay.timeouts` | 타임아웃 가운데 L에 넣은 지연(정답의 `fault`, 없으면 0)이 `timeoutMs`보다 **짧은** 것 — "넣은 지연 ≥ 타임아웃"보다 많이 끊긴 몫. 아래 넷으로 가른다(차례대로 처음 맞는 것): |
| `….belowDelay.calleeNotStarted` | L의 시각 기록이 없다(L의 입구에 들지 않았다) |
| `….belowDelay.calleeServiceAtLeastTimeout` | L의 `end − start ≥ timeoutMs` — L 안의 시간(넣은 지연 + 그 밖)만으로 타임아웃 이상 |
| `….belowDelay.calleeEndedAfterGiveUp` | L의 `end > ` U의 `callEnd` — L 안은 타임아웃 미만인데 U가 부른 때부터 L의 입구까지가 더해져 U가 포기한 뒤 끝났다 |
| `….belowDelay.calleeEndedBeforeGiveUp` | 나머지 — L은 U가 포기하기 전에 끝났는데 응답이 U에 닿기 전에 타임아웃이 울렸다(헛일로 세지 않는 거짓 타임아웃) |
| `….belowDelay.callToCalleeStartMs`·`calleeServiceBeyondDelayMs` | L의 시각 기록이 있는 것의 `start(L) − callStart(U)`, `end(L) − start(L) − 넣은 지연`의 분위수 |
| `layers.<L>.records`, `layers.<L>.serviceMs` | L의 시각 기록 수, `end − start`의 분위수(불린 쪽 응답 시간 — 넣은 지연 포함) |
| `giveUp(L, id)`(보고에 따로 나오지 않음) | 다음 가운데 가장 이른 것(없으면 정의되지 않음): k6가 응답을 받지 못했으면(`status` 0) `sentAtMs + elapsedMs` · L보다 위의 층 U마다 U의 `callOutcome`이 `Unknown:REQUEST_TIMEOUT`이면 U의 `callEnd` |
| `waste.<L>.wasted` | L의 시각 기록 가운데 `giveUp`이 정의되고 `end > giveUp`인 것 |
| `waste.<L>.wastedProcessed` | 그 가운데 L의 DB에 그 requestId의 처리 행이 있는 것 |
| `waste.<L>.startedAfterGiveUp` | 그 가운데 `start ≥ giveUp`인 것(위가 포기한 뒤에 시작한 일) |
| `waste.<L>.startedAfterGiveUpMsSum` | 그 몫(`startedAfterGiveUp`)의 헛일 시간의 합(소수 첫째 자리). `wastedMsSum − 이것` = 위가 포기한 때 진행 중이던 일의 헛일 시간 |
| `waste.<L>.wastedMs`, `wastedMsSum` | 헛일 시간 `end − max(start, giveUp)`의 분위수와 합(소수 첫째 자리) |
| `samples.<서비스>.maxGapMs` | 이웃한 표본의 `atMs` 차의 최대. **표본이 하나면 `null`**(간격이 없다 — 7-4의 검사가 차이 줄을 낸다) |
| `samples.<서비스>.maxInflight`·`maxActive`·`maxThreads`·`maxQueue`·`maxConnections` | 표본의 최대(그 칸이 모두 `null`이면 `null`) |
| `samples.<서비스>.saturatedMs` | `active ≥ maxThreads`인 표본 수 × `intervalMs`(`maxThreads`가 없으면 `null`) |
| `samples.<서비스>.meanLoadDuringSend` | `atMs`가 [첫 `sentAtMs`, 마지막 `sentAtMs`](양끝 포함)인 표본의 `inflight + queue`(queue가 `null`이면 0) 평균. **그 창 안에 표본이 없으면 `null`**(7-4의 검사가 차이 줄을 낸다 — 보내는 시간이 표본 간격보다 짧은 실행) |
| `slow.startMs`·`endMs` | `--slow-label`과 `fault`가 같은 정답의 `sentAtMs` 최소·최대(느린 구간) |
| `slow.lookups.<before/during/after>` | 조회를 `sentAtMs < start` / `start ≤ sentAtMs ≤ end` / `> end`로 나눠 `count`, `failed`(`status ≠ 200`, 0 포함), `elapsedMs` 분위수. **분위수에는 실패한 조회도 넣는다**(k6가 잰 경과 — 응답 없음이면 k6가 포기할 때까지의 시간). 실패만 따로 보려면 `failed` |
| `slow.normalChain.<before/during/after>`, `slow.slowChain` | 느린 라벨이 아닌 사슬 요청과 느린 요청의 4-1절 판정마다의 수. 정상 요청은 조회와 같은 경계로 앞(`< start`) · **안(`start ≤ sentAtMs ≤ end`, 양끝 포함 — 느린 요청 사이에 끼어 보낸 정상 요청)** · 뒤(`> end`)에 센다 |
| `slow.badAfter.<chainNot2xx/chainSlow2xx/lookupsNot200/lookupsSlow200>` | 느린 구간 뒤(`sentAtMs > end`)에 보낸 "나쁜" 요청(아래 회복 ②의 뜻)의 수를 까닭마다: 사슬 2xx 아님 / 사슬 2xx인데 `elapsedMs > 1000` / 조회 200 아님 / 조회 200인데 `elapsedMs > 1000` — 회복 ②가 무엇 때문에 그 길이였는지 보려고 |
| `slow.baselineOrderLoad` | 주문 표본 가운데 `atMs < start`인 것의 `inflight + queue` 최대 |
| `slow.recoveredBySamples`(불) | 마지막 주문 표본이 `atMs ≤ end`이거나 `inflight + queue ≤ baselineOrderLoad`이면 `true`. 아니면 `false` — 표본이 끝날 때까지 회복하지 못했다 |
| `slow.recoveryBySamplesMs` | 회복했으면: `atMs > end`이고 `inflight + queue > baselineOrderLoad`인 주문 표본의 마지막 `atMs` − `end`(없으면 0). **회복하지 못했으면 `null`**(값이 아니라 그 사실 — "마지막 표본까지"의 시간을 회복 시간으로 내지 않는다) |
| `slow.recoveredByClients`(불) | `sentAtMs > end`인 요청(느린 라벨이 아닌 사슬 요청과 조회)이 하나 이상 있고, "나쁜" 것 — 성공이 아님(사슬 2xx, 조회 200) 또는 `elapsedMs > 1000` — 이 없거나 마지막 나쁜 요청의 `sentAtMs` 이상에 보낸 좋은 요청이 있으면 `true`. 아니면 `false`(마지막까지 나빴거나 뒤에 보낸 요청이 없다) |
| `slow.recoveryByClientsMs` | 회복했으면: 나쁜 것의 마지막 `sentAtMs` − `end`(없으면 0). **회복하지 못했으면 `null`** |

**보고에 있는데 위 표에 따로 적지 않은 키**(모두 입력을 센 것이다): `answers`(정답 줄 수), `samples.<서비스>.count`(표본 수)·`intervalMs`(표본 파일의 값), `edges.<U>-><L>.outcomes.<callOutcome>`(`callOutcome`마다의 수 — `metrics.tsv`에서는 이름에 쌍점이 든다), `slow.label`(문자열 — `metrics.tsv`에 없다), `slow.slowRequests`(느린 라벨 정답 수), `slow.lookups.<구간>.count`·`failed`, `slow.normalChain.<구간>.count`·`slow.slowChain.count`(판정 셋의 합). 불 값(`recoveredBy…`)과 문자열은 `report-timing.json`에만 있고 `metrics.tsv`에는 없다 — 표에서 회복 시간이 빈 값(`없음`)이면 회복하지 못한 것이다.

### 7-4. 판정과 출력

- 출력: `<실행 폴더>/report-timing.json`(`run`, `expect`, `differences`와 위 지표), `<실행 폴더>/metrics.tsv`(지표를 `a.b.c<TAB>값` 한 줄씩 편 것 — 수와 `null`만, `null`은 빈 값), 표준 출력에 한 줄 요약, 표준 오류에 불일치.
- 늘 견주는 것(서비스마다, 이 차례로): ① `samples.<서비스>.maxGapMs ≤ 1000` — 넘으면 `samples.<서비스>.maxGapMs: 늘 1000 이하, 실제 <n>`(표본이 끊겼다). 표본이 하나라 `null`이면 `samples.<서비스>.maxGapMs: 표본이 하나라 간격을 잴 수 없다` ② `meanLoadDuringSend`가 `null`이면 `samples.<서비스>.meanLoadDuringSend: 보내는 동안의 표본이 없다`. 서비스의 차례는 order → inventory → payment. 그 뒤에 기대 파일의 차이.
- 기대 파일(`--expect`): `{"<metrics.tsv의 이름>": {"min": 수, "max": 수}, ...}`. 이름마다 `min ≤ 값 ≤ max`가 아니면(값이 없거나 `null`이어도) `<이름>: 기대 <min>~<max>, 실제 <값>`. **수의 꼴**: `min`·`max`·값 모두, 정수인 값은 소수점 없이(`0`, `400` — JSON에 `0.0`으로 적었어도), 정수가 아니면 소수 표기 그대로(`307.6`). 값이 없으면 `없음`. 예: `edges.inventory->payment.falseTimeouts: 기대 0~0, 실제 1`. 차례는 기대 파일에 적은 차례. 보정의 기대 파일: `load/expectations/cal-*.timing.json`(실행 스크립트가 `TIMING_EXPECT` 또는 `<시나리오>.timing.json`을 실행 폴더의 `expect.timing.json`으로 옮겨 넘긴다).
- 종료 코드: 0 불일치 없음 · 1 입력 오류(7-2) · 2 불일치. `scripts/run.sh`의 종료 코드는 대조 분석과 시각 분석 가운데 큰 것(대조 분석이 1이면 시각 분석을 돌리지 않는다).

### 7-5. 알려진 한계

- 시계: 세 서비스(컨테이너)와 k6가 한 컴퓨터의 같은 커널 시계를 쓴다는 것에 기댄다. 서버 여러 대로 나누면(task 06) 시계가 어긋난 만큼 `giveUp`과 `end`의 차가 틀린다 — 그때 다시 본다.
- `end`는 입구 필터를 나간 때다. 처리 코드가 예외로 끝나면 `status`는 필터를 나갈 때의 값이라 실제 응답(오류 페이지)과 다를 수 있다.
- 헛일은 "위가 포기한 뒤 아래가 쓴 시간"이다. 아래가 위의 포기를 알 길이 없으므로(취소가 전해지지 않는다) 이 실험의 아래 층은 늘 끝까지 일한다.
- 표본 간격 100ms보다 짧게 차고 빠지는 것은 보이지 않는다.

## 8. 재시도 지표 (task 03) — `analyzer retry`

> task 03의 실험(`docs/experiments/task-03-design.md`)이 쓰는 지표. 재시도 감싸개의 규칙은 `docs/retry.md`. 1~6절의 대조 분석은 그대로 돌고(두 번 처리됨·잃음은 거기서), 이 절의 분석이 그 뒤에 따로 돈다(`scripts/run.sh`의 10단계 — 시나리오가 task 03의 것일 때). task 03의 시나리오에서는 7절의 시각 분석을 돌리지 않는다(7-2가 한 층에 한 requestId의 시각 기록이 둘 이상이면 입력 오류로 내는데, 재시도하면 그것이 정상이다).
> 1~7절의 형식과 동작은 바꾸지 않았다. 정답 줄에 `attemptFault`가 더해지는 것은 task 03의 시나리오뿐이다(1절의 분석기는 모르는 필드를 무시한다).

### 8-1. 시도마다 넣는 실패 — `X-Attempt-Fault`

`<service>:<kind>=<value>[,<service>:<kind>=<value>...]` — 사슬을 따라 그대로 넘어간다. 해석하는 코드는 한 곳: `fault-header/…/AttemptFaultDirectives.kt`(서비스와 분석기가 같이 쓴다).

| kind | value | 그 서비스에서 | 카운터 |
|---|---|---|---|
| `fail-first` | n(1 이상의 정수) | 그 requestId로 그 서비스에 든 횟수 k(1부터, 이번 것 포함)가 k ≤ n이면 처리하지 않고 500(본문 `{"fault":"fail-first",...}`) | `fail-first` +1 |
| `slow-first` | `<n>@<ms>`(둘 다 1 이상의 정수) | k ≤ n이면 ms만큼 잔 뒤 처리한다 | `slow-first` +1 |
| `outage` | `<fromMs>-<toMs>`(0 이상의 정수, from < to — 벽시계 ms) | 입구에 든 시각(시각 기록의 `startUs`)이 [from·1000, to·1000)이면 처리하지 않고 500 | `outage` +1 |

- 빈 값·없음 = 없음(공백만 있는 값도 없음). 한 서비스를 가리키는 지시는 하나만(둘이면 틀린 헤더). 그 밖의 꼴(모르는 서비스·kind, 0, 부호, 소수)은 틀린 헤더 → 서비스는 400, 분석기는 입력 오류.
- 공백: 쉼표로 나눈 지시 하나의 앞뒤와 `:`의 앞뒤에는 공백이 있어도 된다(`" payment : fail-first=1 "`). `=`의 앞뒤와 값 안에는 안 된다(`fail-first = 1`은 틀린 헤더). 빈 지시(끝의 쉼표 `…=1,`)도 틀린 헤더다.
- 든 횟수 k는 서비스가 requestId마다 세고, 위 층이 몇 번 다시 불렀든(위의 위 층이 다시 불렀든) 같은 카운터다. 헤더가 그 서비스에 `fail-first`·`slow-first`를 줄 때만 센다.
- 서비스 쪽 끝점: `GET /attempt-faults` → `{"fail-first":n,"slow-first":n,"outage":n}`, `DELETE /attempt-faults`(카운터와 든 횟수를 비운다).

### 8-2. 입력

| 파일 | 만드는 곳 | 형식 |
|---|---|---|
| `answers.jsonl` | k6 | 7-1절 그대로(`sentAtMs` 필수) + **`attemptFault`**(문자열, 필수 — 보낸 `X-Attempt-Fault` 그대로, `""` = 없음. 8-1의 문법) |
| `db/*.csv` | 실행 스크립트 | 2절 그대로 |
| `timing/timings-<서비스>.json` | 서비스 `GET /timings` | 7-1절 그대로. **한 층에 한 requestId의 기록이 여럿이어도 된다**(재시도로 여러 번 든 것) — 기록 하나 = 그 층의 업무 입구에 든 시도 하나 |
| `timing/attempts-order.json`, `timing/attempts-inventory.json` | 서비스 `GET /attempts` | JSON 배열. 원소 = 재시도 감싸개의 시도 하나: `requestId`(비지 않은 문자열), `callId`(정수), `attempt`(정수 ≥ 1), `startUs`·`endUs`(정수, 0 ≤ start ≤ end), `outcome`(`Completed` 또는 `(Failed|Unknown):\S+`), `delayBeforeMs`(정수 ≥ 0, 첫 시도는 0), `decision`(`success`·`not-retryable`·`retry`·`max-attempts`·`budget-denied`). 재시도가 꺼진 서비스는 `[]`. **원소의 차례는 보지 않는다** — 분석기는 `callId`로 묶고 `attempt`로 줄 세운 뒤 검사한다(뒤섞인 파일도 받는다). `callId`는 묶는 열쇠로만 쓴다(값의 범위·시작값은 보지 않는다 — 서비스 쪽 값은 `docs/retry.md` 5절) |
| `settings-order.json`, `settings-inventory.json` | 실행 스크립트(`GET /settings`) | JSON 객체. **`retry.enabled`(불)**만 읽는다 — 그 층의 재시도 감싸개가 떠 있었나(`docs/retry.md` 1절). 다른 칸은 읽지 않는다. `settings-payment.json`은 읽지 않는다 |
| `attempt-faults.json` | 실행 스크립트(세 서비스의 `GET /attempt-faults`를 모음) | `{"order":{"fail-first":n,"slow-first":n,"outage":n},"inventory":{...},"payment":{...}}` — 서비스 셋·종류 셋이 모두 있고 값은 0 이상의 JSON 정수, 여분의 키는 입력 오류 |

### 8-3. 입력 오류 (종료 코드 1, `report-retry.json`·`metrics-retry.tsv`를 쓰지 않고 있던 것은 지운다)

- 1·2절과 7-2절의 정답·CSV·시각 기록 검사(같은 읽기 코드) — 다만 "한 층에 한 requestId의 기록이 둘 이상"은 검사하지 않고(재시도하면 정상이다), "DB 행은 있는데 시각 기록이 없음"은 입력 오류가 아니라 **불일치**다(8-5 ③). 정답에 없는 requestId의 시각 기록은 입력 오류.
- 설정: `settings-order.json`·`settings-inventory.json`이 없음·비었음·JSON이 아님, 또는 `retry.enabled`가 JSON 불 값이 아님(없음, 문자열 등).
- 정답에 `attemptFault`가 없거나 문자열이 아니거나 8-1의 문법이 틀림. 같은 `requestId`가 정답에 두 줄 이상.
- 시도 기록: 위 표의 꼴이 아님, 정답에 없는 requestId, `Completed`인데 `decision`이 `success`가 아님(또는 거꾸로), 한 `callId`에 requestId가 둘 이상, `attempt`가 1부터 빈틈없이 이어지지 않음, 마지막이 아닌 시도의 `decision`이 `retry`가 아님, 마지막 시도의 `decision`이 `retry`, 다음 시도의 `startUs`가 앞 시도의 `endUs`보다 이름.
- 구간 장애(`outage`)의 지시가 정답들 사이에 둘 이상(실행마다 하나만 본다).
- 인자: `retry` 뒤에 `--run`(필수), `--expect` 짝. 기대 파일은 7-4절과 같은 꼴(`{"<이름>": {"min": 수, "max": 수}}`).

### 8-4. 지표의 식

분위수는 7-3절의 규칙(nearest-rank, p50·p90·p99·max, 0건이면 `count` 0과 `null`). t0 = 정답의 `sentAtMs` 가운데 **가장 이른 값**(파일의 첫 줄이 아니다 — 정답 줄의 차례는 보지 않는다). 시각 기록의 "든 시각" = `startUs ÷ 1000`(정수 나눗셈, ms).

**칸 지표가 무엇의 시각으로 나누나**(시계가 둘이다): `bins.<L>.*`와 `outage.callee*`는 **불린 층의 입구에 든 시각**(그 층의 시각 기록 `startUs`)으로, `edges.<U>-><L>.retriesPeakPer50ms`와 `outage.retriesPeakPer50msDuring`·`retryBinsDuring`·`retriesInCrowdedBinsDuring`·`retryTailMs`는 **부르는 층이 그 시도를 시작한 시각**(시도 기록 `startUs` — 보내기 직전)으로 나눈다. 한 시도의 둘은 연결과 전달에 든 시간만큼 다르다 — 칸 경계 가까이의 시도는 두 계열에서 다른 칸에 들 수 있다.

| 지표(`report-retry.json`의 자리) | 식 |
|---|---|
| `clients.completed`·`failed`·`unknown` | 4-1절의 판정마다의 정답 수 |
| `clients.successRatio` | `completed ÷ answers` |
| `clients.elapsedMs`, `clients.completedElapsedMs` | 모든 정답 / 완료인 정답의 `elapsedMs` 분위수 |
| `clients.noResponse` | `status` 0인 정답 |
| `clients.notReachedServer` | `status` 0이고 주문의 시각 기록이 없는 정답 — **늘 0이어야 한다**(8-5) |
| `layers.<L>.arrivals` | L의 시각 기록 수(L의 업무 입구에 든 시도 수) |
| `layers.<L>.arrivalsPerRequest` | `arrivals ÷ answers`(요청 하나에 L이 받은 호출의 배수) |
| `layers.<L>.requestsReached` | L의 시각 기록에 나오는 requestId 수 |
| `layers.<L>.repeatArrivals` | `arrivals − requestsReached`(같은 요청으로 다시 든 횟수 — 다시 한 일) |
| `layers.<L>.rows`, `processedRows` | L의 DB 행 수, 처리 행 수(4-2절) |
| `layers.<L>.doubled` | 처리 행이 둘 이상인 requestId 수(4-2절 `doubled`와 같다) |
| `layers.<L>.extraProcessedRows` | requestId마다 `max(0, 처리 행 − 1)`의 합 |
| `layers.<L>.processedNotCompleted` | 정답의 판정이 `completed`가 아닌 requestId의 처리 행 수(끝내 성공으로 끝나지 않은 요청에 쓴 일) |
| `doubledTotal` | 어느 층이든 처리 행이 둘 이상인 requestId 수(4-2절과 같다) |
| `edges.<U>-><L>.calls` | U의 시도 기록의 `callId` 수(감싼 호출 수) |
| `….attempts`, `….retries` | 시도 기록 수, 그 가운데 `attempt ≥ 2` |
| `….decisions.<결정>`, `….budgetDenied` | 결정마다의 수(다섯 모두, 0 포함), `budget-denied`의 수 |
| `….callsByAttempts.<k>` | 시도를 정확히 k번 쓴 호출 수(k = 1 … 가장 큰 `attempt`) |
| `….attemptsNotArrived` | `attempts − layers.<L>.arrivals`(보낸 시도 가운데 L의 입구에 들지 않은 수). **U의 재시도가 켜져 있을 때만 낸다**(`settings-<U>.json`의 `retry.enabled` — 꺼져 있으면 시도 기록이 없어 뜻이 없으므로 키가 없다). 켜져 있으면 늘 0이어야 하고 늘 견준다(8-5 ④) — 이 실험의 사슬에서는 모든 호출이 L의 입구에 닿는다(연결 거절·연결 타임아웃이 있으면 0이 아니게 되고, 그 실행은 불일치다) |
| `….delayMs.attempt<k>` | `attempt = k`인 시도의 `delayBeforeMs` 분위수(k ≥ 2) |
| `….gapMs.attempt<k>` | 같은 호출에서 시도 k의 `startUs` − 시도 k−1의 `endUs`(ms) 분위수 |
| `….gapMinusDelayMs` | 모든 k ≥ 2의 (실제 간격 − `delayBeforeMs`) 분위수 — 잔 시간이 계획보다 얼마나 길었나 |
| `….retriesPeakPer50ms` | `attempt ≥ 2`인 시도의 `startUs ÷ 1000`을 t0부터 50ms 칸(`⌊(t − t0) / 50⌋`)으로 나눈 칸마다의 수의 최대(없으면 0) |
| `attemptFaults.<서비스>.<종류>.counted` | `attempt-faults.json`의 값(서비스가 센 수) |
| `attemptFaults.<서비스>.<종류>.expected` | 기록에서 다시 센 수: `fail-first`·`slow-first` = 그 서비스를 그 종류로 가리킨 정답마다 `min(n, 그 서비스의 그 requestId 시각 기록 수)`의 합 · `outage` = 그 서비스를 `outage`로 가리킨 requestId의 그 서비스 시각 기록 가운데 `startUs`가 [from·1000, to·1000)인 수 |
| `mix.<라벨>` | 정답을 `attemptFault`(빈 값은 `none`)로 묶어: `count`, `completed`, `unknown`, `paymentArrivals`·`inventoryArrivals`(그 묶음의 requestId들의 시각 기록 수의 합), `elapsedMs` 분위수 |
| `bins.binMs` | 50 |
| `bins.<L>.peakPer50ms`, `peakPer1s` | L의 든 시각을 t0부터 50ms / 1000ms 칸으로 나눈 칸마다의 수의 최대 |
| `outage.startMs`·`endMs` | 구간 장애의 from·to − t0 |
| `outage.requestsSentDuring`, `completedSentDuring` | `sentAtMs`가 [from, to)인 정답 수, 그 가운데 완료 |
| `outage.calleeArrivalsDuring` | 장애를 넣은 서비스 S의 시각 기록 가운데 `startUs`가 [from·1000, to·1000)인 수 |
| `outage.calleePeakPer50msBefore`·`During`·`After` | S의 50ms 칸 가운데 칸 끝 ≤ from / from ≤ 칸 시작이고 칸 끝 ≤ to / 칸 시작 ≥ to인 칸의 최대(칸 시작 = t0 + 50·i) |
| `outage.retriesPeakPer50msDuring` | S의 바로 위 층의 `attempt ≥ 2` 시도 시작을 같은 칸으로 — 구간 안의 칸(위 `During`과 같은 조건)의 최대. 재시도가 든 칸이 없으면 0 |
| `outage.retryBinsDuring` | 같은 칸(구간 안) 가운데 **재시도가 하나 이상 든 칸만**의, 칸마다 든 재시도 수의 분위수(7-3절 규칙 — `count` = 재시도가 든 칸의 수, `p90` = 그 칸들의 p90, `max` = `retriesPeakPer50msDuring`과 같다). 빈 칸을 넣지 않는 까닭: 빈 칸의 수는 간격이 재시도를 얼마나 떨어뜨렸나에 따라 달라져, 넣으면 분위수가 칸의 높이가 아니라 빈 칸의 몫을 말한다 |
| `outage.retriesInCrowdedBinsDuring` | 같은 칸(구간 안)에 든 재시도 가운데, **15건 이상 든 칸**(꽉 찬 칸 — 문턱 15 = 실험 나의 무리 20건의 3/4, 코드의 `CROWDED_BIN`)에 든 것의 몫(0~1). 구간 안의 칸에 든 재시도가 없으면 `null`. "칸 최대"는 칸 하나의 극값이라 재시도가 흩어졌는지를 말하지 못한다 — 이 몫과 `retryBinsDuring`이 그것을 본다 |
| `outage.retryTailMs` | S의 바로 위 층의 `attempt ≥ 2` 시도 시작(ms)의 최대 − to. 재시도가 없으면 `null`. 음수면 구간이 끝나기 전에 재시도가 멎었다. **회복 시간이 아니다** — 재시도가 구간 뒤로 얼마나 이어졌나(간격 표와 시도 수로 정해지는 값)이고, 장애가 시각으로 끝나는 이 실험에서는 불린 쪽의 회복을 재지 않는다 |
| `outage.clientRecoveryMs` | 완료가 아닌 정답의 `sentAtMs` 최대 − to. 없으면 `null`. 음수면 그만큼 구간 끝 앞에 보낸 요청부터는 모두 완료됐다 |

- **구간 장애의 지시가 없는 실행**(정답의 `attemptFault`에 `outage`가 없다)에는 `outage` 키가 아예 없다(`outage.*`가 보고에도 `metrics-retry.tsv`에도 없다 — 기대 파일이 `outage.*`를 견주면 "실제 없음"). 칸이 하나도 없으면(든 것이 없으면) 칸 최대는 0이다.

`report-retry.json`에만 있고 `metrics-retry.tsv`에는 없는 것: `series` 전체(아래), `run`·`expect`·`differences`. 시간 흐름의 표는 `series`에서 만든다.

`series`의 꼴: `{"binMs": 100, "originSentAtMs": t0, "paymentArrivals": [...], "inventoryRetries": [...], "orderRetries": [...]}`. 배열 셋은 정수 배열이고 길이가 같다 — n = ⌊(last − t0) / 100⌋ + 1, last = 세 층의 "든 시각"과 정답의 `sentAtMs + elapsedMs`(소수 버림) 가운데 가장 늦은 것. i번째 칸 = [t0 + 100·i, t0 + 100·(i+1)). `paymentArrivals` = 결제의 든 시각, `inventoryRetries`·`orderRetries` = 그 층의 `attempt ≥ 2` 시도 시작(ms). 칸 밖(t0 앞)의 것은 세지 않는다.

### 8-5. 판정과 출력

- 출력: `<실행 폴더>/report-retry.json`, `<실행 폴더>/metrics-retry.tsv`(수와 `null`만 편 것 — 7-4절과 같은 꼴), 표준 출력에 한 줄 요약, 표준 오류에 불일치.
- **늘 견주는 것**(기대 파일이 없어도, 이 차례로):
  ① `clients.notReachedServer`가 0이 아니면 `clients.notReachedServer: 늘 0, 실제 <n>` — 부하 도구가 보낸 요청이 서버에 닿지 않았으면 그 실행은 조건의 부하가 아니다(task 02 R02-1에서 드러난 빈 곳).
  ② 서비스·종류마다(order → inventory → payment, `fail-first` → `slow-first` → `outage`) `counted ≠ expected`이면 `attemptFaults.<서비스>.<종류>: 서비스가 센 수 <c>, 기록에서 다시 센 수 <e>`. **이 대조가 잡는 것**: 시각 기록이 빠지거나 더해진 것, 실행 사이에 비워지지 않은 카운터. **잡지 못하는 것**: 주입 자체의 착오 — 서비스와 분석기가 같은 시각 기록(`startUs`, 같은 든 횟수)을 같은 규칙으로 두 번 세므로, 구간이 의도와 다른 시각에 놓였거나(부하 도구가 구간을 잘못 계산) 규칙이 의도와 달라도 둘은 같다. 구간이 의도대로 놓였는지는 `outage.startMs`·`endMs`·`requestsSentDuring`으로 따로 본다.
  ③ 층마다(order → inventory → payment) DB에 행이 있는 requestId 가운데 그 층의 시각 기록이 하나도 없는 것이 있으면 `layers.<L>: DB 행은 있는데 시각 기록이 없는 requestId <n>건([<앞의 셋>])` — 시각 기록이 빠지면 도착 수·배수·칸이 조용히 작아진다(task 03 리뷰 R03-4: 결제의 시각 기록을 `[]`로 바꾼 보정 실행이 종료 0으로 통과했다).
  ④ 부르는 층마다(order→inventory → inventory→payment): 재시도가 켜져 있는데(`settings-<U>.json`) 시도 기록이 비었으면 `edges.<U>-><L>: 재시도가 켜져 있는데(settings-<U>.json) 시도 기록이 없다`, 시도 기록이 있고 `attemptsNotArrived ≠ 0`이면 `edges.<U>-><L>.attemptsNotArrived: 늘 0, 실제 <n>`, 재시도가 꺼져 있는데 시도 기록이 있으면 `edges.<U>-><L>: 재시도가 꺼져 있는데(settings-<U>.json) 시도 기록이 <n>건`.
  그 뒤에 기대 파일의 차이(7-4절과 같은 꼴과 수의 표기).
- 종료 코드: 0 불일치 없음 · 1 입력 오류(8-3) · 2 불일치. `scripts/run.sh`의 종료 코드는 대조 분석·(시각 분석)·재시도 분석 가운데 큰 것(대조 분석이 1이면 재시도 분석을 돌리지 않는다).
- 실행 폴더에 더해지는 것(task 03의 시나리오): `timing/attempts-<서비스>.json`(결제는 늘 `[]`), `attempt-faults.json`, `expect.retry.json`(기대 파일을 줬으면), `report-retry.json`, `metrics-retry.tsv`. `config.env`에 `retry_expect`와 두 층의 재시도 조건(`order_retry_*`·`inventory_retry_*`). 조건 하나는 `scripts/task03.sh`, 모두는 `scripts/task03-all.sh`, 표는 `scripts/task03-table.sh`(`runs/task-03-index.tsv`와 `metrics-retry.tsv`), 묶기·다시 계산은 `scripts/task03-pack.sh`·`task03-recompute.sh`.

### 8-6. 알려진 한계

- 시계: 7-5절과 같다(한 컴퓨터의 같은 시계).
- 50ms 칸은 t0에 맞춘 고정 칸이다 — 한 무리가 칸 경계에 걸치면 두 칸으로 나뉘어 최대가 작게 나온다.
- 시도 기록과 시각 기록은 메모리에만 있다. 서비스가 다시 뜨면 사라진다.
- `outage`의 판정은 서비스의 시계로 한다. 부하 도구는 그 구간을 시나리오의 시작 시각(`exec.scenario.startTime`)에서 계산해 헤더에 싣는다 — 둘이 같은 시계라는 것에 기댄다.

## 9. 멱등 키 지표 (task 04) — `analyzer idem`

> task 04의 실험(`docs/experiments/task-04-design.md`)이 쓰는 지표. 멱등 키와 끊김 장애의 규칙은 `docs/idempotency.md`. 1~6절의 대조 분석은 그대로 돌고(두 번 처리됨·잃음은 거기서 — 맞추기 단계가 있으면 다시 보낸 것이 만든 행까지 든 끝 상태로), 이 절의 분석이 그 뒤에 따로 돈다(`scripts/run.sh`의 11단계 — 시나리오가 task 04의 것일 때). 재시도가 켜진 시나리오(`idem-retry-mix`)는 8절의 재시도 분석도 돈다.
> 1~8절의 형식과 동작은 바꾸지 않았다. 바뀐 것은 서비스 쪽 셋뿐이다: `GET /settings`에 `idempotency`가 더해졌고, 끊긴 요청(task 04의 `X-Drop-Fault`가 있을 때만)의 시각 기록 `status`가 0이며, 실행 스크립트가 부하 직전에 재시도 예산의 시작 토큰을 남긴다(R03-7 — 9-6).
> **첫 결과 뒤에 바꾼 것**(2026-11-24 20:30 — 첫 62개 실행의 보고는 바꾼 분석기로 다시 만들었다): 업무 행 id와 키 행의 `row_id`를 새 파일로 내보내 짝을 견준다(9-2 — 첫 62개에는 그 입력이 없어 `--no-row-ids`로 다시 계산했다, 그 보고에는 `keyLinks`가 없다), 동시 요청의 "나중에 든 것"에서 같은 μs의 쌍을 따로 센다, 맞추기의 후보를 정답에서 다시 센다, 끊은 수를 위 층이 본 결과와 견준다, 서비스가 센 409·422를 시각 기록과 견준다(9-4·9-5). 서비스 쪽에는 설정 하나(`chain.idempotency.in-progress-as-unknown`, 기본 꺼짐 — `docs/idempotency.md` 3-1절)가 더해졌다.

### 9-1. 넣는 장애 — `X-Drop-Fault`

`<service>:drop-after-commit=<n>[,…]` — 그 requestId로 그 서비스에 든 횟수 k(1부터, 이번 것 포함)가 k ≤ n이면 처리하고 마지막 커밋을 한 뒤 응답을 한 바이트도 보내지 않고 연결을 닫는다. 문법·공백·틀린 헤더는 `X-Attempt-Fault`(8-1)와 같은 규칙(`docs/idempotency.md` 4절). 해석하는 코드는 한 곳: `fault-header/…/DropFaultDirectives.kt`. 서비스 쪽 끝점: `GET /drop-faults` → `{"drop-after-commit":n}`, `DELETE /drop-faults`. 끊긴 요청의 시각 기록은 남고 `status`는 0이다.

### 9-2. 입력

| 파일 | 만드는 곳 | 형식 |
|---|---|---|
| `answers.jsonl` | k6 | 7-1절(`sentAtMs` 필수) + **`key`**(비지 않은 문자열 — 보낸 `Idempotency-Key`), **`copy`**(정수 ≥ 1 — 같은 요청을 두 번 보낸 것의 몇 번째, 한 번이면 1), **`attemptFault`**·**`dropFault`**(문자열, `""` = 없음 — 8-1·9-1의 문법), **`replayed`**(불 — 응답 헤더 `Idempotent-Replayed: true`였나. 판정에 쓰지 않는다). 같은 `requestId`가 여러 줄이어도 된다(같은 요청을 두 번 보낸 것) |
| `resends.jsonl` | k6(맞추기 단계 — 있을 때만) | 줄마다 `{"type":"resend", …}` — 정답 줄과 같은 칸(`type`만 다르다. `requestId`는 정답에 있어야 한다). **파일이 없으면 맞추기 단계가 없는 실행**이고 `reconcile`이 보고에 없다. 빈 파일 = 맞추기 단계는 있었는데 다시 보낼 것이 없었다 |
| `db/*.csv` | 실행 스크립트 | 2절 그대로 |
| `db/idempotency-<서비스>.csv` | 실행 스크립트 | 머리줄 `key,request_id,amount,state,response_status`. `key`·`request_id`는 비지 않은 값(비면 입력 오류), `amount`는 정수, `state` ∈ `IN_PROGRESS`·`COMPLETED`·`FAILED`·`UNKNOWN`, `response_status`는 세 자리 정수나 **빈 값**(빈 값 = 저장한 응답 없음 — `IN_PROGRESS`). 줄끝·따옴표·칸 수의 규칙은 2절과 같다. 행이 0개여도 머리줄은 있다. **정답에 없는 `request_id`는 입력 오류로 하지 않는다** — 그 키 행은 업무 행이 없으면 `keysWithoutRow`·`keyLinks`(늘 0)에서, 업무 행이 있으면 그 업무 행이 대조 분석의 "정답에 없는 행"(늘 0, 5-3)에서 불일치로 드러난다 |
| `db/row-ids-<서비스>.csv` | 실행 스크립트(2026-11-24 20:30 뒤의 실행 — `config.env`의 `row_ids_exported=yes`) | 머리줄 `id,request_id` — 그 층의 업무 행(`orders`·`reservations`·`payments`)마다 id와 requestId, id 차례로 낸다(분석기는 id의 차례를 견주지 않는다). `id`는 1 이상의 정수이고 겹치지 않는다, `request_id`는 비지 않는다. requestId의 차례가 `db/<업무>.csv`(2절)와 같아야 한다(다르면 두 내보내기가 서로 맞지 않는 것 — 입력 오류). 대조 분석이 읽는 `db/<업무>.csv`의 꼴은 바꾸지 않으려고 따로 낸다 |
| `db/idempotency-row-ids-<서비스>.csv` | 실행 스크립트(같은 실행) | 머리줄 `key,row_id` — 키 행마다 그 키 행이 가리키는 업무 행의 id(`docs/idempotency.md` 3절). `key`는 비지 않고 겹치지 않는다, `row_id`는 1 이상의 정수. key의 집합이 `db/idempotency-<서비스>.csv`와 같아야 한다(다르면 입력 오류) |
| `timing/attempts-<서비스>.json` | 서비스(8-2) | 주문·재고 가운데 **재시도가 켜진 층만**(`settings-<서비스>.json`의 `retry.enabled`) 필수 — 끊김을 그 아래 층에 대해 위 층이 본 수로 세려고(9-4 `observedAbove`). 형식과 검사는 8-3 |
| `idempotency-counts.json` | 실행 스크립트(세 서비스의 `GET /idempotency-counts`) | `{"order":{"new":n,"replayed":n,"in-progress":n,"mismatch":n,"resumed":n,"missing-key":n},"inventory":{…},"payment":{…}}` — 서비스 셋·종류 여섯 모두, 0 이상의 JSON 정수, 여분의 키는 입력 오류 |
| `drop-faults.json` | 실행 스크립트 | `{"order":{"drop-after-commit":n},…}` — 같은 규칙 |
| `attempt-faults.json` | 실행 스크립트 | 8-2절 그대로 |
| `timing/timings-<서비스>.json` | 서비스 | 8-2절 그대로(한 층에 한 requestId의 기록이 여럿이어도 된다) |
| `settings-<서비스>.json` | 실행 스크립트 | 세 서비스 모두 **`idempotency.enabled`(불)**, 주문·재고는 **`retry.enabled`(불)**도 읽는다(없거나 불이 아니면 입력 오류) |

### 9-3. 입력 오류 (종료 코드 1, `report-idem.json`·`metrics-idem.tsv`를 쓰지 않고 있던 것은 지운다)

- 1·2·7-2절의 정답·CSV·시각 기록 검사(같은 읽기 코드) — 다만 같은 `requestId`가 정답에 여러 줄인 것, 한 층에 한 requestId의 시각 기록이 여럿인 것은 받는다. 정답에 없는 requestId의 시각 기록은 입력 오류. **시각 기록이 빠진 것**(DB에 행이 있는데 그 층의 시각 기록이 없는 requestId, 보낸 것보다 주문의 시각 기록이 적은 것)은 입력 오류가 아니라 **불일치**다(9-5 ①·③).
- 정답·다시 보낸 줄의 task 04 칸이 없거나 형이 틀림(위 표), `attemptFault`·`dropFault`의 문법이 틀림. `attemptFault`에 `outage`가 있음(이 분석은 다루지 않는다).
- `resends.jsonl`: JSON 객체가 아님, `type`이 `resend`가 아님, 정답에 없는 requestId, `status`가 0도 아니고 100~599도 아님, `elapsedMs`가 0 이상의 수가 아님, `sentAtMs`가 음수, CR이 있음.
- `db/idempotency-*.csv`·`db/row-ids-*.csv`·`db/idempotency-row-ids-*.csv`·`idempotency-counts.json`·`drop-faults.json`·`attempt-faults.json`·(재시도가 켜진 층의) `timing/attempts-*.json`의 꼴과 서로의 맞음(위 표), 설정의 `idempotency.enabled`·`retry.enabled`가 불이 아님. `db/row-ids-*.csv`·`db/idempotency-row-ids-*.csv`가 없으면 입력 오류다(`--no-row-ids`를 준 때만 읽지 않는다). `--no-row-ids`를 줬는데 실행 폴더의 `db/`에 `row-ids-*.csv`·`idempotency-row-ids-*.csv`가 하나라도 있어도 입력 오류다(row_id를 내보낸 실행의 짝 대조를 건너뛰지 않게 — 파일이 있는지로만 가린다, `config.env`는 읽지 않는다).
- 인자: `idem` 뒤에 `--run`(필수), `--expect` 짝, 그리고 값 없는 `--no-row-ids`(한 번까지). 기대 파일은 7-4절과 같은 꼴. **`--no-row-ids`는 row_id를 내보내기 전의 실행(첫 62개)을 다시 계산할 때만 쓴다**(`scripts/task04-recompute.sh`가 `config.env`에 `row_ids_exported=yes`가 없는 실행에만 준다) — 주면 두 파일을 읽지 않고 `layers.<L>.keyLinks`와 그 대조(9-5 ④의 뒤 둘)가 보고에 없다(`rowIdsChecked` 0).

### 9-4. 지표의 식

분위수는 7-3절의 규칙. "보낸 것" = 정답 줄 + 다시 보낸 줄. "처리 행"은 4-2절.

| 지표(`report-idem.json`의 자리) | 식 |
|---|---|
| `enabled.<서비스>` | 설정의 `idempotency.enabled`(켬 1, 끔 0) |
| `rowIdsChecked` | 업무 행 id와 키 행의 `row_id`를 읽어 짝을 견주었나(1) — `--no-row-ids`면 0 |
| `clients.answers`·`requestIds` | 정답 줄 수, requestId 수 |
| `clients.completed`·`failed`·`unknown`·`noResponse` | 4-1절의 판정마다의 정답 줄 수, `status` 0인 줄 수 |
| `clients.notReachedServer` | requestId마다 max(0, 보낸 것의 수 − 주문의 시각 기록 수)의 합 — **늘 0이어야 한다**(8-4의 같은 이름과 같은 뜻을 requestId를 두 번 보내는 실행으로 넓힌 것) |
| `clients.replayed` | `replayed`가 참인 정답 줄 수 |
| `clients.status.<상태>` | 정답 줄의 `status`마다의 수 |
| `clients.elapsedMs` | 정답 줄의 `elapsedMs` 분위수 |
| `doubledTotal` | 어느 층이든 처리 행이 둘 이상인 requestId 수(4-2절과 같다) |
| `layers.<L>.arrivals` | L의 시각 기록 수 |
| `layers.<L>.rows`·`processedRows`·`doubled`·`extraProcessedRows` | 8-4절과 같다 |
| `layers.<L>.processedNotCompleted` | 정답에 `completed`가 하나도 없는 requestId의 L의 처리 행 수(부하 단계에서 클라이언트가 성공으로 받지 못한 요청에 L이 한 일) |
| `layers.<L>.processedClientFailed`·`processedClientUnknown` | 위를 클라이언트의 판정으로 가른 것: 그 requestId의 정답에 결과 모름(4-1)이 하나라도 있으면 `Unknown`, 아니면(모두 실패) `Failed`. 둘의 합 = `processedNotCompleted` |
| `layers.<L>.responses.409`·`422`·`0`·`0Repeat` | L의 시각 기록 가운데 `status`가 409·422·0인 것의 수(L이 낸 응답 — 0은 끊은 것). `0Repeat` = status 0인 기록 가운데 **같은 requestId의 다른 기록이 L에 그 기록보다 먼저(같은 μs 포함 — 다른 기록의 `startUs` ≤ 그 기록의 `startUs`) 든 것** — 다시 온 키라서 409·422였을 수 있는 끊긴 응답(409·422는 다시 온 키에만 나온다. 처음 든 요청을 끊은 기록은, 다른 기록이 그것이 끝나기 전에 들었어도 넣지 않는다. 이 실험은 키 = requestId) |
| `layers.<L>.keyLinks.*` | (`--no-row-ids`가 아닐 때만) `rowsNotLinked` = L의 업무 행 가운데 그 id를 가리키는 키 행이 없는 것, `rowsMultiLinked` = 둘 이상인 것, `keysToMissingRow` = 키 행 가운데 `row_id`가 L의 업무 행 id에 없는 것, `requestIdDiffers` = 키 행 가운데 가리키는 업무 행의 requestId가 키 행의 `request_id`와 다른 것. 꺼진 층에도 내지만(키 행이 없어 `rowsNotLinked` = 업무 행 수) 견주는 것은 켜진 층뿐이다(9-5 ④) |
| `layers.<L>.keyRows`, `keyStates.<상태>` | L의 `idempotency-<L>.csv`의 행 수, 상태마다의 수 |
| `layers.<L>.rowsWithoutKey` | L의 업무 행 가운데 그 requestId의 키 행이 L에 없는 것 |
| `layers.<L>.keysWithoutRow` | L의 키 행 가운데 그 `request_id`의 업무 행이 L에 없는 것 |
| `layers.<L>.counts.<종류>` | `idempotency-counts.json`의 값(서비스가 센 수) |
| `layers.<L>.selfMs` | L의 시각 기록마다 `end − start − (callEnd − callStart)`(call 칸이 없으면 `end − start`)의 분위수 — L 자기 몫의 시간(넣은 지연 포함) |
| `layers.<L>.serviceMs` | `end − start`의 분위수 |
| `dropFaults.<서비스>.counted`·`expected` | `drop-faults.json`의 값 / 기록에서 다시 센 수: requestId마다(그 requestId의 **첫 보낸 것** — 정답 파일에서 그 requestId가 처음 나오는 줄. 정답 줄은 다시 보낸 줄보다 늘 앞선다 — 의 `dropFault`로) 그 서비스를 가리키는 지시가 있으면 min(n, 그 서비스의 그 requestId 시각 기록 수)의 합 |
| `dropFaults.<서비스>.observedAbove` | 서비스 밖에서 본 끊김: 주문 = 보낸 것(정답 줄 + 다시 보낸 줄) 가운데 `status` 0인 것, 재고 = 주문이 재고를 부른 결과 가운데 `Unknown:IO_`로 시작하는 것, 결제 = 재고가 결제를 부른 결과 가운데 같은 것. "부른 결과"는 부르는 층의 재시도가 켜져 있으면 시도 기록(`attempts-<부르는 층>.json`)의 `outcome`, 꺼져 있으면 시각 기록의 `callOutcome`(재시도가 없으면 호출 하나 = 시도 하나) |
| `attemptFaults.<서비스>.<종류>.counted`·`expected` | 8-4절과 같은 식(requestId마다 첫 보낸 것의 `attemptFault`로) |
| `duplicates.*` | **정답 줄이 둘 이상인 requestId가 있을 때만**: `requestIds`(그 수), `pairs.<상태들>`(그 requestId들의 정답 `status`를 오름차순으로 `+`로 이은 것마다의 수 — 예 `201+409`), `secondCopyStatus.<상태>`(`copy` 2인 줄의 상태마다), `secondCopyReplayed`(`copy` 2인 줄이 `replayed`인 requestId 수), `laterArrivalStatus.<상태>`(주문의 시각 기록 가운데 `startUs`가 가장 늦은 것의 `status`마다 — 동시에 보낸 쌍에서 "나중에 든 것". **가장 늦은 둘의 `startUs`가 같으면 넣지 않고** `arrivalTies`로 센다), `arrivalTies`(그 수 — 같은 μs에 들어 나중을 가릴 수 없는 requestId), `arrivalGapMs`(정답 줄이 둘 이상인 requestId 가운데 주문의 시각 기록이 정확히 둘인 것의 두 `startUs`의 차 /1000 — 분위수, 같은 μs면 0), `conflict409.earlierGot409`·`laterGot409`·`sameMicros`(정답이 `201+409`이고 주문의 시각 기록이 정확히 둘인 requestId 가운데, 주문의 시각 기록에서 409를 낸 것이 먼저 든 것 / 나중에 든 것 / 둘이 같은 μs — 셋의 합은 그런 requestId 가운데 주문의 시각 기록에 409가 있는 것), `doubled`(그 가운데 두 번 처리됨) |
| `reconcile.*` | **`resends.jsonl`이 있을 때만**: `candidates`(정답에서 다시 센 다시 보낼 후보 — 9-6의 규칙 그대로: requestId마다 정답에 2xx가 하나도 없고 `status` 0 또는 504가 있는 것), `notResent`(후보 가운데 다시 보낸 줄이 없는 것), `resentNotCandidate`(다시 보낸 requestId 가운데 후보가 아닌 것), `resent`(다시 보낸 줄 수), `resentRequestIds`, `completed`(그 가운데 2xx), `replayed`, `status.<상태>`, `finalOnce`(다시 보낸 requestId 가운데 세 층의 처리 행이 모두 정확히 1인 것), `finalDoubled`(어느 층이든 2 이상), `finalMissing`(어느 층이든 0), `elapsedMs` |

### 9-5. 판정과 출력

- 출력: `<실행 폴더>/report-idem.json`, `<실행 폴더>/metrics-idem.tsv`(수와 `null`만 편 것 — 7-4절과 같은 꼴), 표준 출력에 한 줄 요약, 표준 오류에 불일치.
- **늘 견주는 것**(기대 파일이 없어도, 이 차례로):
  ① `clients.notReachedServer`가 0이 아니면 `clients.notReachedServer: 늘 0, 실제 <n>`.
  ② 서비스마다(order → inventory → payment) `dropFaults` counted ≠ expected이면 `dropFaults.<서비스>: 서비스가 센 수 <c>, 기록에서 다시 센 수 <e>`, counted ≠ observedAbove이면 `dropFaults.<서비스>: 서비스가 센 수 <c>, 위 층이 본 끊김 <o>`, 이어서 서비스·종류마다 `attemptFaults.<서비스>.<종류>: …`(8-5 ②와 같은 꼴). counted와 expected의 대조가 잡는 것과 잡지 못하는 것은 8-5 ②와 같다(같은 시각 기록을 두 번 센다) — 끊김을 넣었는데 그 요청이 응답을 내는 자리(`respond`)에 이르지 못하면(같은 서비스에 다른 장애를 함께 넣은 경우) 둘이 다르다. **observedAbove와의 대조는 서비스 밖의 관측**이라, 서비스가 끊었다고 셌는데 응답이 실제로 나간 것(Tomcat이 바뀌어 끊는 법이 듣지 않음)을 잡는다. 그 대신 끊김이 아닌 까닭의 `IO_` 실패나 status 0(k6의 요청 타임아웃 등), 위 층이 이미 타임아웃으로 포기한 뒤의 끊김(위 층은 `REQUEST_TIMEOUT`을 본다)이 있으면 둘이 달라진다 — task 04의 시나리오는 그런 섞음을 넣지 않으므로 다르면 원시 기록으로 까닭을 본다.
  ③ 층마다 DB에 행이 있는데 그 층의 시각 기록이 없는 requestId가 있으면 `layers.<L>: DB 행은 있는데 시각 기록이 없는 requestId <n>건([…])`(8-5 ③과 같다).
  ④ 층마다(order → inventory → payment): 멱등 키가 켜져 있으면 `layers.<L>.rowsWithoutKey: 늘 0, 실제 <n>`, `layers.<L>.keysWithoutRow: 늘 0, 실제 <n>`, `layers.<L>.counts.new: 키 행 <k>와 다르다(<n>)`, 그리고(`--no-row-ids`가 아니면) `layers.<L>.keyRows: 업무 행 <r>과 다르다(<k>)`, `layers.<L>.keyLinks.<이름>: 늘 0, 실제 <n>`(`rowsNotLinked` → `rowsMultiLinked` → `keysToMissingRow` → `requestIdDiffers`). 꺼져 있으면 키 행이 하나라도 있거나 센 수가 0이 아니면 `layers.<L>: 멱등 키가 꺼져 있는데(settings-<L>.json) …`.
  **이 대조가 받치는 것**: 켜진 층에서 업무 행과 키 행이 **하나씩 짝을 이룬다**(업무 행 수 = 키 행 수, 업무 행마다 그것을 가리키는 키 행이 정확히 하나, 같은 requestId) — 실행 끝의 DB 상태로. `rowsWithoutKey`·`keysWithoutRow`는 requestId의 집합만 견주어(이 실험은 키 = requestId), 같은 requestId에 업무 행이 둘 남고 키 행이 하나인 꼴(진 쪽의 되돌림이 듣지 않음)을 잡지 못한다 — `keyRows`·`keyLinks`가 그것을 잡는다. **받치지 못하는 것**: 두 행이 같은 트랜잭션에서 들어갔다는 것 자체(원자성). 그것은 코드(`docs/idempotency.md` 3절 — 같은 `@Transactional` 메서드, 0행이면 `setRollbackOnly`)와, 남은 행이 PENDING이 아님(대조 분석의 `pendingRows` 0)·두 번 처리됨 0이 함께 받친다. 첫 62개 실행에는 `keyLinks`가 없다(`--no-row-ids`) — 그 실행들에서 본 것은 `rows`·`keyRows`(보고에 있다)가 같다는 것까지다.
  ⑤ 층마다(④와 한 바퀴로 — 층마다 ④ 다음 ⑤, order → inventory → payment): `layers.<L>.responses.409` ≤ `counts.in-progress`, `responses.422` ≤ `counts.mismatch`, 그리고 넘친 몫의 합 (in-progress − 409) + (mismatch − 422) ≤ `responses.0Repeat`이 아니면 `layers.<L>.counts: in-progress <a>·mismatch <b>가 시각 기록의 409 <x>·422 <y>와 맞지 않는다(409·422였을 수 있는 끊긴 응답 <z>)`. 끊긴 요청은 시각 기록의 status가 0이라 그것이 409·422였는지 모른다 — 다시 온 키였을 수 있는 끊긴 응답만큼만 서비스가 센 수가 시각 기록보다 많아도 받는다(그런 것이 없으면 정확히 같아야 한다. 처음 온 키를 끊은 것 — 보정의 칸 1~3, 실험 가·라의 끊김 — 은 409·422일 수 없어 넣지 않는다). `X-Fault`의 `fail-after-commit`이 그 층의 409·422를 500으로 바꾸면 맞지 않는다(task 04의 시나리오는 넣지 않는다). `replayed`·`resumed`는 이렇게 견줄 시각 기록의 표시가 없어 견주지 않는다 — `replayed`는 "되돌려 주기로 한 수"다(`docs/idempotency.md` 3절).
  ⑥ (`resends.jsonl`이 있을 때) 후보의 집합과 다시 보낸 requestId의 집합이 다르면 `reconcile: 다시 보낼 후보 <c>건 가운데 다시 보내지 못한 것 <n>건, 후보가 아닌데 다시 보낸 것 <m>건` — 다시 보낸 기록이 빠지면 `finalOnce`·`resent`가 함께 작아져 맞춘 것처럼 보이는 것을 막는다.
  그 뒤에 기대 파일의 차이(7-4절과 같은 꼴).
- 종료 코드: 0 · 1 입력 오류(9-3) · 2 불일치. `scripts/run.sh`의 종료 코드는 대조·(재시도)·멱등 키 분석 가운데 큰 것(대조 분석이 1이면 이 분석을 돌리지 않는다).

### 9-6. 실행 폴더에 더해지는 것과 실행 차례

- 실행 차례(`scripts/run.sh`, task 04의 시나리오): 6절의 차례에 더해 — 설정 기록 뒤 세 서비스의 `idempotency`를 조건(`IDEMPOTENCY_ENABLED`)과 견줌(다르면 실패) → 워밍업(키가 켜져 있으면 워밍업 요청에도 키) → 상태 초기화에서 세 DB의 `idempotency_keys`도 비우고 `/drop-faults`·`/idempotency-counts`를 0으로 → **부하 직전에 주문·재고의 `GET /retry-budget`을 `retry-budget-start.json`에, 토큰을 `config.env`의 `order_retry_budget_start_tokens`·`inventory_retry_budget_start_tokens`에(예산이 꺼져 있으면 빈 값)** → 부하 → 가라앉기 → **(맞추기 단계가 있는 시나리오) 정답에서 requestId마다 2xx가 하나도 없고 결과 모름(`status` 0 또는 504)이 있는 것을 골라, 그 requestId의 `copy` 1 정답과 같은 헤더·키·금액으로 20/s로 다시 보낸다(k6 `PHASE=resend`) → 다시 가라앉기** → 내보내기(키 표, 센 수, 끊은 수, 시도마다의 실패) → 분석.
- 더해지는 파일: `db/idempotency-<서비스>.csv`, `db/row-ids-<서비스>.csv`·`db/idempotency-row-ids-<서비스>.csv`(2026-11-24 20:30 뒤의 실행 — `config.env`에 `row_ids_exported=yes`), `idempotency-counts.json`, `drop-faults.json`, `attempt-faults.json`, `retry-budget-start.json`(모든 시나리오), `resends.jsonl`·`resend-k6-*`(맞추기 단계), `expect.idem.json`(기대 파일을 줬으면), `report-idem.json`, `metrics-idem.tsv`. `config.env`에 `idempotency_in_progress_as_unknown`(20:30 뒤의 실행), `planned_answers`(정답 줄 수의 계획 — 같은 요청을 두 번 보내는 시나리오는 반복 수보다 많다. 대조 분석의 `--planned`가 이것), `idempotency_enabled`, `recon_expect`·`idem_expect`, `resend`, `resend_candidates`, `settled_after_resend_ms`.
- 대조 분석의 기대 파일은 `RECON_EXPECT`로 바꿔 줄 수 있다(키 켬/끔의 보정이 같은 시나리오라서 — 주지 않으면 6절 그대로 `load/expectations/<시나리오>.json`).
- 조건 하나는 `scripts/task04.sh`, 모두는 `scripts/task04-all.sh`, 표는 `scripts/task04-table.sh`(`runs/task-04-index.tsv`와 `metrics-idem.tsv`), 묶기·다시 계산은 `scripts/task04-pack.sh`·`task04-recompute.sh`.

### 9-7. 알려진 한계

- 시계: 7-5절과 같다.
- `laterArrivalStatus`는 주문의 입구에 든 시각으로 "나중"을 가린다 — 두 요청이 같은 μs에 들면 가릴 수 없어 `arrivalTies`로 따로 센다(첫 62개 실행의 보고를 만든 분석기는 그 쌍을 늘 "나중 = 목록의 첫 기록"으로 셌다 — 시각 기록은 요청이 **끝난** 차례로 쌓이므로 먼저 끝나는 409 쪽으로 치우쳤다. 다시 만든 보고는 새 규칙이다). 409를 받는 쪽은 입구에 든 차례가 아니라 키 행을 넣는 차례로 정해지므로 "나중에 든 것"이 "둘째"를 뜻하지 않는다.
- `selfMs`는 그 층의 필터 안 시간에서 아래 층 호출 시간을 뺀 것이다 — 아래 층 호출의 연결·전달 시간 가운데 호출 밖에서 쓴 몫(직렬화 등)은 자기 몫에 든다.
- 키 행·센 수는 실행 끝의 값이다. 실행 중 키의 상태가 어떻게 바뀌었는지는 남지 않는다(서비스가 센 수와 시각 기록의 `status`로만 본다).
