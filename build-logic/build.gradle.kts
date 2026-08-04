plugins {
    `kotlin-dsl`
}

group = "io.github.smiskinext"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(plugin(libs.plugins.spotless))
    implementation(plugin(libs.plugins.springBoot))
    implementation(plugin(libs.plugins.springDependencyManagement))
    implementation(plugin(libs.plugins.hibernateOrm))
    implementation(plugin(libs.plugins.protobuf))
    implementation(plugin(libs.plugins.pitest))
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

fun DependencyHandlerScope.plugin(plugin: Provider<PluginDependency>) =
    plugin.map {
        "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
    }
