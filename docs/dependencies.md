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
