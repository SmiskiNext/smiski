plugins {
    id("io.github.smiskinext.plugin.spotless")
    id("io.github.smiskinext.plugin.jvm.base")
    id("io.github.smiskinext.plugin.service.base")
}

group = "io.github.smiskinext.services"
version = "0.0.1-SNAPSHOT"
description = "notification"

dependencies {
    implementation(libs.shared)
    implementation(libs.cloudevents.kafka)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(testFixtures(libs.shared))
}
