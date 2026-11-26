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
        property("sonar.coverage.jacoco.xmlReportPaths", suiteReportTasks.map {
            it.get().reports.xml.outputLocation.get().asFile
        })
        property("sonar.exclusions", "**/generated/**,**/xtext-gen/**,**/emf-gen/**")
        property("sonar.projectKey", "ftsrg_semantifyr")
        property("sonar.organization", "ftsrg-github")
        property("sonar.projectName", "Semantifyr")
    }
}

val workspaceManifests = listOf(
    "subprojects/semantifyr-editor-common",
    "subprojects/semantifyr-live/frontend",
    "subprojects/semantifyr-vscode",
    "subprojects/website",
).map {
    layout.projectDirectory.file("$it/package.json")
}

// npm links each member into node_modules by its package name, and the install must not take in the members' sources and builds
val workspaceLinks = workspaceManifests.map {
    (JsonSlurper().parseText(providers.fileContents(it).asText.get()) as Map<*, *>)["name"] as String
}

tasks.npmInstall {
    inputs.file(layout.projectDirectory.file("package-lock.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(workspaceManifests).withPathSensitivity(PathSensitivity.RELATIVE)
    val links = workspaceLinks
    nodeModulesOutputFilter {
        exclude(links)
    }
}

// npm's record of the installed tree stands for node_modules, which links back into the members
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
    }

    kotlinGradle {
        target(
            fileTree(".") {
                include("*.gradle.kts")
            },
            fileTree("build-logic/src") {
                include("**/*.gradle.kts")
            },
        )
    }
}
