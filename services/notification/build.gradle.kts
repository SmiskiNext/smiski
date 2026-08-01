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
