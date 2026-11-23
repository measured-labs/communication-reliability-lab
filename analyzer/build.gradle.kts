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
