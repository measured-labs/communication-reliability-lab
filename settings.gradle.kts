pluginManagement {
    val kotlinVersion: String by settings
    val springBootVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        id("org.springframework.boot") version springBootVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "communication-reliability-lab"

// chain-link: 세 서비스가 같은 규칙으로 쓰는 것(장애 헤더, 아래 층 호출, 요청 문맥)
include("chain-link", "order", "inventory", "payment", "analyzer")
