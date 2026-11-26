/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.semantifyrKtlint

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
        reportTask.configure {
            reports {
                xml.required = true
            }
        }
    }.flatMap {
        it.reportTask
    }
}

tasks.named("sonar") {
    inputs.files(suiteReportTasks)
}

sonar {
    properties {
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            suiteReportTasks.map {
                it.get().reports.xml.outputLocation.get().asFile
            },
        )
        property("sonar.exclusions", "**/generated/**,**/xtext-gen/**,**/emf-gen/**")
        property("sonar.projectKey", "ftsrg_semantifyr")
        property("sonar.organization", "ftsrg-github")
        property("sonar.projectName", "Semantifyr")
    }
}

val workspaceMembers = listOf(
    "subprojects/semantifyr-editor-common",
    "subprojects/semantifyr-live/frontend",
    "subprojects/semantifyr-vscode",
    "subprojects/website",
)

val workspaceManifests = workspaceMembers.map {
    layout.projectDirectory.file("$it/package.json")
}

tasks.npmInstall {
    inputs.file(layout.projectDirectory.file("package-lock.json")).withPathSensitivity(PathSensitivity.RELATIVE)
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
        semantifyrKtlint(libs.versions.ktlint.get())
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
