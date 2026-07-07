import com.diffplug.spotless.extra.wtp.EclipseWtpFormatterStep
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.the

plugins {
    id("com.diffplug.spotless")
}

group = "io.github.smiskinext.conventions"
version = "0.0.1-SNAPSHOT"

val libs = the<LibrariesForLibs>()

spotless {
    java {
        target("**/src/**/*.java")
        targetExclude("**/build/**", "**/generated/**", "**/sdks/**")
        palantirJavaFormat(libs.versions.palantirJavaFormat.get()).style("AOSP")
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
        importOrder()
        removeUnusedImports()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/generated/**", "**/gradle/**", "**/bin/**", "**/sdks/**")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**", "**/generated/**", "**/bin/**", "**/.idea/**", "**/.gradle/**", "**/sdks/**")
        eclipseWtp(EclipseWtpFormatterStep.XML)
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }
}
