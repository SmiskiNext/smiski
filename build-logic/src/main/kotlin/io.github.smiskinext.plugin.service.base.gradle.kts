import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.the


plugins {
    id("io.github.smiskinext.plugin.jvm.base")
    id("io.github.smiskinext.plugin.spotless")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.hibernate.orm")
    id("org.graalvm.buildtools.native")
    id("com.google.protobuf")
    java
}

group = "io.github.smiskinext.conventions"
version = "0.0.1-SNAPSHOT"

val libs = the<LibrariesForLibs>()

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"))
    implementation(platform("org.springframework.grpc:spring-grpc-dependencies:${libs.versions.springGrpc.get()}"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.aspectj)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.log4j2)
    implementation(libs.log4j.layout.template.json)
    implementation(libs.springdoc.openapi.starter.webmvc.api)
    modules {
        module(
            libs.spring.boot.starter.logging
                .get()
                .module,
        ) {
            replacedBy(
                libs.spring.boot.starter.log4j2
                    .get()
                    .module,
                "Use Log4j2 instead of Logback",
            )
        }
    }
    implementation(libs.spring.kafka)
    implementation(libs.cloudevents.core)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.integration)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.integration.http)
    implementation(libs.spring.integration.jpa)
    implementation(libs.spring.security.messaging)
    implementation(libs.jjwt.api)
    implementation(libs.proto)
    implementation(libs.spring.boot.starter.server.grpc)
    implementation(libs.spring.boot.starter.client.grpc)
    compileOnly(libs.jspecify)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    developmentOnly(libs.spring.boot.devtools)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.bouncycastle)
    annotationProcessor(libs.spring.boot.configuration.processor)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.integration.test)
    testImplementation(libs.spring.boot.grpc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    imageName.set("ghcr.io/smiskinext/${project.name}:${project.version}")
    environment.set(
        mapOf(
            "BP_JVM_VERSION" to "25",
        ),
    )
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    outputs.upToDateWhen { false }
    notCompatibleWithConfigurationCache(
        "bootRun starts a long-running JVM and must not be replayed from the Gradle configuration cache.",
    )
}
