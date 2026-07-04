import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.the

plugins {
    id("io.github.smiskinext.plugin.spotless")
    id("io.github.smiskinext.plugin.jvm.base")
    id("io.github.smiskinext.plugin.service.base")
}

group = "io.github.smiskinext.services"
version = "0.0.1-SNAPSHOT"
description = "user-management"

val libs = the<LibrariesForLibs>()

dependencies {
    implementation(libs.shared)
    implementation(libs.uuid.creator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.cloudevents.kafka)
    implementation(libs.firebase.admin)
    runtimeOnly(libs.flyway.database.postgresql)
    testImplementation(testFixtures(libs.shared))
}

hibernate {
    enhancement {
        enableAssociationManagement = false
        classNames.addAll(
            "io.github.smiskinext.usermanagement.infrastructure.persistence.UserJpaEntity",
            "io.github.smiskinext.usermanagement.infrastructure.persistence.RefreshTokenJpaEntity",
            "io.github.smiskinext.usermanagement.infrastructure.persistence.OutboxEventJpaEntity",
        )
    }
}

val testTask = tasks.named<Test>("test")

tasks.register<Test>("generateOpenApiDocsFromTests") {
    group = "openapi"
    description = "Generate the user-management OpenAPI spec via SpringBootTest"
    testClassesDirs = testTask.get().testClassesDirs
    classpath = testTask.get().classpath
    useJUnitPlatform()
    filter {
        includeTestsMatching("*OpenApiGenerationTest")
    }
    outputs.file(layout.buildDirectory.file("openapi/openapi.yaml"))
    shouldRunAfter(testTask)
}
