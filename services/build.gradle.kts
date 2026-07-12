plugins {
    id("dev.nx.gradle.project-graph") version "0.1.24"
}

tasks.register("projectReportAll") {
    gradle.includedBuilds.forEach { includedBuild ->
        dependsOn(includedBuild.task(":projectReportAll"))
    }
}
