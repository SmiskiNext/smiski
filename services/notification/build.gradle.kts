import org.gradle.api.tasks.testing.Test

plugins {
    id("io.github.smiskinext.plugin.spotless")
    id("io.github.smiskinext.plugin.jvm.base")
    id("io.github.smiskinext.plugin.service.base")
    id("io.github.smiskinext.plugin.test.base")
}

group = "io.github.smiskinext.services"
description = "notification"

dependencies {
    implementation(libs.shared)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.cloudevents.kafka)
    implementation(libs.protobuf.java.util)
    implementation(libs.biweekly)
    implementation(libs.resend.java)
    implementation(libs.svix)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(testFixtures(libs.shared))
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.protobuf" && requested.name == "protobuf-java") {
            useVersion("4.35.1")
            because("Proto module compiled with protoc 4.35.1 requires matching runtime")
        }
    }
}

val integrationTestTask = tasks.named<Test>("integrationTest")

tasks.register<Test>("generateOpenApiDocsFromTests") {
    group = "openapi"
    description = "Generate the notification OpenAPI spec via SpringBootTest"
    testClassesDirs = integrationTestTask.get().testClassesDirs
    classpath = integrationTestTask.get().classpath
    useJUnitPlatform()
    filter {
        includeTestsMatching("*OpenApiGenerationTest")
    }
    val specFile = layout.projectDirectory.file("openapi.yaml")
    systemProperty("openapi.output.file", specFile.asFile.absolutePath)
    outputs.file(specFile)
    shouldRunAfter(integrationTestTask)
}
