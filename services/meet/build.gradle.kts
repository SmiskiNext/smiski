import org.gradle.api.tasks.testing.Test

plugins {
    id("io.github.smiskinext.plugin.spotless")
    id("io.github.smiskinext.plugin.jvm.base")
    id("io.github.smiskinext.plugin.service.base")
    id("io.github.smiskinext.plugin.test.base")
    alias(libs.plugins.nxProjectGraph)
}

group = "io.github.smiskinext.services"
version = "0.0.1-SNAPSHOT"
description = "meet"

dependencies {
    implementation(libs.shared)
    implementation(libs.uuid.creator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.cloudevents.kafka)
    implementation(libs.livekit.server)
    implementation(libs.aws.s3)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.bouncycastle)
    runtimeOnly(libs.flyway.database.postgresql)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.boot.data.jpa.test)
    testImplementation(libs.spring.boot.jdbc.test)
    testImplementation(testFixtures(libs.shared))
}

val integrationTestTask = tasks.named<Test>("integrationTest")

tasks.register<Test>("generateOpenApiDocsFromTests") {
    group = "openapi"
    description = "Generate the meet OpenAPI spec via SpringBootTest"
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

tasks.register("projectReportAll") {
    gradle.includedBuilds.forEach { includedBuild ->
        dependsOn(includedBuild.task(":projectReportAll"))
    }
}
