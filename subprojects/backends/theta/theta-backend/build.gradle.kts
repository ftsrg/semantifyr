/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.conformance")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":backend"))
    api(project(":xsts.lang"))
    api(project(":theta-executor"))
    api(libs.guice.extensions.assistedinject)

    implementation(project(":logging"))
    implementation(libs.kotlinx.serialization.json)
}

testing {
    suites {
        val verificationTest by getting(JvmTestSuite::class) {
            dependencies {
                implementation(testFixtures(project(":verification")))
            }
        }
        val conformanceTest by getting(JvmTestSuite::class) {
            dependencies {
                implementation(testFixtures(project(":verification")))
                // Portfolios.AllAgree validates every produced witness via every installed backend.
                implementation(project(":portfolios"))
            }
        }
    }
}
