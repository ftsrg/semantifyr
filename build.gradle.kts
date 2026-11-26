/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sonarqube)
    `jacoco-report-aggregation`
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

val coverageReport = tasks.register<JacocoReport>("coverageReport") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    suiteReportTasks.forEach { reportTask ->
        executionData.from(
            reportTask.map {
                it.executionData
            },
        )
        sourceDirectories.from(
            reportTask.map {
                it.sourceDirectories
            },
        )
        classDirectories.from(
            reportTask.map {
                it.classDirectories
            },
        )
    }

    reports {
        xml.required = true
    }
}

tasks.named("sonar") {
    inputs.files(coverageReport)
}

sonar {
    properties {
        property("sonar.coverage.jacoco.xmlReportPaths", coverageReport.get().reports.xml.outputLocation.get().asFile)
        property("sonar.exclusions", "**/generated/**,**/xtext-gen/**,**/emf-gen/**")
        property("sonar.projectKey", "ftsrg_semantifyr")
        property("sonar.organization", "ftsrg-github")
        property("sonar.projectName", "Semantifyr")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "UTF-8")
    }
}
