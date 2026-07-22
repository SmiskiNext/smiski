plugins {
    java
}
group = "io.github.smiskinext.conventions"
version =
    providers
        .environmentVariable("VERSION")
        .orElse(providers.gradleProperty("version"))
        .getOrElse("0.0.1-SNAPSHOT")
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}
