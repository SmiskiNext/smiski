import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.the

plugins {
    id("io.github.smiskinext.plugin.jvm.base")
    id("io.github.smiskinext.plugin.spotless")
    id("io.github.smiskinext.plugin.service.base")
    `java-test-fixtures`
}

group = "io.github.smiskinext.services"
version = "0.0.1-SNAPSHOT"

val libs = the<LibrariesForLibs>()

dependencies {
    implementation(libs.cloudevents.json.jackson)
    implementation(libs.cloudevents.core)
    implementation(libs.spring.kafka)
    implementation(libs.protobuf.java.util)
    testFixturesImplementation(libs.archunit.junit5)
    testFixturesImplementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"))
    testFixturesImplementation(libs.spring.boot.starter.test)
    testFixturesImplementation(libs.spring.boot.starter.webmvc)
    testFixturesImplementation(libs.spring.boot.starter.data.jpa)
    testFixturesImplementation(libs.spring.boot.testcontainers)
    testFixturesImplementation(libs.testcontainers.junit.jupiter)
    testFixturesImplementation(libs.testcontainers.postgresql)
    testFixturesImplementation(libs.testcontainers.kafka)
    testFixturesImplementation(libs.testcontainers.minio)
    testFixturesImplementation("org.springframework:spring-test")
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}

tasks.withType<org.springframework.boot.gradle.tasks.aot.ProcessAot> {
    enabled = false
}

tasks.withType<org.springframework.boot.gradle.tasks.aot.ProcessTestAot> {
    enabled = false
}
