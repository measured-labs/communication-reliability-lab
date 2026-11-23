# 의존성 목록 초안 — 통신 장애 대응 실험 1: 동기 호출

> 작성: 2026-11-23 23:56 ~ 2026-11-24 00:05 KST · 대상 명세: `../requirement-spec.md`(초안) · 상태: **초안 — 사용자 확인 전**
> 읽는 법: 자리마다 "무엇을 위해 → 정말 필요한가 → 선택지 → 권하는 안 → 버전 → 측정에 주는 영향" 차례로 적었다. 버전과 기본값은 모두 이 문서를 쓰며 직접 열어 본 곳(링크)에서 옮겼다. 열지 못한 것은 "확인하지 못함"이라고 적었다.
> 고르는 차례(하네스의 "덜 만드는 순서"): ①정말 필요한가 ②이미 있나 ③표준 라이브러리(JDK) ④프레임워크 기본 기능(Spring) ⑤이미 들인 의존성 ⑥새 의존성.

---

## 0. 이 컴퓨터에 깔린 것 (2026-11-23 23:56 KST에 확인)

| 도구 | 버전 | 확인 명령 |
|---|---|---|
| JDK | Temurin 21.0.5+11 (2024-10-15) | `java -version` |
| Docker | 29.1.3 | `docker --version` |
| Docker Compose | 2.40.3 | `docker compose version` |
| k6 | v2.4.0 (go1.26.11) | `k6 version` |
| Go | go1.27.1 | `go version` |
| Gradle(시스템) | 8.14.6 (Kotlin DSL 2.0.21) | `gradle --version` |
| 떠 있는 컨테이너 | 없음 | `docker ps`(보기만 함) |

## 0-1. 두 참고 프로젝트가 실제로 쓴 것

| 항목 | http-grpc-lab | failure-alert-lab |
|---|---|---|
| Kotlin | 1.9.22 (`apiServer/build.gradle.kts:4`) | 2.3.21 (`services/order-service/build.gradle.kts:2`) |
| Spring Boot | 3.2.2 (`apiServer/build.gradle.kts:6`) | 4.1.2 (`services/order-service/build.gradle.kts:4`) |
| JDK | 21 (`apiServer/build.gradle.kts:15`) | 21 툴체인 (`order-service/build.gradle.kts:13`), 런타임 `eclipse-temurin:21.0.13_9-jre-noble` (`order-service/Dockerfile:8`) |
| Gradle 래퍼 | 8.14.3 (`apiServer/gradle/wrapper/gradle-wrapper.properties:3`) | 9.8.2 (`order-service/gradle/wrapper/gradle-wrapper.properties:3`) |
| gRPC | grpc-java 1.61.0, grpc-kotlin 1.4.1, protobuf 3.25.2, 플러그인 0.9.4 (`apiServer/build.gradle.kts:8,22-24`) | 없음 |
| HTTP 클라이언트 | WebClient(WebFlux) (`apiServer/build.gradle.kts:45`, `HttpDataClient.kt:117`) | RestClient + JDK HttpClient, 연결 1초·읽기 2초 (`order-service/.../order/Downstream.kt:38-45`) |
| 서버 스레드 | 기본(플랫폼) | 가상 스레드 켬 (`order-service/src/main/resources/application.yml:10-12`) |
| DB 접근 | 없음 | spring-boot-starter-jdbc + schema.sql, Hikari 20 (`order-service/build.gradle.kts:23`, `application.yml:17-21`) |
| DB | 없음 | `postgres:17` (`compose.yaml:22`) |
| 장애 주입 | `tc netem`(컨테이너에 NET_ADMIN) (`docker-compose.remeasure.yml:22-27`) | 요청 헤더 `X-Fault` (`order-service/.../support/Fault.kt:63,76,81`) + Toxiproxy 2.12.0 (`compose.yaml:47-54`) |
| 부하 | k6 스크립트 (`scripts/*.js`) | k6 `constant-arrival-rate` (`loadtest/k6/k3-storm.js:6`) |
| 분석기 | — | Go (`tools/analyzer/go.mod:3` go 1.27.1) |
| 관측 | Actuator (`apiServer/build.gradle.kts:29`) | Actuator + micrometer-registry-prometheus (`order-service/build.gradle.kts:22,27`), Prometheus v3.14.0 (`compose.yaml:110`, profile obs) |
| 이미지 | 공용 JRE 이미지에 jar를 붙임 (`scripts/remeasure/container/Dockerfile:4`) | 멀티스테이지 Dockerfile (`order-service/Dockerfile:2-11`) |
| 테스트 | spring-boot-starter-test (`apiServer/build.gradle.kts:48`) | kotlin-test-junit5 + Testcontainers postgresql (`order-service/build.gradle.kts:32-35`) |

---

## 1. JDK · Kotlin · Spring Boot · 빌드 도구

**무엇을 위해**: 세 서비스와(고르면) 측정 도구의 바탕.
**정말 필요한가**: 언어·프레임워크는 이미 정해졌다(Kotlin + Spring Boot MVC). 남은 것은 버전이다.

### 1-1. JDK

| 선택지 | 장점 | 단점 |
|---|---|---|
| 21 (지금 깔린 것) | 바로 쓴다. 두 참고 프로젝트와 같다 | **가상 스레드가 `synchronized` 안에서 막히면 바탕 스레드를 붙잡는다(pinning)** — 가상 스레드 조건의 수치가 이 현상에 흐려진다 |
| **25 (LTS)** | JEP 491로 `synchronized`가 거의 더는 붙잡지 않는다. 지원 기간 길다(2031-09-30) | 새로 깔아야 한다(래퍼 툴체인 또는 sdkman). 참고 프로젝트와 다르다 |

- **권하는 안: 25.** 2절(스레드)의 비교가 "가상 스레드가 묶이나"를 재는데, 21에서는 그 결과가 JDK의 옛 제약에서 온 것인지 구별이 안 된다.
- 버전: Temurin **25.0.5.1+2**(2026-11-18) — https://api.github.com/repos/adoptium/temurin25-binaries/releases/latest (확인 2026-11-24). Docker 태그 `eclipse-temurin:25.0.5.1_2-jre-*` 있음 — Docker Hub tags API (확인 2026-11-24). LTS 여부와 지원 끝: endoflife.date API "25 True … 2031-09-30".
- JEP 491 "Synchronize Virtual Threads without Pinning", Release **24**, Closed/Delivered — https://openjdk.org/jeps/491 . 찾은 문장: "This will eliminate nearly all cases of virtual threads being pinned to platform threads". 남는 경우: "if a virtual thread calls native code … then the virtual thread will be pinned".
- 측정에 주는 영향: JDK 버전은 "같게 둘 것"이다. 노트북과 서버에서 같은 이미지 태그를 쓴다.

### 1-2. Kotlin

- **권하는 안: 2.4.21** — Spring Boot 4.2.0이 관리하는 버전과 같다(`spring-boot-dependencies-4.2.0.pom`의 `kotlin.version` 2.4.21). 최신 안정판도 2.4.21(2026-10-08) — https://kotlinlang.org/docs/releases.html , GitHub `v2.4.21`. (2.5.0-RC는 RC라 쓰지 않는다.)
- JDK 25: "Starting with Kotlin 2.3.0, the compiler can generate classes containing Java 25 bytecode." — https://kotlinlang.org/docs/whatsnew23.html
- 측정에 주는 영향: 없음.

### 1-3. Spring Boot

| 선택지 | 장점 | 단점 |
|---|---|---|
| 3.5.x | 자료가 많다 | 공개 지원 끝(2026-06-30, endoflife.date). 내장 gRPC 없음 |
| 4.1.3 | failure-alert-lab(4.1.2)과 가깝다. 반년 넘게 쓰였다 | 공개 지원 2027-07-31까지 |
| **4.2.0** | 지금의 안정판(2026-11-19). 공개 지원 2027-12-31. **gRPC 스타터가 Boot 안에 있다**(4.1부터) | 나온 지 닷새 |

- **권하는 안: 4.2.0.** 실험 기간 안에 지원이 끝나지 않고, task 05의 gRPC를 Boot가 관리하는 버전으로 맞출 수 있다. 문제가 나오면 4.1.3으로 내리는 길이 짧다(같은 4.x).
- 확인: https://docs.spring.io/spring-boot/system-requirements.html — "Spring Boot 4.2.0 requires at least Java 17 and is compatible with versions up to and including Java 27", 빌드 "Gradle 8.x (8.14 or later) and 9.x", 서버 "Tomcat 11.0.x". Maven Central 최신 `spring-boot` 4.2.0, GitHub 릴리스 2026-11-19 (확인 2026-11-24).
- Boot 4.2.0이 관리하는 버전(같은 pom에서): Spring Framework 7.1.0 · Tomcat 11.0.27 · Jackson 3.1.8 · HikariCP 7.1.0 · PostgreSQL JDBC 42.7.14 · Flyway 12.11.0 · Micrometer 1.18.0 · JUnit Jupiter 6.1.3 · AssertJ 3.27.7 · Testcontainers 2.0.5 · Spring gRPC 1.1.2.
- 측정에 주는 영향: Boot 4에서 JSON은 Jackson 3(`tools.jackson`)이다 — failure-alert-lab과 같은 꼴(`order-service/build.gradle.kts:26`).

### 1-4. 빌드 도구

- **권하는 안: Gradle 래퍼 9.9.2(Kotlin DSL)** — 시스템 Gradle(8.14.6)에 기대지 않는다. 지금 버전: https://services.gradle.org/versions/current → `"version" : "9.9.2"` (확인 2026-11-24). Boot 4.2.0은 9.x를 지원한다(위 문서).
- Maven은 들이지 않는다(참고 프로젝트 둘 다 Gradle).
- 플러그인: `org.springframework.boot` 4.2.0, `io.spring.dependency-management` 1.1.7(Boot gRPC 문서의 예와 같음), `kotlin("jvm")`·`kotlin("plugin.spring")` 2.4.21.
- 측정에 주는 영향: 없음.

---

## 2. 서버의 스레드 방식과 내장 서버

**무엇을 위해**: task 02의 "느린 아래 층이 위 층의 스레드를 묶어 관련 없는 요청까지 실패시키나"를 재는 바탕.
**정말 필요한가**: 내장 서버는 Spring MVC에 딸려 온다(spring-boot-starter-webmvc → Tomcat). 따로 들일 것 없음. 정할 것은 기본 조건이다.

| 선택지 | 장점 | 단점 |
|---|---|---|
| **플랫폼 스레드(Boot 기본)** | 스레드 수에 상한(200)이 있어 "묶임 → 큐 → 거절"이 그대로 보인다. 흔한 운영 기본값 | 가상 스레드를 쓰는 곳의 모습과 다르다 |
| 가상 스레드(`spring.threads.virtual.enabled=true`) | 스레드가 묶여도 바로 고갈되지 않는다 | 묶임이 스레드가 아니라 DB 연결 풀·아래 층 연결로 옮겨 간다. 스레드 수 상한 설정이 듣지 않는다 |

- **권하는 안: 기본은 플랫폼 스레드, 가상 스레드는 task 02에서 "바꾼 한 가지"로 비교한다.**
- Tomcat 기본값(Boot 4.2.0 `spring-boot-tomcat-4.2.0.jar`의 `spring-configuration-metadata.json`에서 직접 읽음, 2026-11-24):
  - `server.tomcat.threads.max` = **200** — "Doesn't have an effect if virtual threads are enabled."
  - `server.tomcat.threads.min-spare` = 10
  - `server.tomcat.accept-count` = **100** — "Maximum queue length for incoming connection requests when all possible request processing threads are in use."
  - `server.tomcat.max-connections` = **8192**
  - `server.tomcat.connection-timeout` = 기본값 적혀 있지 않음
  - `spring.threads.virtual.enabled` = false (`spring-boot-autoconfigure-4.2.0.jar`)
- 측정에 주는 영향(숨은 기본값): 200·100·8192는 **실행 기록에 적는 값**이다. 노트북과 서버에서 같게 둔다. 가상 스레드를 켜면 200이 사라지므로 DB 풀(5절)과 HTTP 클라이언트 연결 수(3절)가 새 상한이 된다 — 비교할 때 이것도 적는다.
- 내장 서버를 Jetty로 바꾸는 것은 들이지 않는다(바꿀 까닭이 없다).

---

## 3. HTTP 클라이언트 (주문→재고→결제)

**무엇을 위해**: 층 사이 HTTP 호출. 타임아웃을 **요청마다**(남은 기한만큼) 줄 수 있어야 하고, **스스로 재시도하지 않아야** 한다(재시도는 직접 짠다).
**정말 필요한가**: 필요. 다만 JDK에 이미 있다(③).

| 선택지 | 요청마다 타임아웃 | 기본 재시도 | 기본 타임아웃·연결 풀 | 새 의존성 |
|---|---|---|---|---|
| **JDK `HttpClient` 직접** | 된다 — `HttpRequest.Builder.timeout()` | 연결 실패는 자동 재시도(끌 수 있음) | 응답 타임아웃 없음(무한), 풀 무제한, 유휴 30초 | 없음(③) |
| Spring `RestClient` + `JdkClientHttpRequestFactory` | 팩토리 단위(`setReadTimeout`). 요청마다 다른 값은 문서에서 찾지 못함 | 위와 같음(바탕이 JDK) | 위와 같음 | `spring-boot-starter-restclient`(④) |
| Apache HttpClient 5 | 된다(RequestConfig) | **기본 1회 재시도, 1초 간격** | 풀 기본값 문서에서 찾지 못함 | 새 의존성(⑥) |
| OkHttp 5 | 된다 | **`retryOnConnectionFailure = true`** | 연결·읽기·쓰기 각 10초, 전체 호출 0(없음) | 새 의존성(⑥) |
| `WebClient` | 된다(`timeout`) | 없음(직접 붙임) | Reactor Netty 기본값 | WebFlux 전체(⑥) — MVC 실험에 리액티브 스택이 섞인다 |

- **권하는 안: JDK `HttpClient`를 직접 쓰는 얇은 호출 코드.** 남은 기한을 요청마다 넘기는 것(미리 나눈 타임아웃, 기한 전파의 HTTP판)이 실험의 중심인데, JDK 클라이언트는 요청 하나에 `timeout`을 바로 단다. 새 의존성이 없다. RestClient는 팩토리 단위라 요청마다 값을 바꾸려면 팩토리를 여럿 만들거나 우회해야 한다. failure-alert-lab도 바탕은 JDK 클라이언트였다(`Downstream.kt:41`).
  - 대안(사용자가 고를 수 있음): RestClient + JDK 팩토리를 쓰고 층마다 고정 타임아웃만 잰다. 이 경우 "남은 기한 전파(HTTP)"는 재기 어렵다.
- 확인한 기본값:
  - `HttpRequest.Builder.timeout`: "The effect of not setting a timeout is the same as setting an infinite Duration, i.e. block forever." — https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html
  - `HttpClient.Builder`: `connectTimeout` 기본값은 적혀 있지 않음(안 주면 OS의 연결 제한까지 기다림 — 문서에 없는 추론이므로 반드시 준다). 리다이렉트 기본 `NEVER`. — https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpClient.Builder.html
  - 시스템 속성(https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/module-summary.html): `jdk.httpclient.keepalive.timeout` = 30(초) · `jdk.httpclient.connectionPoolSize` = 0("the cache is unbounded") · **`jdk.httpclient.disableRetryConnect` = false("If false, then retries are attempted")** · `jdk.httpclient.enableAllMethodRetry` = false("Whether it is permitted to automatically retry non-idempotent HTTP requests") · `jdk.httpclient.redirects.retrylimit` = 5("…when redirected or any failure occurs for any reason").
  - `JdkClientHttpRequestFactory.setReadTimeout`: "Default is the system's default timeout." (Spring Framework 7.1.0) — https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/client/JdkClientHttpRequestFactory.html
  - RestClient가 고르는 차례: "Apache HttpClient → Jetty → Reactor Netty → JDK client → Simple JDK client" — https://docs.spring.io/spring-boot/reference/io/rest-client.html . `spring.http.clients.connect-timeout`·`read-timeout`의 기본값은 비어 있다(Boot 4.2.0 메타데이터).
  - Apache HC5 `DefaultHttpRequestRetryStrategy()`: "a max retry count of 1", "default retry interval of 1 second", 429·503 재시도 — https://hc.apache.org/httpcomponents-client-5.6.x/current/httpclient5/apidocs/org/apache/hc/client5/http/impl/DefaultHttpRequestRetryStrategy.html (버전 5.6.5, Maven Central)
  - OkHttp: `connectTimeout`·`readTimeout`·`writeTimeout` = 10_000, `callTimeout` = 0("By default there is no timeout for complete calls"), `retryOnConnectionFailure = true` — https://github.com/square/okhttp/blob/master/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt (버전 5.5.1, Maven Central). 연결 풀 기본값은 확인하지 못함.
- **측정에 주는 영향 — 꺼야 하는 것**:
  1. `-Djdk.httpclient.disableRetryConnect=true` — 켜 두면 연결 실패가 내 재시도 코드 밖에서 한 번 더 일어나 "맨 아래의 요청 배수"가 틀어진다.
  2. 요청마다 `timeout`을 반드시 준다(안 주면 무한).
  3. `connectTimeout`을 반드시 준다.
  4. 연결 풀 크기(무제한)와 유휴 30초를 실행 기록에 적는다. 무제한이면 "아래 층 연결 수"가 상한이 되지 않는다 — 상한을 두고 재려면 풀 대신 동시 요청 수를 직접 막는 코드가 필요하다(task 02에서 정함).
  5. Apache HttpClient가 클래스패스에 들어오면 Boot의 자동 선택이 그쪽으로 바뀐다(위 차례). 다른 의존성이 끌고 오지 않는지 `dependencies`로 확인한다.
  6. HTTP/1.1로 고정한다(failure-alert-lab과 같이 `version(HTTP_1_1)`) — JDK 클라이언트 기본은 HTTP/2 시도다(이 문장은 이번에 문서에서 확인하지 못함 — 코드에서 명시해 기본값에 기대지 않는다).

---

## 4. gRPC (task 05)

**무엇을 위해**: 같은 사슬을 gRPC로 잇고, 기한과 취소 전파가 HTTP의 미리 나눈 타임아웃보다 헛일을 얼마나 줄이는지 잰다.
**정말 필요한가**: task 05에 필요. task 01~04에는 들이지 않는다.

| 선택지 | 장점 | 단점 |
|---|---|---|
| grpc-java만(서버·채널을 직접 만든다) | 숨은 설정이 가장 적다. 실행기(executor)·재시도를 코드에서 다 보인다 | 버전을 직접 맞춘다. 서버 수명 관리를 직접 짠다 |
| **Spring Boot gRPC 스타터(`spring-boot-starter-grpc-server`/`-client`, Spring gRPC 1.1.2 위)** | Boot 4.2가 버전을 관리한다. protobuf 플러그인의 protoc·grpc 버전을 Boot 플러그인이 맞춘다 | 관측(observation)·헬스·리플렉션이 기본으로 켜진다. 기본 서버 실행기는 grpc-java와 같다(아래) |
| + grpc-kotlin(코루틴 스텁) | Kotlin다운 `suspend` 호출 | 코루틴이 들어와 HTTP 쪽(스레드 모델)과 비교 조건이 달라진다. 1.5.0이 2025-09-16 이후 새 판이 없다 |

- **권하는 안: Spring Boot gRPC 스타터 + grpc-java의 blocking 스텁. grpc-kotlin(코루틴)은 들이지 않는다.** HTTP 쪽과 같은 "요청 하나 = 스레드 하나" 모델로 두어야 비교가 한 가지만 바꾼 것이 된다. 단, 스타터의 숨은 기본값(아래)을 다 끄거나 명시한다. task 05를 시작할 때 다시 본다.
- 버전:
  - Spring gRPC 1.1.2(Boot 4.2.0 관리), 그 안의 grpc-java **1.84.1**(`spring-grpc-core-1.1.2.pom`), protobuf-java **4.36.2**(`spring-grpc-dependencies-1.1.2.pom`). grpc-java 최신은 1.85.1(2026-10-20, GitHub). Boot가 맞춘 1.84.1을 쓴다.
  - 스타터 존재: Maven Central `spring-boot-starter-grpc-server` 4.1.0~4.2.0.
  - protobuf Gradle 플러그인: Boot 문서 예 `id 'com.google.protobuf' version '0.9.6'`, Maven Central 최신 0.10.1. **Boot 문서의 0.9.6을 쓴다**(0.10.1과 Boot의 맞춤이 되는지는 확인하지 못함).
  - grpc-kotlin 1.5.0 — https://github.com/grpc/grpc-kotlin/releases (쓰지 않음).
  - Boot gRPC 문서: https://docs.spring.io/spring-boot/reference/io/grpc.html — "Netty is used by default, listening on port 9090", 서블릿 안에서 돌리려면 `grpc-servlet-jakarta` + `server.http2.enabled=true`. → **권하는 안: 서블릿이 아니라 기본 Netty 서버(따로 포트)**. 서블릿에 얹으면 Tomcat 스레드와 섞여 HTTP와의 비교가 흐려진다.
- 기한·취소 전파(공식 문서):
  - "By default, gRPC does not set a deadline" — https://grpc.io/docs/guides/deadlines/
  - 기한 자동 전파: "in others it is enabled by default (e.g. Java and Go)" — 같은 문서. 서버는 기한이 지나면 호출을 `CANCELLED`로 만든다. 하지만 "the server application is responsible for stopping any activity it has spawned".
  - 취소: Java는 "Automatically cancels outgoing RPCs" — https://grpc.io/docs/guides/cancellation/ . `Context` 취소는 "will cascade to all descendant contexts", `fork()`는 넘기지 않는다 — https://grpc.github.io/grpc-java/javadoc/io/grpc/Context.html
  - 즉 **DB 작업 같은 "gRPC 밖의 일"은 자동으로 멈추지 않는다** — 헛일을 줄이려면 처리 코드가 `Context.current().isCancelled()`를 직접 보아야 한다. 이것이 task 05가 재는 차이다.
- **측정에 주는 영향 — 꺼야 하는 것**:
  1. 재시도: "Retries are enabled by default, but there is no default retry policy." "Even without an explicit retry policy, gRPC may perform transparent retries." — https://grpc.io/docs/guides/retry/ → 채널에 `disableRetry()`("designed for the case when users have their own retry implementation" — ManagedChannelBuilder javadoc 1.85.1). Spring에서는 `GrpcChannelBuilderCustomizer`로 건다(Spring gRPC 문서의 예가 `b.disableRetry()`).
  2. 서버 실행기: "If the user has not provided an executor when the server is built, the builder will use a static cached thread pool." — https://grpc.github.io/grpc-java/javadoc/io/grpc/ServerBuilder.html → **상한 없는 풀**이라 Tomcat(200)과 조건이 다르다. HTTP와 같은 크기의 고정 풀을 명시한다.
  3. 기본 기한: Spring gRPC의 채널 속성 `default.deadline`이 있으면 `DefaultDeadlineSetupClientInterceptor`가 기한 없는 호출에 기한을 붙인다(소스 v1.1.2, `if (callOptions.getDeadline() == null)`). 비워 두고, 기한은 코드에서 명시한다.
  4. `spring.grpc.client.observation.enabled`·`spring.grpc.server.observation.enabled` = true, `spring.grpc.server.reflection.enabled` = true, 헬스 갱신 5초(Boot 4.2.0 메타데이터). 측정 중에는 관측을 끄거나 켠 채로 HTTP 쪽과 같게 둔다.
  5. 채널 `idleTimeout` 기본 30분 — 실험 길이보다 길어 영향 없음(javadoc).
- 메시징 명세에의 걸림돌: 없음(gRPC는 메시징과 따로 선다).

---

## 5. DB와 접근 방식

**무엇을 위해**: 주문·재고·결제의 상태, 멱등 키 기록, 그리고 "두 번 처리됨/잃음"을 **DB 상태로** 판정(명세 2절).
**정말 필요한가**: 필요(판정의 바탕).

### 5-1. PostgreSQL

| 선택지 | 장점 | 단점 |
|---|---|---|
| 17 | failure-alert-lab과 같다 | 새 프로젝트라 맞출 까닭이 약하다 |
| **18 (18.7)** | 1년 넘게 쓰인 판 | — |
| 19 (19.0) | 가장 새 판 | 2026-11-19에 나왔다. .0이다 |

- **권하는 안: `postgres:18`(18.7).** 실험에서 DB 새 기능은 쓰지 않으므로 안정된 쪽.
- 확인: https://www.postgresql.org/support/versioning/ — 19 = 19.0(2026-11-19), 18 = 18.7, 17 = 17.12 (확인 2026-11-24). Docker 태그 `19.0`, `19-bookworm` 등 있음(Docker Hub API). 18의 정확한 이미지 태그(`18.7`)는 Docker Hub에서 확인하지 못함 — 이미지를 받을 때 다이제스트를 실행 기록에 남긴다.
- 서비스마다 DB를 나눈다(failure-alert-lab의 `infra/postgres/init/01-databases.sql`처럼 한 서버에 DB 여럿). 서버를 여럿 둘지는 10절.

### 5-2. 접근 방식

| 선택지 | 장점 | 단점 |
|---|---|---|
| **Spring `JdbcClient`(spring-boot-starter-jdbc)** | SQL이 코드에 그대로 보인다. `INSERT … ON CONFLICT`로 멱등 키를 기록하고 같은 트랜잭션에서 변경 — 무엇이 DB에 가는지 숨지 않는다. failure-alert-lab과 같다 | 매핑을 손으로 쓴다 |
| JPA(Hibernate) | 매핑이 적다 | 쓰기 지연·1차 캐시·flush 시점이 숨어 "키 저장이 더하는 쓰기 지연"(task 04)을 흐린다 |
| jOOQ 3.21.x | 타입 안전 SQL | 코드 생성이 더해진다(⑥) |
| Exposed 1.6.1 | Kotlin다움 | 새 의존성(⑥), Spring 트랜잭션과의 맞춤을 따로 봐야 한다 |

- **권하는 안: `JdbcClient` + `@Transactional`(또는 `TransactionTemplate`).** 새 의존성 없음(Boot 스타터 ④). 대조 분석기가 DB를 셀 때도 같은 SQL을 쓴다.
- JDBC 드라이버: `org.postgresql:postgresql` 42.7.14(Boot 관리; Maven 최신 42.7.15).
- 연결 풀 HikariCP 7.1.0(Boot 관리, 스타터에 딸려 옴) 기본값 — https://github.com/brettwooldridge/HikariCP : `maximumPoolSize` **10**, `minimumIdle` = maximumPoolSize, **`connectionTimeout` 30000ms**, `idleTimeout` 600000ms, `maxLifetime` 1800000ms, `keepaliveTime` 120000ms, `validationTimeout` 5000ms, `autoCommit` true. Boot의 `spring.datasource.hikari.maximum-pool-size`는 기본값을 따로 두지 않는다(메타데이터).
- **측정에 주는 영향 — 숨은 기본값**: `connectionTimeout` 30초는 "연결을 30초까지 기다리는 숨은 타임아웃"이다. 층의 타임아웃보다 길면 타임아웃 실험이 DB 대기에서 흐려진다 → 명시해서 적는다. 풀 크기 10도 명시한다(가상 스레드 조건에서는 이것이 실제 상한이 된다 — 2절).
- 메시징 명세에의 걸림돌: Outbox는 같은 JdbcClient 트랜잭션에 행 하나를 더하면 되므로 걸림돌 없음.

---

## 6. 스키마 관리

**무엇을 위해**: 테이블(주문·재고·결제·멱등 키) 만들기와 실행마다 초기화.
**정말 필요한가**: 테이블은 필요. 이전 판 관리(migration)는 필요 없다 — 실험 DB는 실행마다 비우고 새로 만든다.

| 선택지 | 장점 | 단점 |
|---|---|---|
| **`schema.sql`(Boot 기본 기능)** | 의존성 없음. failure-alert-lab과 같다 | 판 관리 없음(이 실험엔 필요 없음) |
| Flyway 12.11.0(Boot 관리) | 판 관리 | `spring-boot-starter-flyway` + `flyway-database-postgresql` 두 개를 들인다 |
| Liquibase | 판 관리, 여러 형식 | 더 무겁다 |

- **권하는 안: `schema.sql`, Flyway·Liquibase는 들이지 않는다.**
- 확인: https://docs.spring.io/spring-boot/how-to/data-initialization.html — "By default, SQL database initialization is only performed when using an embedded in-memory database. To always initialize an SQL database, irrespective of its type, set `spring.sql.init.mode` to `always`." Boot 4.2.0 메타데이터: `spring.sql.init.mode` 기본 `embedded`.
- **측정에 주는 영향**: PostgreSQL에서는 `spring.sql.init.mode=always`를 주지 않으면 **스키마가 조용히 안 만들어진다**. 실행 사이의 초기화(TRUNCATE·재고 채우기)는 서비스 기동이 아니라 측정 도구가 실행 앞에 명시적으로 한다(정답 기록과 묶기 위해).

---

## 7. 장애 주입

**무엇을 위해**: 느린 응답·실패·끊김·죽임을 넣고, 넣은 수와 관측한 수를 대조(명세 8절 가정 2).
**정말 필요한가**: 필요. 다만 "요청 단위로 정확히"가 조건이다.

| 선택지 | 요청 단위로 정확히 | 넣을 수 있는 것 | 비용 |
|---|---|---|---|
| **요청 헤더로 넣는 직접 짠 코드** | **된다**(요청 id마다) | 지연(앞·뒤), 오류 응답, "처리 후 응답 버림"(결과 모름 만들기) | 서비스에 작은 필터 하나. failure-alert-lab `X-Fault`(`Fault.kt:63,81`)와 같은 꼴 |
| Toxiproxy 2.13.0 | 안 된다 — TCP 수준, `toxicity`는 연결의 확률 | 지연·지터, 끊기(reset_peer), 대역폭, 데이터 막기(timeout) | 컨테이너 하나 + 경로마다 프록시 |
| `tc netem` | 안 된다 — 인터페이스 전체 | 지연·손실 | 컨테이너에 `NET_ADMIN`(http-grpc-lab `docker-compose.remeasure.yml:23`) |
| 컨테이너 죽이기(`docker compose kill`) | 시점만 정확 | 프로세스 죽음 | 없음(compose 기본) |

- **권하는 안: 헤더로 넣는 직접 짠 코드를 기본으로. 컨테이너 죽이기는 compose 명령으로(이 프로젝트의 compose 이름 안에서만). Toxiproxy·`tc netem`은 지금 들이지 않는다** — "연결이 안 맺어짐(connect timeout)"이나 "중간에 끊김"을 재야 할 때 그 task에서 다시 정한다(그때 후보 1순위 Toxiproxy: 경로별로 걸 수 있고 failure-alert-lab에서 써 봤다).
- 확인: Toxiproxy 릴리스 v2.13.0(2026-10-14) — https://api.github.com/repos/Shopify/toxiproxy/releases/latest . 기능과 `toxicity` = "probability of the toxic being applied to a link (defaults to 1.0, 100%)", TCP 수준 — https://github.com/Shopify/toxiproxy . ghcr 이미지 태그 `2.13.0`은 확인하지 못함.
- 측정에 주는 영향: 헤더 방식은 넣는 코드 자체가 처리 경로에 있으므로, 장애를 넣지 않는 실행에서도 필터는 똑같이 돈다(조건 사이에 같게 둠). 헤더가 아래 층으로 전달되는지(층별 지정)를 정답 기록에 남긴다.
- 메시징 명세에의 걸림돌: 브로커 쪽 장애(브로커 죽임, 네트워크 끊김)는 헤더로 못 넣는다 — 그때 Toxiproxy를 들일 가능성이 높다. 지금 들이지 않아도 걸림돌은 아니다.

---

## 8. 부하 도구와 측정 도구의 언어

**무엇을 위해**: 정해진 도착률(open model)로 보내고, 요청마다 정답(보낸 것·멱등 키·결과·지연)을 기록하고, DB 상태와 대조.
**정말 필요한가**: 필요. 명세는 "새로 짠다"로 정했다 — 남은 것은 "보내는 부분"까지 새로 짤지, 이미 있는 부하 도구 위에 스크립트를 새로 짤지다.

| 선택지 | open model | 요청마다 기록 | 장점 | 단점 |
|---|---|---|---|---|
| **k6(스크립트 새로 짬) + 분석기 Kotlin** | `constant-arrival-rate`·`ramping-arrival-rate` | 스크립트에서 요청 id·결과를 직접 써 내보낸다 | **이미 깔림(v2.4.0)**. 도착률 스케줄을 직접 짜지 않는다. failure-alert-lab에서 써 봤다 | 언어가 둘(JS + Kotlin). 요청별 기록 형식을 정해야 한다 |
| 전부 Kotlin(부하 + 분석기) | 직접 짜야 한다 | 된다 | 한 언어. 서비스 코드와 같은 HTTP 클라이언트 | 도착률을 지키는 스케줄러를 직접 짜고 **그 자체를 보정**해야 한다(늦게 보낸 요청이 지연을 숨기는 문제). JVM GC가 부하 쪽에 섞인다 |
| Gatling 3.16.1 | `constantUsersPerSec`(open) | 된다 | Kotlin DSL 있음(문서 예에 Kotlin 탭) | 새 도구(⑥). 무겁다 |
| wrk2 | 정해진 속도 | 요청별 기록이 약하다 | 가볍다 | 마지막 커밋 2019-09-24. 요청별 정답 기록에 맞지 않는다 |
| Go(부하 또는 분석기) | 직접 | 된다 | 깔려 있음(1.27.1). failure-alert-lab 분석기가 Go | 언어가 하나 더 는다 |

- **권하는 안: 보내는 쪽은 k6 v2.4.0 위에 새로 짠 스크립트, 분석기(정답 기록 ↔ DB 대조)는 Kotlin(서비스와 같은 저장소의 Gradle 모듈, JdbcClient 또는 순수 JDBC).** 도착률 스케줄은 이미 있는 것을 쓰고(②), "재는 것이 맞나"의 보정은 분석기에 집중한다. 이 자리는 사용자에게 묻는다(아래).
- 확인:
  - open model: "k6 implements the open model with two arrival rate executors", "the open model decouples VU iterations from the iteration duration" — https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/
  - VU가 모자랄 때: "This executor continues to start iterations at the given rate as long as VUs are available." "Using too low of a `preAllocatedVUs` setting will reduce the test duration at the desired rate." — https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/
  - k6 v2.4.0(2026-11-12) — https://api.github.com/repos/grafana/k6/releases/latest
  - Gatling: https://docs.gatling.io/concepts/injection/ , v3.16.1(2026-11-16, GitHub). wrk2: GitHub API 최신 커밋 2019-09-24.
- **측정에 주는 영향 — 숨은 기본값**:
  1. k6 요청 타임아웃 "Default timeout is 60 seconds" — https://grafana.com/docs/k6/latest/javascript-api/k6-http/params/ → 스크립트에서 명시한다. 60초가 서비스의 타임아웃보다 길게, 그리고 "결과 모름"을 가를 기준과 맞춰 정한다.
  2. VU가 모자라면 정해진 도착률을 지키지 못한다 → 실행마다 "보낸 수 = 계획한 수"를 분석기가 확인한다(못 보낸 반복의 수를 k6 요약에서 같이 기록 — 그 지표 이름은 이번에 문서에서 확인하지 못함).
  3. k6가 연결을 재사용하는 방식·자체 재시도는 이번에 확인하지 못함 — task 01 보정에서 "보낸 요청 수 = 서비스가 받은 수"로 실측해 확인한다.

---

## 9. 관측

**무엇을 위해**: 스레드 수(묶임), 진행 중인 요청 수, DB 풀 대기, **헛일**(위 층이 이미 포기한 요청을 아래 층이 처리한 수).
**정말 필요한가**: 숫자는 필요. 대시보드는 필요 없다(결과는 원시 기록에서 계산 — 명세 2절).

| 선택지 | 장점 | 단점 |
|---|---|---|
| **직접 세는 카운터 + 요청별 로그(또는 DB 행)** | 헛일은 "요청이 받은 기한"과 "처리 끝 시각"을 비교해야 해서 어차피 직접 짜야 한다. 원시 기록으로 남는다 | 코드 몇 줄 |
| **Actuator + Micrometer(Boot 기본)** | `jvm.`(스레드 활용, 가상 스레드 통계는 `micrometer-java21` 필요), `hikaricp`·`jdbc.connections`, `tomcat.`이 거저 나온다 | Tomcat 지표는 MBean 레지스트리를 켜야 한다 |
| Prometheus + Grafana | 시간 흐름을 본다 | 서버 두 개를 더 띄운다. 긁는 간격(초 단위)이 짧은 실험엔 거칠다 |

- **권하는 안: 직접 짠 카운터와 요청별 기록 + Actuator(Micrometer)의 스냅샷을 실행 앞뒤와 일정 간격으로 받아 원시 기록에 넣는다. Prometheus·Grafana·`micrometer-registry-prometheus`는 들이지 않는다.** (Actuator의 `/actuator/metrics/{name}` JSON으로 충분하다.)
- 확인 — https://docs.spring.io/spring-boot/reference/actuator/metrics.html :
  - "Auto-configuration enables the instrumentation of Tomcat only when an MBean Registry is enabled. By default, the MBean registry is disabled" → `server.tomcat.mbeanregistry.enabled=true`(Boot 4.2.0 메타데이터 기본 false).
  - "Hikari-specific metrics are exposed with a `hikaricp` prefix."
  - "Virtual threads statistics (for this, `io.micrometer:micrometer-java21` has to be on the classpath)" → 가상 스레드 조건을 잴 때 들인다(Micrometer 1.18.0, Boot 관리).
  - RestClient의 `http.client.requests`는 자동 구성된 `RestClient.Builder`를 써야 붙는다 — JDK 클라이언트를 직접 쓰면 붙지 않는다(3절 권하는 안과 함께 직접 센다).
- 측정에 주는 영향: 관측 자체가 일을 더한다(observation, 지표 기록). 조건 사이에 같게 켜 둔다. gRPC의 observation 기본 켜짐(4절)과 맞춘다.

---

## 10. 실행 환경

**무엇을 위해**: 노트북에서 세 서비스 + DB + 부하를 띄우고, 뒤에 서버 여러 대에 나눠 같은 것을 띄운다.
**정말 필요한가**: 필요.

### 10-1. 묶어 띄우기

- **권하는 안: Docker Compose(깔림 2.40.3)**, 프로젝트 이름을 고정(`name:`)해 다른 프로젝트 자원과 섞이지 않게 한다. 포트는 다른 lab과 겹치지 않게 고른다(failure-alert-lab이 8081~8084·15432·8474·9090 등을 쓴다 — `compose.yaml:33,54,75,84,93,106,119`). 서비스마다 `cpus`·`mem_limit`를 명시한다(조건을 같게 두기).
- `spring-boot-docker-compose`(앱이 compose를 띄우는 기능)는 **들이지 않는다** — "Call `docker compose up` … Call `docker compose stop` when the application is shutdown"(https://docs.spring.io/spring-boot/reference/features/dev-services.html). 측정 도구가 띄우고 내리는 시점을 쥐어야 한다.

### 10-2. 이미지 만들기

| 선택지 | 장점 | 단점 |
|---|---|---|
| **멀티스테이지 Dockerfile** | 새 의존성 없음. failure-alert-lab에서 그대로 썼다(`order-service/Dockerfile`) | 파일을 손으로 쓴다 |
| Jib 3.5.4 | Docker 없이 빌드, 기본 바탕 `eclipse-temurin:{…,21,25}-jre` | Gradle 플러그인 하나 더(⑥) |
| Buildpacks(`bootBuildImage`, Boot 기본) | 의존성 없음(④) | 느리고 빌더 이미지가 크다. 들어가는 JVM 설정(메모리 계산 등)이 숨는다 |

- **권하는 안: 멀티스테이지 Dockerfile**(빌드 `gradle:9.9.2-jdk25` 꼴, 런타임 `eclipse-temurin:25.0.5.1_2-jre-…` — 정확한 태그는 만들 때 Docker Hub에서 다시 확인). JVM 옵션(`-XX:MaxRAMPercentage`, 3절의 `-Djdk.httpclient.disableRetryConnect=true`)을 compose에서 명시해 기록에 남긴다.
- Jib: https://github.com/GoogleContainerTools/jib/blob/master/jib-gradle-plugin/README.md — 3.5.4, 바탕 `eclipse-temurin:{8,11,17,21,25}-jre`. 쓰지 않음.

### 10-3. 서버 여러 대로 나눌 때 걸리는 것

- 주소: 서비스 주소를 환경 변수로 받게 한다(http-grpc-lab `docker-compose.remeasure.yml:40-42`의 방식). compose 파일은 서버마다 나누거나 profile로 고른다.
- 시각: 판정은 DB 상태로 하므로(명세 2절) 서버 사이 시계 차이는 판정에 들어가지 않는다. 다만 "헛일"을 시각으로 가르면 시계 차이가 들어간다 → 헛일은 **기한을 요청에 실어 보내고, 받은 쪽이 자기 시계로 남은 시간을 계산**하는 방식(상대 시간)으로 재야 한다(4절 gRPC와 같은 방식).
- 서버의 상주 컨테이너와 포트를 건드리지 않는다(명세 4절). 올리기 전 배치를 보이고 확인받는다.
- 이미지 옮기기: 레지스트리를 둘지(`docker save/load`로 충분한지)는 task 06에서 정한다.

---

## 11. 테스트

**무엇을 위해**: 분석기의 단위 테스트(일부러 넣은 유실·중복을 세나 — 명세 5절), 재시도·예산·멱등 키 코드의 단위 테스트.
**정말 필요한가**: 필요.

- **JUnit Jupiter 6.1.3 + kotlin-test**: 권한다. Boot 관리 버전. failure-alert-lab과 같은 구성(`kotlin-test-junit5`, `junit-platform-launcher` — `order-service/build.gradle.kts:32,36`).
- **단언 라이브러리**: `spring-boot-starter-test`를 쓰면 AssertJ 3.27.7이 딸려 온다. 분석기 모듈(Spring 없음)은 `kotlin-test`의 `assertEquals`로 충분하다 → **따로 들이지 않는다**(Kotest·Strikt 등 들이지 않음).
- **Testcontainers 2.0.5**:
  - task 01~03: 들이지 않는다 — 분석기 테스트는 기록 파일을 입력으로 받으면 되고, 서비스 통합은 compose로 띄운 실행(보정 실행)이 검증한다.
  - task 04(멱등 키): "같은 키의 동시 요청이 한 건만 들어간다"는 PostgreSQL의 실제 잠금·제약으로만 확인되므로 그때 들일 후보 1순위(failure-alert-lab에서 씀 — `order-service/build.gradle.kts:33-35`). Boot의 `@ServiceConnection` 지원이 있다(dev-services 문서).
- 측정에 주는 영향: 없음(테스트는 측정 경로 밖).

---

## 12. 일부러 쓰지 않는 것

| 무엇 | 왜 쓰지 않나 | 확인한 것 |
|---|---|---|
| Resilience4j(2.4.0) | 명세 2절 — 재시도·백오프·예산을 직접 짠다. 재려는 것이 규칙이다 | Maven Central 최신 2.4.0 |
| Spring Retry | 위와 같음 + **보관(archive)됨**: "Spring Retry is no longer maintained as an open-source project." "superseded by Spring Framework 7" (2026-07-27 archive) | https://github.com/spring-projects/spring-retry |
| Spring Framework 7의 `@Retryable`·`@ConcurrencyLimit` | 프레임워크 안에 있지만(④) 같은 까닭으로 쓰지 않는다. **기본은 꺼져 있다**: `@EnableResilientMethods`를 붙여야 동작. 기본값 "maxRetries = 3", "delay of 1 second" | https://docs.spring.io/spring-framework/reference/core/resilience.html |
| Spring Cloud(Gateway, CircuitBreaker, OpenFeign) | 실험 대상이 아닌 층을 더한다. 게이트웨이·서킷 브레이커의 기본 동작이 측정에 섞인다 | 쓰지 않으므로 버전 확인하지 않음 |
| grpc-kotlin(코루틴) | 4절 | — |
| WebFlux / WebClient | 3절 — MVC 실험에 리액티브 스택이 섞인다 | — |
| JPA, Flyway, Liquibase | 5·6절 | — |
| Prometheus, Grafana | 9절 | — |

- **지킬 것**: 위의 어느 것도 다른 의존성에 딸려 들어오지 않았는지 `./gradlew dependencies`로 확인하고, 코드에 `@EnableResilientMethods`가 없는지 본다(있으면 Spring의 재시도가 몰래 켜진다).
- **뒤에 "라이브러리와의 비교"를 할 자리**: 이 명세 밖(명세 2절). 할 때는 직접 짠 재시도 모듈과 같은 인터페이스 뒤에 Resilience4j 또는 Spring Framework 7 `RetryTemplate`을 꽂는 변형으로 둔다 — 그러려면 재시도 코드를 "호출 하나를 감싸는 함수" 하나로 모아 두는 것이 지금 정할 것의 전부다.

---

## 13. 메시징 명세(다음)에의 걸림돌 — 한 줄씩

| 지금의 선택 | 걸림돌? |
|---|---|
| Spring Boot 4.2.0 | 아니다 — Boot 4.2.0이 spring-kafka 4.2.0 · Kafka 클라이언트 4.4.0 · spring-amqp 4.2.0 · Lettuce 7.8.0(Redis)을 관리한다(`spring-boot-dependencies-4.2.0.pom`). 세 브로커의 실제 동작은 그 명세에서 확인 |
| JDK 25 | 아니다(Boot 4.2.0은 Java 27까지 호환) |
| JdbcClient + 손으로 쓴 트랜잭션 | 아니다 — Outbox·Inbox는 같은 트랜잭션에 행을 더하는 일이라 오히려 맞다 |
| `schema.sql` | 아니다 — 판 관리가 필요해지면 그때 Flyway를 들인다 |
| 헤더 장애 주입만 | 브로커 장애엔 모자란다 — 그때 Toxiproxy를 들일 가능성 |
| k6 + Kotlin 분석기 | 아니다 — 분석기의 "정답 ↔ DB 대조"는 그대로 쓰고, k6는 HTTP 입구만 친다(브로커 직접 발행이 필요하면 그때 정함) |
| 플랫폼 스레드 기본 | 아니다 |

---

## 한눈에 보는 표

| 자리 | 권하는 안 | 버전 | 정말 필요한가 |
|---|---|---|---|
| JDK | Temurin 25 (LTS) | 25.0.5.1+2 | 필요 — 21은 가상 스레드가 `synchronized`에서 묶여 비교가 흐려진다 |
| Kotlin | Boot가 관리하는 판 | 2.4.21 | 필요(정해진 언어) |
| Spring Boot | 4.2.0 | 4.2.0 (Framework 7.1.0, Tomcat 11.0.27) | 필요(정해진 프레임워크) |
| 빌드 | Gradle 래퍼, Kotlin DSL | 9.9.2 | 필요 |
| 서버 스레드 | 플랫폼 스레드 기본, 가상 스레드는 비교 조건 | Tomcat 기본 200·큐 100·연결 8192 | 새로 들일 것 없음(값만 명시) |
| HTTP 클라이언트 | JDK `HttpClient` 직접 | JDK 25 | 필요, 새 의존성 없음 — 재시도 자동(연결 실패)을 끈다 |
| gRPC | Boot gRPC 스타터 + blocking 스텁, Netty 서버 | Spring gRPC 1.1.2, grpc-java 1.84.1, protobuf 4.36.2, 플러그인 0.9.6 | task 05에서만 — 재시도 끄고 실행기 고정 |
| DB | PostgreSQL 18 | 18.7 | 필요(판정의 바탕) |
| DB 접근 | `JdbcClient` + 트랜잭션 | Boot 관리(드라이버 42.7.14, Hikari 7.1.0) | 필요, 새 의존성 없음 — Hikari 풀 10·연결 대기 30초를 명시 |
| 스키마 | `schema.sql` (`mode=always`) | — | 들이지 않는다(Flyway·Liquibase) |
| 장애 주입 | 요청 헤더 직접 코드 + compose kill | — | 들이지 않는다(Toxiproxy·netem은 필요해질 때) |
| 부하 | k6 위에 새 스크립트 | v2.4.0 (깔림) | 필요, 이미 있음 — 기본 타임아웃 60초를 명시 |
| 분석기 | Kotlin(Gradle 모듈) | Kotlin 2.4.21 | 필요(명세 — 새로 짠다) |
| 관측 | 직접 센 카운터 + Actuator 스냅샷 | Micrometer 1.18.0 | 직접 세는 것은 필요, Prometheus·Grafana는 들이지 않는다 |
| 실행 | Docker Compose + 멀티스테이지 Dockerfile | Compose 2.40.3 (깔림) | 필요, 새 의존성 없음 |
| 테스트 | JUnit Jupiter + kotlin-test | 6.1.3 | 필요. AssertJ는 딸려 오는 것만, Testcontainers는 task 04 후보 |
| 쓰지 않음 | Resilience4j, Spring Retry, `@Retryable`, Spring Cloud | — | 쓰지 않는다(명세 2절) |

## 사용자에게 물을 것

1. **JDK 21 → 25로 올리나?** 권하는 안은 25(가상 스레드 비교가 깨끗해진다). 대가: 노트북에 25를 새로 깔고(또는 Gradle 툴체인 자동 받기), 두 참고 프로젝트와 버전이 달라진다. 21로 두면 가상 스레드 조건의 결과에 "21의 pinning 탓일 수 있음"을 한계로 적어야 한다.
2. **부하를 보내는 부분까지 새로 짜나?** 권하는 안은 "k6 위에 새 스크립트 + Kotlin 분석기"(도착률 스케줄을 직접 짜고 보정하는 일을 덜어낸다). 다른 안은 "전부 Kotlin 한 언어" — 한 언어지만 open model 스케줄러와 그 보정을 새로 짜야 한다. 명세의 "측정 도구를 새로 짠다"가 어느 쪽을 뜻했는지 정해 주기 바란다.
3. **HTTP 호출에 RestClient가 아니라 JDK `HttpClient`를 직접 쓰나?** 권하는 안은 직접(요청마다 남은 기한을 타임아웃으로 넘길 수 있다). RestClient를 고르면 코드가 Spring답지만 "남은 기한 전파(HTTP)"를 재기 어려워진다.
4. (task 05 앞에서 다시 물어도 됨) **gRPC를 Boot 스타터로 넣나, grpc-java만으로 넣나?** 권하는 안은 스타터(버전 관리를 Boot에 맡김). 대가는 숨은 기본값(관측·헬스·리플렉션·상한 없는 서버 실행기)을 하나씩 끄거나 명시해야 하는 것.
