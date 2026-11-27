/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import groovy.json.JsonSlurper
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sonarqube)
    `jacoco-report-aggregation`
    id("hu.bme.mit.semantifyr.nodejs")
}

dependencies {
    jacocoAggregation(project(":benchmark"))
    jacocoAggregation(project(":corpus-check"))
    jacocoAggregation(project(":gamma-cli"))
    jacocoAggregation(project(":semantifyr-cli"))
    jacocoAggregation(project(":semantifyr-live-backend"))
    jacocoAggregation(project(":sysml-cli"))
    jacocoAggregation(project(":website"))
}

val suiteReportTasks = listOf(
    "test",
    "adapterTest",
    "semanticTest",
    "integrationTest",
    "endToEndTest",
).map { suite ->
    reporting.reports.register<JacocoCoverageReport>("${suite}CoverageReport") {
        testSuiteName = suite
    }.flatMap {
        it.reportTask
    }
}

tasks.named("sonar") {
    inputs.files(suiteReportTasks).withPathSensitivity(PathSensitivity.RELATIVE)
}

sonar {
    properties {
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            suiteReportTasks.map {
                it.get().reports.xml.outputLocation.get().asFile
            },
        )
        property(
            "sonar.exclusions",
            listOf(
                "**/generated/**",
                "**/xtext-gen/**",
                "**/emf-gen/**",
            ),
        )
        property("sonar.projectKey", "ftsrg_semantifyr")
        property("sonar.organization", "ftsrg-github")
        property("sonar.projectName", "Semantifyr")
    }
}

val workspaceManifests = providers.fileContents(layout.projectDirectory.file("package.json")).asText.map { manifest ->
    ((JsonSlurper().parseText(manifest) as Map<*, *>)["workspaces"] as List<*>).map {
        "$it/package.json"
    }
}

node {
    npmInstallCommand = providers.environmentVariable("CI").map {
        "ci"
    }.orElse("install")
}

tasks.npmInstall {
    inputs.files(workspaceManifests).withPathSensitivity(PathSensitivity.RELATIVE)
}

configurations.consumable("npmWorkspaceElements") {
    attributes(semantifyrArtifact("npm-workspace"))
    outgoing.artifact(layout.projectDirectory.file("node_modules/.package-lock.json")) {
        builtBy(tasks.npmInstall)
    }
}

spotless {
    kotlin {
        target(
            fileTree("build-logic/src") {
                include("**/*.kt")
            },
        )
        ktlint(libs.versions.ktlint.get())
    }

    kotlinGradle {
        target(
            fileTree(".") {
                include("*.gradle.kts")
            },
            fileTree("build-logic") {
                include("*.gradle.kts")
            },
            fileTree("build-logic/src") {
                include("**/*.gradle.kts")
            },
        )
    }
}
