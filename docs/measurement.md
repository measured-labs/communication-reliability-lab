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
- 테스트용 입구: `lab.reliability.analyzer.runAnalyzer(args: Array<String>): Int` — 프로세스를 끝내지 않고 종료 코드를 돌려준다(`main`은 이것을 부르고 `exitProcess`).

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
| (task 02) `config.env`에 더한 칸 | `k6_max_vus`, `lookup_rate_per_second`, `slow_label`, 조건 `order_to_inventory_timeout`·`inventory_to_payment_timeout`·`virtual_threads`, `timing_expect`, `discard_run`(반복 번호 0 = 서비스를 새로 띄운 직후의 버리는 실행 — `yes`) |
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

### 7-3. 지표의 식

분위수: nearest-rank, 천분율 `q`(500·900·990)에 대해 `(q·n + 999) / 1000`번째(정수 나눗셈, 1부터) — 4-5절과 같은 규칙에 p90을 더했다. 꼴 `{"count","p50","p90","p99","max"}`, 0건이면 `count` 0과 나머지 `null`. 시간은 ms(μs ÷ 1000).

| 지표(`report-timing.json`의 자리) | 식 |
|---|---|
| `delays.<서비스>` | 정답의 `fault`에서 그 서비스를 가리키는 `delay=<ms>` 값들의 분위수(넣은 지연) |
| `edges.<U>-><L>.timeoutMs` | `settings-<U>.json`의 `nextLayer.requestTimeoutMs` |
| `edges.<U>-><L>.calls` | U의 시각 기록 가운데 `callOutcome`이 있는 것의 수 |
| `edges.<U>-><L>.timeouts` | 그 가운데 `callOutcome == "Unknown:REQUEST_TIMEOUT"` |
| `edges.<U>-><L>.falseTimeouts` | 타임아웃 가운데 L의 DB에 그 requestId의 처리 행(4-2절)이 있는 것 |
| `edges.<U>-><L>.falseTimeoutRatio` | `falseTimeouts ÷ calls`(calls가 0이면 `null`) |
| `edges.<U>-><L>.outcomes` | `callOutcome`마다의 수 |
| `edges.<U>-><L>.callMs` | U가 잰 `callEnd − callStart`의 분위수 |
| `edges.<U>-><L>.injectedCalleeDelayAtLeastTimeout` | L에 넣은 지연 가운데 `timeoutMs` 이상인 것의 수(거짓 타임아웃의 아래 한계로 견주려는 것) |
| `layers.<L>.records`, `layers.<L>.serviceMs` | L의 시각 기록 수, `end − start`의 분위수(불린 쪽 응답 시간 — 넣은 지연 포함) |
| `giveUp(L, id)`(보고에 따로 나오지 않음) | 다음 가운데 가장 이른 것(없으면 정의되지 않음): k6가 응답을 받지 못했으면(`status` 0) `sentAtMs + elapsedMs` · L보다 위의 층 U마다 U의 `callOutcome`이 `Unknown:REQUEST_TIMEOUT`이면 U의 `callEnd` |
| `waste.<L>.wasted` | L의 시각 기록 가운데 `giveUp`이 정의되고 `end > giveUp`인 것 |
| `waste.<L>.wastedProcessed` | 그 가운데 L의 DB에 그 requestId의 처리 행이 있는 것 |
| `waste.<L>.startedAfterGiveUp` | 그 가운데 `start ≥ giveUp`인 것(위가 포기한 뒤에 시작한 일) |
| `waste.<L>.wastedMs`, `wastedMsSum` | 헛일 시간 `end − max(start, giveUp)`의 분위수와 합(소수 첫째 자리) |
| `samples.<서비스>.maxGapMs` | 이웃한 표본의 `atMs` 차의 최대 |
| `samples.<서비스>.maxInflight`·`maxActive`·`maxThreads`·`maxQueue`·`maxConnections` | 표본의 최대(그 칸이 모두 `null`이면 `null`) |
| `samples.<서비스>.saturatedMs` | `active ≥ maxThreads`인 표본 수 × `intervalMs`(`maxThreads`가 없으면 `null`) |
| `samples.<서비스>.meanLoadDuringSend` | `atMs`가 [첫 `sentAtMs`, 마지막 `sentAtMs`]인 표본의 `inflight + queue`(queue가 `null`이면 0) 평균 |
| `slow.startMs`·`endMs` | `--slow-label`과 `fault`가 같은 정답의 `sentAtMs` 최소·최대(느린 구간) |
| `slow.lookups.<before/during/after>` | 조회를 `sentAtMs < start` / `start ≤ sentAtMs ≤ end` / `> end`로 나눠 `count`, `failed`(`status ≠ 200`, 0 포함), `elapsedMs` 분위수 |
| `slow.normalChain.<before/after>`, `slow.slowChain` | 느린 라벨이 아닌 사슬 요청(앞·뒤)과 느린 요청의 4-1절 판정마다의 수 |
| `slow.baselineOrderLoad` | 주문 표본 가운데 `atMs < start`인 것의 `inflight + queue` 최대 |
| `slow.recoveryBySamplesMs` | `atMs > end`이고 `inflight + queue > baselineOrderLoad`인 주문 표본의 마지막 `atMs` − `end`. 없으면 0 |
| `slow.recoveryByClientsMs` | `sentAtMs > end`인 요청(느린 라벨이 아닌 사슬 요청과 조회) 가운데 "나쁜" 것 — 성공이 아님(사슬 2xx, 조회 200) 또는 `elapsedMs > 1000` — 의 마지막 `sentAtMs` − `end`. 없으면 0 |

### 7-4. 판정과 출력

- 출력: `<실행 폴더>/report-timing.json`(`run`, `expect`, `differences`와 위 지표), `<실행 폴더>/metrics.tsv`(지표를 `a.b.c<TAB>값` 한 줄씩 편 것 — 수와 `null`만, `null`은 빈 값), 표준 출력에 한 줄 요약, 표준 오류에 불일치.
- 늘 견주는 것: 서비스마다 `samples.<서비스>.maxGapMs ≤ 1000` — 넘으면 `samples.<서비스>.maxGapMs: 늘 1000 이하, 실제 <n>`(표본이 끊겼다).
- 기대 파일(`--expect`): `{"<metrics.tsv의 이름>": {"min": 수, "max": 수}, ...}`. 이름마다 `min ≤ 값 ≤ max`가 아니면(값이 없거나 `null`이어도) `<이름>: 기대 <min>~<max>, 실제 <값>`. 보정의 기대 파일: `load/expectations/cal-*.timing.json`(실행 스크립트가 `TIMING_EXPECT` 또는 `<시나리오>.timing.json`을 실행 폴더의 `expect.timing.json`으로 옮겨 넘긴다).
- 종료 코드: 0 불일치 없음 · 1 입력 오류(7-2) · 2 불일치. `scripts/run.sh`의 종료 코드는 대조 분석과 시각 분석 가운데 큰 것(대조 분석이 1이면 시각 분석을 돌리지 않는다).

### 7-5. 알려진 한계

- 시계: 세 서비스(컨테이너)와 k6가 한 컴퓨터의 같은 커널 시계를 쓴다는 것에 기댄다. 서버 여러 대로 나누면(task 06) 시계가 어긋난 만큼 `giveUp`과 `end`의 차가 틀린다 — 그때 다시 본다.
- `end`는 입구 필터를 나간 때다. 처리 코드가 예외로 끝나면 `status`는 필터를 나갈 때의 값이라 실제 응답(오류 페이지)과 다를 수 있다.
- 헛일은 "위가 포기한 뒤 아래가 쓴 시간"이다. 아래가 위의 포기를 알 길이 없으므로(취소가 전해지지 않는다) 이 실험의 아래 층은 늘 끝까지 일한다.
- 표본 간격 100ms보다 짧게 차고 빠지는 것은 보이지 않는다.
