import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.the
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    java
    jacoco
    id("info.solidsoft.pitest")
}

group = "io.github.smiskinext.conventions"
version = "0.0.1-SNAPSHOT"

val libs = the<LibrariesForLibs>()

val coverageExclusions =
    listOf(
        "**/*Application.class",
        "**/*Config*",
        "**/*Configuration*",
        "**/infrastructure/persistence/**JpaEntity*",
        "**/generated/**",
    )

val mutationExclusions =
    listOf(
        "*Application",
        "*Config",
        "*Configuration",
        "*JpaEntity",
        "*_Factory",
        "*__*",
    )

val integrationTest: SourceSet =
    sourceSets.create("integrationTest") {
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += output + compileClasspath
    }

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

val integrationTestTask =
    tasks.register<Test>("integrationTest") {
        description = "Runs container-backed integration and @SpringBootTest tests."
        group = "verification"
        testClassesDirs = integrationTest.output.classesDirs
        classpath = integrationTest.runtimeClasspath
        useJUnitPlatform()
        shouldRunAfter(tasks.named("test"))
    }

tasks.named("check") {
    dependsOn(integrationTestTask)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map { dir ->
                fileTree(dir) { exclude(coverageExclusions) }
            },
        ),
    )
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(
        files(
            classDirectories.files.map { dir ->
                fileTree(dir) { exclude(coverageExclusions) }
            },
        ),
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

val aggregatedExecutionData =
    fileTree(layout.buildDirectory) {
        include("jacoco/test.exec", "jacoco/integrationTest.exec")
    }

fun filteredMainClassDirectories() =
    files(
        sourceSets["main"].output.classesDirs.files.map { dir ->
            fileTree(dir) { exclude(coverageExclusions) }
        },
    )

tasks.register<JacocoReport>("jacocoAggregatedReport") {
    group = "verification"
    description = "Aggregates unit and integration test coverage into a single report."
    dependsOn(tasks.named("test"), integrationTestTask)
    sourceDirectories.setFrom(files(sourceSets["main"].allSource.srcDirs))
    classDirectories.setFrom(filteredMainClassDirectories())
    executionData.setFrom(aggregatedExecutionData)
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoCoverageVerificationAll") {
    group = "verification"
    description = "Enforces coverage thresholds across unit and integration tests."
    dependsOn(tasks.named("test"), integrationTestTask)
    sourceDirectories.setFrom(files(sourceSets["main"].allSource.srcDirs))
    classDirectories.setFrom(filteredMainClassDirectories())
    executionData.setFrom(aggregatedExecutionData)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

configure<PitestPluginExtension> {
    pitestVersion.set(libs.versions.pitest.get())
    junit5PluginVersion.set(libs.versions.pitestJunit5.get())
    targetClasses.set(
        listOf(
            "io.github.smiskinext.*.domain.*",
            "io.github.smiskinext.*.application.*",
        ),
    )
    excludedClasses.set(mutationExclusions)
    testSourceSets.set(listOf(sourceSets["test"]))
    mainSourceSets.set(listOf(sourceSets["main"]))
    threads.set(4)
    mutationThreshold.set(60)
    useClasspathFile.set(true)
    timestampedReports.set(false)
}
