import org.gradle.api.tasks.testing.Test

plugins {
    id("io.github.smiskinext.plugin.service.mongodb.base")
}

group = "io.github.smiskinext.services"
version = "0.0.1-SNAPSHOT"
description = "chat-management"

dependencies {
    implementation(libs.shared)
    implementation(libs.cloudevents.kafka)
    implementation(libs.livekit.server)
    testImplementation(testFixtures(libs.shared))
}

val testTask = tasks.named<Test>("test")

tasks.register<Test>("generateOpenApiDocsFromTests") {
    group = "openapi"
    description = "Generate the chat-management OpenAPI spec via SpringBootTest"
    testClassesDirs = testTask.get().testClassesDirs
    classpath = testTask.get().classpath
    useJUnitPlatform()
    filter {
        includeTestsMatching("*OpenApiGenerationTest")
    }
    outputs.file(layout.buildDirectory.file("openapi/openapi.yaml"))
    shouldRunAfter(testTask)
}
