# 들인 것 — 정말 필요한가, 왜, 선택지, 고른 것과 버전

> 고르는 차례(하네스의 "덜 만드는 순서"): ①정말 필요한가 ②이미 있나 ③JDK ④프레임워크 기본 기능 ⑤이미 들인 의존성 ⑥새 의존성.
> 조사 전문(선택지마다의 장단점, 링크): `docs/plans/2026-11-23/communication-reliability-experiment/research/dependencies-draft.md`. 아래는 그 가운데 정해진 것을 옮기고, task 01을 구현하며 더 정한 것을 같은 꼴로 더한 것이다.
> 버전은 2026-11-24에 다시 확인했다: Maven Central `maven-metadata.xml`(spring-boot 최신 4.2.0), `spring-boot-dependencies-4.2.0.pom`(관리 버전), Boot 4.2.0 jar의 `spring-configuration-metadata.json`(설정 이름과 기본값), services.gradle.org(9.9.2), Docker Hub 태그 API(이미지 태그), JDK 25 `java.net.http` module-summary(시스템 속성), k6 문서(타임아웃 기본값, error code 1050).

## 정해진 것(명세 "의존성 목록")

| 자리 | 고른 것 · 버전 | 정말 필요한가 · 왜 | 버린 선택지 |
|---|---|---|---|
| JDK | Temurin **25.0.5.1+2**, 컨테이너 안에서만(빌드 `eclipse-temurin:25.0.5.1_2-jdk-noble`, 실행 `…-jre-noble`) | 필요. 21은 가상 스레드가 `synchronized`에서 묶여(JEP 491 이전) 뒤의 스레드 비교가 흐려진다 | 21(이 컴퓨터의 것 — 건드리지 않는다) |
| Kotlin | **2.4.21** (`gradle.properties`) | 정해진 언어. Boot 4.2.0이 관리하는 판과 같다 | 2.5.0-RC(RC) |
| Spring Boot | **4.2.0** (Framework 7.1.0, Tomcat 11.0.27, Jackson 3.1.8, HikariCP 7.1.0, PostgreSQL JDBC 42.7.14) | 정해진 프레임워크. 지원 기간이 실험보다 길고 task 05의 gRPC를 Boot가 관리한다 | 3.5.x(공개 지원 끝), 4.1.3 |
| 빌드 | Gradle 래퍼 **9.9.2**, Kotlin DSL, 한 저장소의 다중 프로젝트 | 필요. 시스템 Gradle(8.14.6)에 기대지 않는다 | Maven |
| 서버 스레드 | 플랫폼 스레드(기본). 가상 스레드는 `VIRTUAL_THREADS=true` 하나로 켠다 | 새로 들일 것 없음 — 값만 명시(아래 "꺼 둔 숨은 기본값") | Jetty |
| HTTP 클라이언트 | JDK `HttpClient` 직접 (`chain-link/…/NextLayerCall.kt`) | 필요, 새 의존성 없음(③). 요청마다 타임아웃을 줄 수 있다 | RestClient(팩토리 단위 타임아웃), Apache HC5(기본 1회 재시도), OkHttp(`retryOnConnectionFailure=true`), WebClient(리액티브 스택) |
| DB | PostgreSQL **18.7** (`postgres:18.7`, 다이제스트는 실행마다 `images.txt`) | 판정의 바탕 | 17, 19.0 |
| DB 접근 | `JdbcClient` + `@Transactional` (spring-boot-starter-jdbc) | 필요, Boot 스타터(④). 무엇이 DB에 가는지 숨지 않는다 | JPA(쓰기 지연·flush가 숨는다), jOOQ, Exposed |
| 스키마 | 서비스마다 `schema.sql`, `spring.sql.init.mode=always` | 테이블은 필요, 판 관리는 필요 없다(실행마다 비운다) | Flyway, Liquibase |
| 장애 주입 | 요청 헤더 `X-Fault`를 읽는 직접 짠 필터 (`chain-link/…/Fault.kt`, `ChainRequest.kt`) | 요청 단위로 정확히 넣어야 한다 | Toxiproxy·`tc netem`(요청 단위가 아니다 — 필요해질 때 다시 본다) |
| 부하 | k6 **v2.4.0**(이 컴퓨터에 있음) 위에 새 스크립트 `load/chain.js` | 필요, 이미 있음(②). 도착률 스케줄을 직접 짜지 않는다 | 전부 Kotlin, Gatling, wrk2 |
| 분석기 | Kotlin, Gradle 모듈 `analyzer`(명령줄) | 명세 — 새로 짠다 | Go |
| 관측 | 직접 센 카운터(`GET /faults`) + Actuator health | 넣은 수와 센 수의 대조에 필요. 대시보드는 필요 없다 | Prometheus, Grafana, micrometer-registry-prometheus |
| 실행 | Docker Compose(2.40.3, 있음) + 멀티스테이지 Dockerfile | 필요, 새 의존성 없음 | Jib, Buildpacks, spring-boot-docker-compose |
| 테스트 | JUnit Jupiter 6.1.3 + kotlin-test (`kotlin-test-junit5`, `junit-platform-launcher`) — 분석기 모듈 | 명세 5절(분석기 단위 테스트). 테스트는 다른 워커가 쓴다 | Kotest, Strikt, AssertJ 따로 들이기, Testcontainers(task 04에서 다시) |
| 쓰지 않는 것 | Resilience4j, Spring Retry, `@Retryable`/`@EnableResilientMethods`, Spring Cloud, gRPC(task 05) | 명세 2절 — 재시도·백오프·예산·멱등 키는 직접 짠다 | — |

## 구현하며 더 정한 것

### Gradle 플랫폼(BOM)으로 버전 맞추기 — `io.spring.dependency-management` 플러그인을 들이지 않음

- 정말 필요한가: Boot가 관리하는 버전을 다섯 모듈에 맞추는 것은 필요하다. 그것을 하는 플러그인은 필요 없다.
- 선택지: (가) `io.spring.dependency-management` 1.1.7 — Boot 문서의 기본 예. 장점: 익숙하다. 단점: 플러그인이 하나 는다, Gradle의 해석 위에 한 층이 더 놓인다. (나) Gradle 기본 기능 `platform("org.springframework.boot:spring-boot-dependencies:4.2.0")` — 장점: 새 플러그인 없음(④), 분석기처럼 Spring이 아닌 모듈에도 같은 방식으로 쓴다. 단점: 모듈마다 한 줄씩 적는다.
- 고른 것: (나). `chain-link`는 `api(platform(...))`로 세 서비스에 넘기고, `analyzer`는 자기 줄을 둔다.

### `jackson-module-kotlin` (Boot 관리, Jackson 3.1.8 — `tools.jackson.module`)

- 정말 필요한가: 예. 세 서비스가 `{"amount":…}` 본문을 Kotlin 데이터 클래스(`AmountRequest`)로 받는다. 이 모듈이 없으면 Jackson이 기본 생성자 없는 Kotlin 클래스를 만들지 못한다.
- 선택지: (가) 이 모듈 — Spring Initializr가 Kotlin 프로젝트에 넣는 기본, Boot가 버전 관리. (나) `Map<String, Any>`로 받아 손으로 꺼낸다 — 의존성은 없지만 형 검사를 직접 짠다. (다) 본문 대신 헤더나 질의 변수 — 실제 서비스의 꼴과 멀어진다.
- 고른 것: (가). 내보내는 본문(`{"amount":N}`)은 문자열로 직접 만든다(매퍼를 쓰지 않는다).

### 분석기의 JSON: `tools.jackson.core:jackson-databind` (Boot 관리 3.1.8)

- 정말 필요한가: 예. 정답 줄(JSONL)·장애 수·기대 파일을 읽고 `report.json`을 쓴다.
- 선택지: (가) Jackson 3 databind — 이미 서비스 쪽에서 쓰는 것(⑤), 트리(`JsonNode`)로 읽어 필드의 형을 직접 검사할 수 있다(문자열 `"201"`을 정수로 몰래 바꾸지 않게). (나) kotlinx.serialization — 컴파일러 플러그인이 하나 더 는다. (다) 손으로 짠 파서 — 틀리기 쉽다.
- 고른 것: (가). CSV는 값이 id·상태 낱말·정수뿐이라 직접 나누고, 따옴표가 든 줄은 입력 오류로 낸다.

### Gradle `application` 플러그인 (Gradle 기본)

- 정말 필요한가: 분석기를 명령줄로 돌리려면 실행 스크립트와 클래스패스가 필요하다.
- 선택지: (가) `application` + `installDist` — Gradle 기본(④), 새 의존성 없음. (나) fat jar(Shadow 플러그인) — 새 플러그인. (다) Spring Boot `bootJar` — 분석기에 Spring을 들이게 된다.
- 고른 것: (가). 분석기 이미지는 `installDist` 결과를 JRE 이미지에 넣는다.

### 빌드 이미지: `eclipse-temurin:25.0.5.1_2-jdk-noble` + 래퍼 (`gradle:9.9.2-jdk25` 이미지가 아니라)

- 정말 필요한가: 컨테이너 안의 빌드 단계는 필요(JDK 25가 거기에만 있다).
- 선택지: (가) `gradle:9.9.2-jdk25-noble` — Gradle이 들어 있다. 단점: 그 안의 JDK 판이 실행 이미지(`25.0.5.1_2-jre`)와 같다는 보장이 태그에 없다. (나) 실행과 같은 Temurin 판의 JDK 이미지 + 래퍼 — 빌드와 실행의 JDK가 같은 판이다. 래퍼가 배포판을 받는 시간은 층 캐시로 한 번만 든다.
- 고른 것: (나). 래퍼 파일은 `gradle:9.9.2-jdk25-noble` 컨테이너에서 `gradle wrapper --gradle-version 9.9.2`로 만들었다(이 컴퓨터의 JDK를 쓰지 않으려고).
- 이 컴퓨터의 Docker에는 BuildKit(buildx)이 없어서 Dockerfile에 캐시 마운트를 쓰지 않고, 빌드 파일만 먼저 넣고 의존성을 받는 층을 따로 두었다.

## 꺼 둔 숨은 기본값

| 숨은 기본값 | 그대로 두면 | 한 것 | 있는 곳 |
|---|---|---|---|
| JDK HttpClient `jdk.httpclient.disableRetryConnect=false` | 연결 실패를 내 코드 밖에서 다시 시도한다 | `-Djdk.httpclient.disableRetryConnect=true`. 없으면 서비스가 뜨지 않는다 | `compose.yaml`(JAVA_TOOL_OPTIONS), `NextLayerCall.kt`(init의 `check`) |
| JDK HttpClient `enableAllMethodRetry=false` | (기본은 꺼짐) | 같은 값을 명시, 켜져 있으면 뜨지 않는다 | 같은 곳 |
| JDK HttpClient 요청 타임아웃 없음(무한) | 아래 층이 멈추면 위 층도 영원히 기다린다 | 요청마다 `timeout(requestTimeout)` | `NextLayerCall.kt`, `compose.yaml`(`NEXT_LAYER_REQUEST_TIMEOUT`) |
| JDK HttpClient 연결 타임아웃 없음 | OS의 연결 제한까지 기다린다 | 클라이언트에 `connectTimeout` | 같은 곳(`NEXT_LAYER_CONNECT_TIMEOUT`) |
| JDK HttpClient HTTP/2 시도, 연결 풀 무제한 | 프로토콜이 실행마다 같다는 보장이 없다 | HTTP/1.1 고정, 리다이렉트 NEVER, `connectionPoolSize=0`(무제한)을 명시 | `NextLayerCall.kt`, `compose.yaml` |
| **keep-alive — 클라이언트와 서버의 유휴 연결 시간.** JDK HttpClient `jdk.httpclient.keepalive.timeout`(초, 기본 **30** — 유휴 연결을 풀에 두는 시간, HTTP/1.1과 HTTP/2 모두) · Tomcat `keepAliveTimeout`(ms, 기본 = **`connectionTimeout`의 값** — 이 실험에서는 20s, Tomcat 자체의 `connectionTimeout` 기본은 60s) · Boot `server.tomcat.keep-alive-timeout`(기본값 없음 — "When not set the connectionTimeout is used") | 클라이언트가 30s 둔 연결을 서버가 20s에 닫는다. 그 사이 클라이언트가 닫힌 연결에 요청을 쓰면, 서버가 받지도 않은 요청이 "연결이 끊김 → 결과 모름(504)"이 된다(R01-10) | 둘 다 명시하고 **클라이언트 15s < 서버 20s**: `-Djdk.httpclient.keepalive.timeout=15`, `server.tomcat.keep-alive-timeout: 20s`. 실제 값은 `/settings`의 `jdkSystemProperties`와 `tomcat.keepAliveTimeoutMs` | `compose.yaml`, `chain-link.yml` |
| Tomcat 스레드 200 · 큐 100 · 연결 8192 · 연결 타임아웃(Boot 기본값 없음 → Tomcat 60s) | 값이 기록에 남지 않는다 | 같은 값을 명시, 연결 타임아웃 20s | `chain-link/src/main/resources/chain-link.yml` |
| Hikari 풀 10 · 연결 대기 30초 | 30초 대기가 층의 타임아웃(1~2초)보다 길어 타임아웃 실험이 DB 대기에 묻힌다 | 풀 10·최소 유휴 10·대기 1000ms | `chain-link.yml` |
| `spring.sql.init.mode=embedded` | PostgreSQL에서 `schema.sql`이 조용히 돌지 않는다 | `always`, `continue-on-error: false` | `chain-link.yml` |
| 가상 스레드 꺼짐 | — | `spring.threads.virtual.enabled: ${VIRTUAL_THREADS:false}` — compose에서 `"false"`를 명시 | `chain-link.yml`, `compose.yaml` |
| k6 요청 타임아웃 60초 | "결과 모름"의 기준이 층의 타임아웃과 무관해진다 | `REQUEST_TIMEOUT=5s`를 요청마다 | `scripts/run.sh`, `load/chain.js` |
| 컨테이너 재시작 정책 | 죽은 서비스가 되살아나 측정이 조용히 이어진다 | `restart: "no"`, 실행 앞뒤로 네 컨테이너가 떠 있는지 확인 | `compose.yaml`, `scripts/lib.sh` |

실제로 쓰인 값은 실행마다 `runs/<실행>/settings-<서비스>.json`과 `compose.resolved.yaml`에 남는다. `settings-*.json`은 설정 문자열이 아니라 떠 있는 객체에서 읽는다 — `HikariDataSource`, Tomcat 커넥터의 프로토콜 핸들러와 실행기, 아래 층 호출의 `HttpClient`와 요청(`docs/measurement.md` 6절). 설정 키가 적용되지 않았으면 그 파일에서 설정과 다른 값이 보인다.

keep-alive 두 설정의 출처(2026-11-24 확인):
- JDK 25 `java.net.http` 모듈 요약 — `jdk.httpclient.keepalive.timeout`(기본 30, "The number of seconds to keep idle HTTP connections alive in the keep alive cache"), `jdk.httpclient.connectionPoolSize`(기본 0 = 무제한): https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/module-summary.html
- Tomcat 11 HTTP 커넥터 — `keepAliveTimeout`("The default value is to use the value that has been set for the connectionTimeout attribute"), `connectionTimeout`(기본 60000, 배포판 `server.xml`은 20000): https://tomcat.apache.org/tomcat-11.0-doc/config/http.html
- Spring Boot 4.2.0 `server.tomcat.keep-alive-timeout`·`server.tomcat.connection-timeout`(둘 다 `Duration`, 기본값 없음): `spring-boot-tomcat-4.2.0.jar`의 `META-INF/spring-configuration-metadata.json`, 공개 목록 https://docs.spring.io/spring-boot/appendix/application-properties/index.html

### 장애 헤더 해석을 나눈 모듈 `fault-header` — 새 의존성 없음

- 정말 필요한가: 분석기가 정답의 `fault`를 서비스와 같은 규칙으로 읽어야 한다. 규칙을 두 곳에 두었더니 서로 달라졌다(같은 서비스의 같은 종류 두 번, 공백 — R01-4).
- 선택지: (가) 분석기가 `chain-link`에 기댄다 — Spring Boot 스타터가 분석기에 따라온다. (나) `chain-link`에 기대되 전이 의존성을 끊는다 — 같은 jar 안의 Spring 클래스가 클래스패스 없이 실려 깨지기 쉽다. (다) 해석 코드(`FaultKind`, `FaultDirective`, `FaultDirectives`)만 Kotlin 표준 라이브러리에만 기대는 Gradle 모듈로 옮기고, `chain-link`는 `api`로, 분석기는 `implementation`으로 그것에 기댄다 — 파일 하나를 옮기는 변경, 패키지 이름은 그대로.
- 고른 것: (다).

## task 02에서 정한 것 — 새 의존성 없음

### 층마다의 시각 기록 — 서비스 메모리 + 끝점(`GET /timings`), DB 표나 로그가 아니라

- 정말 필요한가: 예. 헛일(위가 포기한 뒤 아래가 계속한 일)은 층마다 요청의 시작·끝 시각과 위 층이 포기한 시각 없이는 셀 수 없다(명세 task 02 계약). DB의 `created_at`·`updated_at`은 트랜잭션 시작 시각(`now()`)이라 지연 장애로 자는 시간과 아래 층을 기다린 시간을 가르지 못한다.
- 선택지: (가) 서비스마다 시각 표를 하나 더 두고 요청마다 행을 쓴다 — 장점: 서비스가 죽어도 남는다. 단점: 요청마다 쓰기가 하나 늘어 Hikari 풀(10)을 더 쓴다 — 실험 나의 측정 대상(묶이는 자원)을 측정 도구가 흔든다. 판정용 CSV의 머리줄(2절)이 바뀌어 task 01의 계약과 테스트가 흔들린다. (나) 로그 줄로 남기고 `docker logs`에서 긁는다 — 단점: 로그 버퍼·순서·잘림을 따로 검사해야 한다. (다) 메모리에 쌓고 끝점으로 내보낸다 — 장점: 새 의존성·DB 부하 없음, 실행 스크립트가 받기 실패를 바로 실패로 낸다. 단점: 서비스가 다시 뜨면 사라진다(이 실험은 실행 중에 서비스를 죽이지 않는다).
- 고른 것: (다). 시각은 `Instant.now()`(μs) — 세 서비스와 k6가 한 컴퓨터의 시계를 쓴다(서버 여러 대로 나누면 다시 본다 — `docs/measurement.md` 7-5).

### 바쁜 스레드·대기 수 — 서비스 안에서 100ms마다 뜬 표본, Actuator 지표 끝점이 아니라

- 정말 필요한가: 예. 실험 나의 "묶이는 스레드와 대기 수의 시간 흐름"(명세).
- 선택지: (가) Actuator의 `metrics` 끝점(`tomcat.threads.busy` 등)을 열고 밖에서 주기적으로 묻는다 — 이미 있는 의존성의 기능이다. 단점: 묻는 요청이 바로 그 차 버린 Tomcat 스레드를 기다려야 해서, 스레드가 다 찬 순간에는 답이 늦거나 오지 않는다 — 재려는 때에 측정이 빠진다. 큐 길이(실행기의 대기 작업 수)는 기본 지표에 없다. (나) Prometheus 등록기 + 수집기 — 새 의존성과 컨테이너(명세가 들이지 않기로 함). (다) 서비스 안의 데몬 플랫폼 스레드 하나가 Tomcat 실행기(`org.apache.tomcat.util.threads.ThreadPoolExecutor`의 `getActiveCount`·`getPoolSize`·`getMaximumPoolSize`·`getQueue`)와 처리 중 요청 수를 읽어 메모리에 적고, 부하가 끝난 뒤 끝점(`GET /samples`)으로 받는다 — 새 의존성 없음, 스레드가 다 차도 계속 뜬다.
- 고른 것: (다). 메서드 이름과 뜻은 Tomcat 11 API 문서에서 확인했다(https://tomcat.apache.org/tomcat-11.0-doc/api/org/apache/tomcat/util/threads/ThreadPoolExecutor.html — `getQueue`는 "intended primarily for debugging and monitoring"). 가상 스레드 실행기(`VirtualThreadExecutor`)는 스레드 수·큐가 없어 그 칸은 `null`이고, 처리 중 요청 수(`inflight`)로 견준다.
- 고치며 알게 된 것: 커넥터를 `Tomcat.getConnector()`로 찾으면 서비스에 커넥터가 없을 때 기본 커넥터를 새로 만들어 붙인다. Boot가 초기화와 시작 사이에 커넥터를 떼어 두는 틈에 표본 스레드가 그것을 불러 8080을 두 번 열었고 기동이 "Port 8080 was already in use"로 실패했다. `service.findConnectors()`로 있는 것만 찾게 고쳤다(`chain-link/…/Observation.kt`).

### 요청마다의 지연을 분포에서 뽑기 — k6 스크립트 안의 작은 의사 난수(mulberry32 + Box–Muller)

- 정말 필요한가: 예. 실험 가는 넣은 분포의 분위수에 타임아웃을 맞추고, 반복과 조건마다 같은 요청에 같은 지연을 넣어야 한다.
- 선택지: (가) `Math.random()` + k6의 `randomSeed` — VU가 반복을 집어 가는 차례가 실행마다 달라 같은 요청 번호에 같은 값이 간다는 보장이 없다. (나) 외부 JS 라이브러리(jslib 등) — 새 의존성. (다) 요청 번호와 씨앗으로만 정해지는 의사 난수를 스크립트에 직접 짠다(20줄) — VU 차례와 무관하게 요청 i의 값이 같다.
- 고른 것: (다). 분포의 실제 분위수는 분석기가 정답에서 다시 센다(`delays.payment` — 그것이 맞는지의 확인이 된다).

### 조건을 바꾸는 방법 — `compose.yaml`의 환경 변수 + `/settings` 대조

- 타임아웃 둘과 스레드 방식을 `${ORDER_TO_INVENTORY_TIMEOUT:-2000ms}`·`${INVENTORY_TO_PAYMENT_TIMEOUT:-1000ms}`·`${VIRTUAL_THREADS:-false}`로 읽는다(기본값 = task 01의 값). 바꾼 값이 정말 걸렸는지는 실행 스크립트가 `settings-<서비스>.json`(떠 있는 `HttpClient` 요청과 Tomcat 실행기에서 읽은 값)과 견준다. Spring의 설정 키(`spring.threads.virtual.enabled`)와 Tomcat 설정은 task 01에서 확인한 것 그대로다.

## task 03에서 정한 것 — 새 의존성 없음

### 재시도 감싸개 — 직접 짠 코드(`chain-link/…/Retry.kt`), 라이브러리가 아니라

- 정말 필요한가: 예. 명세 task 03의 계약(층마다의 최대 시도, 다시 시도하는 갈래, 간격 넷, 토큰 버킷 예산, 시도마다의 기록).
- 선택지: (가) Resilience4j Retry·Spring Retry·Spring Framework 7의 `@Retryable` — 명세 2절이 쓰지 않기로 했다(라이브러리의 기본 동작이 아니라 규칙을 재려는 것이다). (나) 직접 짠다 — 규칙(`docs/retry.md`)이 코드 한 파일에 그대로 있고, 시계·난수·잠을 바꿔 끼울 수 있다(테스트 입구). 단점: 틀리면 우리 탓이다 — 규칙을 문서에 정확히 적고 다른 워커가 문서에서 테스트를 쓴다.
- 고른 것: (나). 시도를 보내는 것은 그대로 `HttpNextLayerCall`(JDK HttpClient, 자체 재시도 없음 — task 01에서 끈 숨은 기본값 그대로).

### 재시도 설정 — Spring `@ConfigurationProperties`(`chain.retry.*`), 직접 짠 설정 문자열 해석이 아니라

- 선택지: (가) 서비스마다 환경 변수 하나에 규칙을 한 줄로 적고(`max=3;backoff=…`) 직접 해석한다 — 환경 변수가 둘로 준다. 단점: 해석 코드를 새로 짜고 시험해야 한다. (나) Boot의 설정 바인딩(이미 있는 기능 — ④): 기간(`100ms` → `Duration`), 쉼표 목록 → `Set<enum>`, 낱말 → enum(Boot의 느슨한 enum 변환이 `exponential-jitter`를 `EXPONENTIAL_JITTER`로 받는다 — `/settings`에서 실제 값으로 확인). 단점: `compose.yaml`의 환경 변수가 서비스마다 아홉 줄.
- 고른 것: (나). 적용됐는지는 `/settings`의 `retry`(떠 있는 감싸개의 규칙 객체에서 읽은 값)를 `run.sh`가 조건과 견준다.

### 예산의 소수 — `BigDecimal`

- `Double`로 0.1을 열 번 더하면 0.9999…가 되어 "토큰 1개"의 경계가 한 칸 밀린다. 넣는 양이 소수인 예산(0.1)을 시험이 정확히 견줄 수 있게 `BigDecimal`(JDK — ③)로 더한다.

### 시도마다 넣는 실패 — 새 헤더 `X-Attempt-Fault`(직접 짠 필터의 한 갈래), `X-Fault`를 넓히지 않고

- 정말 필요한가: 예. "처음 몇 번의 시도는 실패한다"와 구간 장애는 요청 단위가 아니라 **시도** 단위의 장애다.
- 선택지: (가) `X-Fault`에 kind를 더한다 — 단점: `FaultKind`가 늘면 `/faults`의 키가 늘고, task 01의 분석기가 모르는 키를 입력 오류로 내는 계약(3절)과 그 테스트가 깨진다(task 01·02의 동작이 바뀐다). (나) 새 헤더와 새 카운터 끝점(`/attempt-faults`) — 헤더가 없으면 아무 일도 하지 않으므로 task 01·02의 경로가 그대로다. (다) Toxiproxy·`tc netem` — 요청·시도 단위가 아니다.
- 고른 것: (나). 해석은 `fault-header` 모듈(Spring 없음)에 두어 서비스와 분석기가 같은 코드를 쓴다(task 01 R01-4의 교훈).

### 무리 지은 도착 — k6 스크립트 안에서 기다렸다 보냄(`exec.scenario.startTime` + `sleep`), 새 실행기·`http.batch`가 아니라

- 정말 필요한가: 실험 나(지터)에만. 고르게 오는 부하에서는 실패도 고르게 나서 지터가 흩을 몰림이 없다.
- 선택지: (가) `http.batch`로 N건을 한 번에 — 단점: 요청마다의 경과를 따로 재기 어렵고 `batch`·`batchPerHost` 옵션을 더 맞춰야 한다. (나) `constant-arrival-rate`의 반복이 자기 무리의 시각(시나리오 시작 + (무리 + 1) × 500ms)까지 `sleep`한 뒤 보낸다 — 이미 쓰는 실행기, 요청마다 경과를 그대로 잰다. 2026-11-24에 k6 v2.4.0에서 20건이 같은 ms에 보내지는 것을 확인했다(`exec.scenario.startTime`은 시나리오 시작의 벽시계 ms).
- 고른 것: (나).

## task 04에서 정한 것 — 새 의존성 없음

### 멱등 키 — 직접 짠 코드(`chain-link/…/Idempotency.kt`)와 서비스마다의 표 `idempotency_keys`, 라이브러리가 아니라

- 정말 필요한가: 예. 명세 task 04의 계약(키를 업무 행과 같은 트랜잭션에, 다시 온 키는 처음의 결과, 동시에 온 같은 키의 동작 하나, 다른 금액은 거절).
- 선택지: (가) 라이브러리 — Spring에는 멱등 키 기능이 없고, 따로 된 것(예: 캐시에 응답을 두는 필터들)은 키를 업무 행과 **다른 저장소·다른 트랜잭션**에 둔다 — 계약("같은 트랜잭션")과 맞지 않고 명세 2절이 직접 짠다고 정했다. (나) Redis 같은 다른 저장소 — 새 컨테이너·새 의존성, 그리고 같은 트랜잭션이 될 수 없다. (다) 업무 표의 `request_id`에 유일 제약을 거는 것 — 가장 짧지만 "처음의 결과를 돌려준다"(응답 저장)와 "처리 중"·"결과 모름"을 가를 상태가 없다. (라) 서비스마다 같은 DB에 키 표를 두고 업무 행과 한 트랜잭션에 — 이미 있는 `JdbcClient`·`@Transactional`(④)과 PostgreSQL의 `insert … on conflict do nothing`(⑤ 이미 있는 DB의 기능).
- 고른 것: (라). 동시에 온 같은 키는 거절(409) — 까닭은 `docs/experiments/task-04-design.md` 1절. 트랜잭션을 되돌리는 것은 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`(Spring 기본 기능).
- 응답 본문의 JSON: 이미 들인 Jackson(`tools.jackson.databind.json.JsonMapper`)으로 문자열을 만들어 그 문자열을 저장하고 그대로 돌려준다(처음 응답과 되돌려 주는 응답이 같은 바이트가 되게).

### 커밋 뒤 끊김 — 새 헤더 `X-Drop-Fault`와 Tomcat 내부(`ResponseFacade`의 필드 + `setSuspended` + `ActionCode.CLOSE_NOW`), 프록시나 컨테이너 죽이기가 아니라

- 정말 필요한가: 예. R01-13 — 커밋한 뒤 위 층이 결과를 모르는 꼴이 보정 섞음에 없었다.
- 선택지: (가) 컨테이너를 죽인다 — 요청 단위가 아니고 처리 중인 다른 요청도 죽는다. (나) Toxiproxy·`tc netem`으로 연결을 끊는다 — 요청 단위가 아니고 "커밋한 뒤"라는 시점을 맞출 수 없다, 새 컨테이너. (다) 응답 전에 잠들어 위 층의 타임아웃을 부른다 — "결과 모름"은 되지만 끊김이 아니라 늦음이다(이미 `slow-first`가 한다). (라) 서블릿 안에서 응답을 보내지 않고 연결을 닫는다 — 서블릿 API에는 없어서 Tomcat 내부를 쓴다. 처음에는 `CLOSE_NOW`만 썼는데 응답(201과 본문)이 그대로 나갔다(2026-11-24 — `nc`로 바이트를 봄). Tomcat 11.0.27의 소스(`connector/OutputBuffer`의 `suspended`, `coyote/AbstractProcessor`·`Http11Processor`의 오류 상태)를 읽고 `setSuspended(true)`를 더해 0바이트가 되는 것을 `nc`와 `curl`(52 Empty reply)로 확인했다.
- 고른 것: (라). `X-Fault`에 kind를 더하지 않은 까닭은 task 03의 `X-Attempt-Fault`와 같다(task 01 분석기의 계약·테스트·커밋한 실행의 다시 계산). 단점: Tomcat을 올리면 다시 확인해야 한다(`DropFault.kt`의 `단순화:` 주석) — 보정 `cal-idem`이 끊긴 수·결과 모름을 건수까지 견주므로 조용히 바뀌지는 않는다.

### 맞추기 단계 — 같은 k6 스크립트의 두 번째 실행(`PHASE=resend`, `open()`으로 정답 파일을 읽음), 새 도구가 아니라

- 선택지: (가) 실행 스크립트에서 `curl`로 한 건씩 — 응답 헤더·경과·정답 줄의 꼴을 다시 짜야 한다. (나) 같은 k6 스크립트를 한 번 더(이미 있는 도구 — ②) — 같은 헤더를 만드는 함수와 정답 줄의 꼴을 그대로 쓴다.
- 고른 것: (나). 무엇을 다시 보낼지는 k6와 실행 스크립트(`jq` — 이 컴퓨터에 있음)가 같은 규칙으로 센다(0건이면 그 단계를 건너뛴다).

### Testcontainers — 들이지 않았다(task 04에서 다시 보기로 한 것)

- 정말 필요한가: 지금은 아니다. 멱등 키의 순수한 갈래(`IdempotencyRules.onRepeat`)·헤더 해석·분석기는 DB 없이 시험할 수 있고, 저장과 동시성(같은 트랜잭션, `on conflict`의 기다림, 409)은 보정 실행 `cal-idem`이 실제 PostgreSQL로 건수까지 본다. 테스트를 쓰는 워커가 SQL(`IdempotencyKeys`)을 단위로 시험해야 한다고 판단하면 그때 덜 만드는 순서대로 다시 본다(이 컴퓨터의 Docker를 테스트가 쓰게 되므로 `scripts/test.sh`의 빌드 방식도 함께 바뀐다).
