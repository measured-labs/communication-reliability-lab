plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    // task 05: src/main/proto의 메시지와 blocking 스텁(Java)을 만든다
    id("com.google.protobuf")
}

val springBootVersion: String by project
val protocVersion: String by project
val grpcJavaVersion: String by project

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

dependencies {
    api(project(":fault-header"))
    api(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("tools.jackson.module:jackson-module-kotlin")
    api("org.jetbrains.kotlin:kotlin-reflect")
    // task 05: 서비스 사이의 gRPC(기본 꺼짐 — chain.transport=http). 서버·클라이언트 스타터(Netty, grpc-stub)와 protobuf 메시지의 직렬화.
    // 판은 Boot BOM이 정한다(grpc-java 1.84.1, protobuf-java 4.36.2 — docs/dependencies.md "task 05").
    api("org.springframework.boot:spring-boot-starter-grpc-server")
    api("org.springframework.boot:spring-boot-starter-grpc-client")
    api("io.grpc:grpc-protobuf")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$protocVersion" }
    plugins { create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcJavaVersion" } }
    // @generated=omit: 만든 코드에 javax.annotation.Generated를 붙이지 않는다(그 의존성을 들이지 않으려고 — Boot 플러그인이 거는 것과 같은 선택)
    generateProtoTasks { all().configureEach { plugins { create("grpc") { option("@generated=omit") } } } }
}

// task 03: 재시도 감싸개(Retry.kt)의 단위 테스트를 둘 자리(테스트는 docs/retry.md에서 다른 워커가 쓴다). 테스트가 없으면 NO-SOURCE.
dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
