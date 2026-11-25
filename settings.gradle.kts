pluginManagement {
    val kotlinVersion: String by settings
    val springBootVersion: String by settings
    val protobufPluginVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        id("org.springframework.boot") version springBootVersion
        // task 05: chain-link의 .proto에서 메시지·스텁 코드를 만든다(docs/dependencies.md "task 05")
        id("com.google.protobuf") version protobufPluginVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "communication-reliability-lab"

// fault-header: 장애 헤더의 해석(Spring 없음 — 서비스와 분석기가 함께 쓴다)
// chain-link: 세 서비스가 같은 규칙으로 쓰는 것(장애 주입, 아래 층 호출, 요청 문맥)
include("fault-header", "chain-link", "order", "inventory", "payment", "analyzer")
