import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
}

group = "com.xiilab.astrago.keycloak"
version = "1.1.0"

repositories {
    mavenCentral()
}

// 배포 대상 Keycloak 버전. 실제 운영 Keycloak 과 반드시 일치시킬 것.
val keycloakVersion = "23.0.7"

dependencies {
    // Keycloak 런타임이 제공하므로 compileOnly (jar 에 포함하지 않음)
    compileOnly("org.keycloak:keycloak-core:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-server-spi:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-server-spi-private:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-services:$keycloakVersion")
    compileOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
    compileOnly("org.jboss.logging:jboss-logging:3.5.3.Final")
    // Keycloak 가 런타임에 제공하는 Jackson (JsonSerialization 사용)
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.16.0")

    testImplementation(kotlin("test"))
}

// 빌드 JDK(24)와 무관하게, 산출물은 Keycloak 런타임 JVM(=Java 21 LTS, keycloakx 이미지)에 맞춘 21 타겟.
// 타깃이 런타임보다 높으면 UnsupportedClassVersionError 로 SPI 로드 실패.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("astrago-doosan-enb-spi")
}
