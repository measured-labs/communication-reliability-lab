plugins {
    kotlin("jvm")
    application
}

val springBootVersion: String by project

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("lab.reliability.analyzer.MainKt")
}

dependencies {
    // 장애 헤더의 해석을 서비스와 같은 코드로 한다(fault-header는 Spring에 기대지 않는다).
    implementation(project(":fault-header"))
    // Spring은 쓰지 않는다. 버전만 Boot의 BOM에 맞춘다(Jackson 3).
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("tools.jackson.core:jackson-databind")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
