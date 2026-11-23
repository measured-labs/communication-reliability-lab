# JDK 25는 이 이미지 안에만 있다(이 컴퓨터의 JDK는 쓰지 않는다). 빌드와 실행은 같은 Temurin 판.
# 한 번의 빌드 단계에서 세 서비스와 분석기를 만들고, 대상(target)마다 결과물만 옮긴다.
# 이 컴퓨터의 Docker에는 BuildKit(buildx)이 없어 캐시 마운트 대신 층 캐시를 쓴다:
# 빌드 파일만 먼저 넣고 Gradle 배포판·플러그인·의존성을 받아 두면, 소스만 바뀔 때 그 층을 다시 쓴다.
FROM eclipse-temurin:25.0.5.1_2-jdk-noble AS build
WORKDIR /src
COPY gradlew settings.gradle.kts gradle.properties ./
COPY gradle gradle
COPY chain-link/build.gradle.kts chain-link/
COPY order/build.gradle.kts order/
COPY inventory/build.gradle.kts inventory/
COPY payment/build.gradle.kts payment/
COPY analyzer/build.gradle.kts analyzer/
RUN ./gradlew --no-daemon -q \
      :chain-link:dependencies :order:dependencies :inventory:dependencies :payment:dependencies :analyzer:dependencies > /dev/null
COPY chain-link chain-link
COPY order order
COPY inventory inventory
COPY payment payment
COPY analyzer analyzer
RUN ./gradlew --no-daemon -q :order:bootJar :inventory:bootJar :payment:bootJar :analyzer:installDist

FROM eclipse-temurin:25.0.5.1_2-jre-noble AS service
WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM service AS order
COPY --from=build /src/order/build/libs/app.jar app.jar

FROM service AS inventory
COPY --from=build /src/inventory/build/libs/app.jar app.jar

FROM service AS payment
COPY --from=build /src/payment/build/libs/app.jar app.jar

FROM eclipse-temurin:25.0.5.1_2-jre-noble AS analyzer
COPY --from=build /src/analyzer/build/install/analyzer /opt/analyzer
ENTRYPOINT ["/opt/analyzer/bin/analyzer"]

# 테스트만 돌리는 대상(scripts/test.sh): docker build --target test .
# 맨 끝에 둔다 — 레거시 빌더는 대상 앞의 단계를 모두 지으므로, 앞에 두면 서비스 이미지마다 테스트가 돈다.
FROM build AS test
RUN ./gradlew --no-daemon test
