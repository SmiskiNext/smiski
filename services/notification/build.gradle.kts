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
    implementation(libs.protobuf.java.util)
    testImplementation(libs.testcontainers.kafka)
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
