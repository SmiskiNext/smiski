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
    implementation(libs.cloudevents.kafka)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(testFixtures(libs.shared))
}
