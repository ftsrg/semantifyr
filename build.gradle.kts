/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.bmuschko.docker) apply false
    `jacoco-report-aggregation`
}

repositories {
    mavenCentral()
}

val jvmConventionIds = setOf(
    "hu.bme.mit.semantifyr.gradle.conventions.jvm",
)

dependencies {
    subprojects.forEach { subproject ->
        jvmConventionIds.forEach { conventionId ->
            subproject.plugins.withId(conventionId) {
                add("jacocoAggregation", subproject)
            }
        }
    }
}

reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName = "test"
        }
        val verificationTestCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName = "verificationTest"
        }
        val conformanceTestCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName = "conformanceTest"
        }
    }
}
