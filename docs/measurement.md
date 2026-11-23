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
- 같은 서비스에 같은 `kind`가 두 번이면 틀린 헤더다. 빈 값(또는 헤더 없음) = 장애 없음.

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

서비스의 카운터: `GET /faults` → `{"delay":n,"error":n,"fail-after-commit":n}`(프로세스가 마지막 `DELETE /faults` 뒤 넣은 수). 요청이 그 서비스에 닿지 않으면(위 층에서 `error` 등) 그 서비스의 지시는 세지 않는다.

## 1. 정답 — `answers.jsonl`

한 줄 = 보낸 HTTP 요청 하나. UTF-8, 줄마다 JSON 객체 하나. 빈 줄은 없다.

| 필드 | 형 | 필수 | 뜻 |
|---|---|---|---|
| `type` | 문자열 | 예 | 늘 `"answer"` |
| `requestId` | 문자열 | 예 | 비지 않음. 같은 값이 여러 줄에 나올 수 있다(같은 id를 두 번 보낸 경우) |
| `fault` | 문자열 | 예 | 보낸 `X-Fault` 값 그대로. `""` = 장애 없음. 0절의 문법을 따른다 |
| `status` | 정수 | 예 | 받은 HTTP 상태. `0` = 응답을 받지 못함(k6 타임아웃, 연결 오류). 0이 아니면 100~599 |
| `errorCode` | 정수 | 예 | k6의 `error_code`(0 = 없음, 1050 = 요청 타임아웃). 판정에 쓰지 않고 기록만 |
| `elapsedMs` | 수 | 예 | 보내기 직전부터 응답(또는 타임아웃)까지의 경과(ms, k6 VU 안에서 잼). 0 이상 |
| `iteration`, `sentAt` | — | 아니오 | 기록용. 분석기는 읽지 않는다 |

그 밖의 필드는 무시한다. 줄의 차례는 뜻이 없다(완료 차례로 쌓인다).

k6는 `--log-output=file=<k6-log.txt> --log-format=raw`로 `console.log`를 파일에 쓰고, 같은 파일에 자기 경고(`Request Failed`)도 쓴다. 실행 스크립트가 `{"type":"answer"`로 시작하는 줄만 골라 `answers.jsonl`을 만든다(0줄이면 실행 실패).

## 2. DB 상태 — `db/orders.csv` · `db/reservations.csv` · `db/payments.csv`

PostgreSQL `\copy ... to stdout with (format csv, header)`의 결과. 첫 줄은 머리줄이고 정확히 아래와 같아야 한다. 행이 0개여도 머리줄은 있다.

| 파일 | 머리줄 | 판정에 쓰는 칸 |
|---|---|---|
| `orders.csv` | `request_id,amount,status` | `request_id`, `status` |
| `reservations.csv` | `request_id,status` | `request_id`, `status` |
| `payments.csv` | `request_id,amount,status` | `request_id`, `status` |

- 값에 따옴표(`"`)가 든 줄은 받지 않는다(이 실험의 값은 id·상태 낱말·정수뿐이다).
- 칸 수가 머리줄과 다르거나 `request_id`·`status`가 비면 입력 오류.

## 3. 장애 수 — `fault-counts.json`

`{"order":{"delay":n,"error":n,"fail-after-commit":n},"inventory":{...},"payment":{...}}` — 실행 끝에 세 서비스의 `GET /faults`를 모은 것. 서비스 셋과 종류 셋이 모두 있어야 하고 값은 0 이상의 정수.

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

| 전체 지표 | 세는 것(requestId 수) |
|---|---|
| `lostTotal` | `hasCompleted` 이고 어느 한 층이라도 `processed == 0` |
| `doubledTotal` | 어느 한 층이라도 `processed >= 2` |
| `layerMismatch`(층마다의 건수 일치) | 세 층의 `processed` 값이 모두 같지는 않다 |

- "잃음"의 기준은 "처리 행이 하나 이상"이다(정답 줄 수만큼이 아니다) — 같은 id를 k번 보내 모두 완료여도 행이 1개면 잃음이 아니다. 멱등 키(task 04)가 들어오면 그것이 맞는 결과이기 때문이다. 같은 id를 두 번 보내 행이 두 개면 `doubled`로 센다.
- 정답에 없는 행은 위 requestId 지표에 들어가지 않고 `orphanRows`로만 센다.

### 4-3. 라벨과 서명 — 종류마다의 건수

- 묶음의 **라벨** = 묶음의 `fault` 값(모두 같을 때). 서로 다르면 `"(mixed)"`.
- 묶음의 **서명** = `응답 | order | inventory | payment` 네 칸을 `" | "`(공백-세로줄-공백)로 이은 문자열.
  - 응답 칸: 묶음의 판정 낱말(`completed`/`failed`/`unknown`)을 사전순으로 정렬해 `+`로 잇는다. 예: `completed+completed`
  - 층 칸: 그 requestId의 그 층 행의 `status`(처리 행만이 아니라 모든 행)를 사전순으로 정렬해 `+`로 잇는다. 행이 없으면 `-`. 예: `COMPLETED+COMPLETED`, `-`
  - 예: `unknown | UNKNOWN | RESERVED | APPROVED`
- 라벨마다: 정답 줄 수(`answers`), requestId 수, 판정마다의 줄 수(`verdicts`), 서명마다의 requestId 수(`signatures`), `elapsedMs` 분위수.

### 4-4. 장애 수 대조

(서비스, 종류)마다 `injected` = 정답 줄들의 `fault`에 그 지시가 든 줄 수(줄마다 1, 지시의 `delay` 값은 보지 않는다), `counted` = `fault-counts.json`의 값. 아홉 칸 모두 같으면 `faultCountsMatch = true`.
한계: 요청이 그 서비스에 닿지 않으면 서비스는 세지 않으므로, 한 요청에 여러 서비스의 지시를 섞으면(예: `inventory:error,payment:delay=100`) 일치하지 않는 것이 맞다. 보정 실행은 요청마다 지시를 하나만 넣는다.

### 4-5. 처리 시간 분위수

`elapsedMs`로 nearest-rank: 오름차순 정렬한 n개에서 p 분위수 = `ceil(p·n)`번째 값(1부터 셈). `p50`·`p95`·`p99`·`max`와 `count`. 0건이면 값은 `null`. 묶음: 전체(`all`), 판정마다(`completed`·`failed`·`unknown`), 라벨마다.

## 5. 분석기 — 명령줄

```
analyzer --run <실행 폴더> [--expect <기대 파일>] [--planned <n>]
```

- 입력: `<실행 폴더>/answers.jsonl`, `<실행 폴더>/db/{orders,reservations,payments}.csv`, `<실행 폴더>/fault-counts.json`.
- 출력: `<실행 폴더>/report.json`, `<실행 폴더>/report.md`(같은 내용의 사람용 표), 표준 출력에 한 줄 요약, 표준 오류에 불일치 목록.
- 인자는 `--이름 값` 짝. 모르는 인자, 짝이 안 맞음, `--run` 없음, `--planned`가 1 이상의 정수가 아님 → 종료 코드 1.
- 테스트용 입구: `lab.reliability.analyzer.runAnalyzer(args: Array<String>): Int` — 프로세스를 끝내지 않고 종료 코드를 돌려준다(`main`은 이것을 부르고 `exitProcess`).

| 종료 코드 | 뜻 | 보고 파일 |
|---|---|---|
| 0 | 통과(불일치 없음) | 씀 |
| 1 | 입력 오류: 파일 없음, `answers.jsonl`이 0줄이거나 빈 줄·JSON 아님·필드 없음·형이 틀림·`type`이 `answer` 아님·`status` 범위 밖·`elapsedMs` 음수·`fault` 문법 틀림, CSV 머리줄·칸 수·빈 값·따옴표, `fault-counts.json` 꼴, 기대 파일이 없음·JSON 아님·빈 객체·모르는 키·`labels`의 꼴 | 쓰지 않음 |
| 2 | 불일치: `--planned`와 정답 줄 수가 다름, 또는 기대 파일과 다름 | 씀(`differences`에 목록) |

`--planned`가 없고 `--expect`도 없으면 잃음·두 번 처리됨이 있어도 0이다(그것은 결과이지 도구의 오류가 아니다).

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
    "lostTotal": 0, "doubledTotal": 0, "layerMismatch": 200,
    "layers": {
      "order": {"rows": 550, "processedRows": 250, "pendingRows": 0, "orphanRows": 0, "lost": 0, "doubled": 0,
                "failedButProcessed": 50, "unknownProcessed": 50, "unknownNotProcessed": 100},
      "inventory": {"...": "같은 꼴"}, "payment": {"...": "같은 꼴"}
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

객체 안 키의 차례는 뜻이 없다(지금은 사전순으로 나온다). `planned`가 없으면 `null`.

### 5-2. 기대 파일(`--expect`)

JSON 객체. 아래 키 가운데 **적은 것만** 견준다(하나 이상 적어야 한다). 모르는 키는 입력 오류.

| 키 | 형 | 견주는 값 |
|---|---|---|
| `answers` | 정수 | 정답 줄 수 |
| `lostTotal`, `doubledTotal`, `layerMismatch` | 정수 | 4-2 |
| `orphanRows`, `pendingRows` | 정수 | 세 층의 합 |
| `faultCountsMatch` | 불 | 4-4 |
| `labels` | 객체: 라벨 → (서명 → requestId 수) | **꼴 전체**가 같아야 한다 — 기대에 없는 라벨·서명이 실제에 있거나 그 반대여도 불일치(없는 쪽을 0으로 보고 견준다) |

불일치 한 건 = `differences`의 한 줄. 예: `lostTotal: 기대 0, 실제 1`, `labels['']['completed | COMPLETED | RESERVED | -']: 기대 0, 실제 1`.

## 6. 실행 폴더 — `runs/<시나리오>-<YYYYMMDDTHHMMSS>-r<번호>/`

| 파일 | 내용 |
|---|---|
| `config.env` | 시나리오, 반복 번호, 시각, git 커밋과 작업 트리가 깨끗했는지, 도착률·시간·계획한 수, k6 요청 타임아웃, 가라앉기 시간, 워밍업, k6 판, 호스트 CPU·메모리 |
| `compose.resolved.yaml` | `docker compose config` — 자원 제한, JVM 옵션(`-Djdk.httpclient.disableRetryConnect=true` 등), 층 사이 타임아웃 |
| `images.txt` | 쓴 이미지와 PostgreSQL 이미지의 다이제스트 |
| `settings-<서비스>.json` | 떠 있는 서비스가 실제로 쓴 설정(`GET /settings`): Tomcat 스레드·큐·연결 수, Hikari 풀·대기, 가상 스레드, 타임아웃, JDK HttpClient 시스템 속성 |
| `warmup-k6-log.txt`, `warmup-k6-stdout.txt` | 워밍업(모두 201이어야 실행이 이어진다) |
| `k6-log.txt`, `k6-stdout.txt`, `k6-summary.json` | k6 원본 출력 |
| `answers.jsonl` | 1절 |
| `db/*.csv` | 2절 |
| `fault-counts.json` | 3절 |
| `expect.json` | 견준 기대 파일의 사본(`load/expectations/<시나리오>.json`) |
| `report.json`, `report.md` | 5절 |

실행 차례(`scripts/run.sh`): 컨테이너 넷이 떠 있고 세 서비스가 UP인지 확인 → 설정 기록 → 워밍업(장애 없음) → 세 DB의 행을 비우고 카운터를 0으로(남은 행이 있으면 실패) → 부하 → 가라앉기(늦게 끝나는 지연 요청을 기다림) → 컨테이너가 여전히 떠 있는지 확인 → 내보내기 → 분석. 어느 단계든 실패하면 0이 아닌 코드로 멈춘다.
