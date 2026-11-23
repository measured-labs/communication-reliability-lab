plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(project(":chain-link"))
    runtimeOnly("org.postgresql:postgresql")
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}
