# communication-reliability-lab

서비스 사이의 동기 호출에서 타임아웃, 재시도, 멱등 키, 기한 전파를 어떻게 고르느냐가 실패·중복·지연·부하에 어떤 차이를 만드는지 재는 실험이다.

명세: `docs/plans/2026-11-23/communication-reliability-experiment/requirement-spec.md`

## 이 랩이 묻는 것

- 타임아웃 값에 따라 "실제로는 처리됐는데 실패로 본" 요청이 얼마나 생기나, 느린 아래 층이 위 층을 얼마나 묶나(task 02)
- 재시도 횟수·백오프·예산이 맨 아래 층의 요청 수와 회복에 주는 차이(task 03)
- 멱등 키가 없을 때와 있을 때의 이중 처리, 키 저장의 비용(task 04)
- gRPC의 기한·취소 전파가 HTTP의 미리 나눈 타임아웃보다 헛일을 얼마나 줄이나(task 05)

"잃음"과 "두 번 처리됨"은 응답이 아니라 **DB의 행**으로 판정한다. 그래서 먼저 측정 도구가 답을 아는 실행에서 맞는 수를 내는지 확인했다(보정 — `docs/calibration.md`).

## 구조

```
k6 (load/chain.js) ──HTTP──▶ order :8081 ──HTTP──▶ inventory :8082 ──HTTP──▶ payment :8083
                              │                     │                         │
                              ▼                     ▼                         ▼
                           order_db              inventory_db              payment_db     (PostgreSQL 18 한 인스턴스)
```

- **재고가 결제를 부르는 것은 호출의 깊이를 셋으로 만들려는 실험용 구조이지 설계의 권고가 아니다.** 실제 주문 흐름이라면 주문이 재고와 결제를 각각 부르는 꼴이 더 흔하다.
- 서비스마다 자기 데이터베이스를 쓴다. 각 층은 `PENDING` 행을 커밋하고, 아래 층을 부르고, 결과(`COMPLETED`/`RESERVED`, `FAILED`, `UNKNOWN`)를 커밋한다. 결제는 한 트랜잭션에 `APPROVED` 행 하나.
- task 01에는 재시도도 멱등 키도 없다. 같은 `requestId`가 두 번 오면 두 번 처리된다(분석기가 "두 번 처리됨"으로 센다).
- 장애는 요청 헤더 `X-Fault`로 넣는다(어느 서비스에서, 늦춤·오류 응답·커밋 뒤 실패). 헤더는 사슬을 따라 넘어가고, 서비스는 넣은 장애를 종류마다 센다(`GET /faults`).

| 폴더 | 내용 |
|---|---|
| `chain-link/` | 세 서비스가 같은 규칙으로 쓰는 것: 장애 헤더(`Fault.kt`), 요청 문맥과 입구 필터(`ChainRequest.kt`), 아래 층 호출(`NextLayerCall.kt` — 뒤에 gRPC 구현을 끼울 자리), 같게 두는 설정(`chain-link.yml`) |
| `order/`, `inventory/`, `payment/` | 세 서비스(Kotlin, Spring Boot 4.2, Spring MVC, `JdbcClient`) |
| `analyzer/` | 정답과 DB 상태를 대조하는 명령줄 분석기 |
| `load/` | k6 스크립트와 시나리오마다의 기대 파일 |
| `scripts/` | 띄우기·실행·내리기 |
| `runs/` | 실행마다의 기록(커밋한 것은 보정 실행뿐 — `.gitignore`) |
| `docs/measurement.md` | 측정 도구의 입력·출력 형식과 판정 규칙(분석기의 계약) |
| `docs/calibration.md` | 보정 실행의 결과와 편차 |
| `docs/dependencies.md` | 들인 것마다 필요한가·왜·선택지·버전, 꺼 둔 숨은 기본값 |

## 실행

필요한 것: Docker(Compose v2), k6, curl. **JDK는 필요 없다** — 빌드와 실행 모두 컨테이너 안(JDK 25)에서 한다.

```bash
scripts/up.sh                      # 이미지를 짓고 DB와 세 서비스를 띄운다(호스트 포트 8081~8083)
scripts/run.sh calibration 1       # 보정 실행 한 번 → runs/calibration-<시각>-r1/
scripts/run.sh duplicate 1         # 일부러 같은 requestId를 두 번 보내는 실행
scripts/test.sh                    # 테스트(컨테이너 안)
scripts/down.sh                    # 이 프로젝트의 컨테이너·네트워크·볼륨을 내린다
```

- `run.sh`의 종료 코드: 0 통과, 1 실행 실패(서비스가 뜨지 않음, 워밍업 실패, 입력 오류 등), 2 분석 결과가 기대(`load/expectations/<시나리오>.json`)와 다름.
- Docker 자원은 compose 프로젝트 이름 `communication-reliability-lab`으로만 만든다.
- DB 계정 `lab`/`lab`은 **로컬 실험용** 값이다(비밀 값이 아니다).
