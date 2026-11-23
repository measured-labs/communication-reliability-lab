// 장애 헤더(X-Fault)의 해석 하나만 둔다. 서비스(chain-link)와 분석기가 같은 규칙을 쓰려고 나눴다 —
// 분석기가 Spring을 끌고 오지 않게 이 모듈은 Kotlin 표준 라이브러리 밖에 기대지 않는다.
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(25)
}
