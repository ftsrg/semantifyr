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
    subprojects.forEach { subproject ->
        subproject.plugins.withId("hu.bme.mit.semantifyr.gradle.conventions.jvm") {
            jacocoAggregation(project(subproject.path))
        }
    }
}

val suiteReportTasks = reporting.reports.run {
    listOf(
        register<JacocoCoverageReport>("mergedJacocoTestReport") { testSuiteName = "test" },
        register<JacocoCoverageReport>("mergedJacocoVerificationTestReport") { testSuiteName = "verificationTest" },
        register<JacocoCoverageReport>("mergedJacocoIntegrationTestReport") { testSuiteName = "integrationTest" },
    ).map { report ->
        report.flatMap { it.reportTask }
    }
}

tasks.register<JacocoReport>("mergedJacocoReport") {
    group = "verification"

    suiteReportTasks.forEach { reportTask ->
        executionData.from(reportTask.map { it.executionData })
        sourceDirectories.from(reportTask.map { it.sourceDirectories })
        classDirectories.from(reportTask.map { it.classDirectories })
    }

    reports {
        xml.required = true
        html.required = true
    }
}

val generatedSourceExclusions = "**/src-gen/**,**/generated/**,**/xtext-gen/**,**/emf-gen/**"

sonar {
    properties {
        property("sonar.coverage.exclusions", generatedSourceExclusions)
        property("sonar.exclusions", generatedSourceExclusions)
        property("sonar.projectKey", "ftsrg_semantifyr")
        property("sonar.organization", "ftsrg-github")
        property("sonar.projectName", "Semantifyr")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "UTF-8")
    }
}
