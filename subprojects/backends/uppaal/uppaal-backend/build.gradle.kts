/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
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
    api(project(":oxsts.lang"))
    api(project(":uppaal-executor"))
    api(libs.guice.extensions.assistedinject)

    implementation(project(":logging"))
    implementation(libs.kotlinx.serialization.json)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            dependencies {
                implementation(testFixtures(project(":oxsts.lang")))
            }
        }
        val verificationTest by getting(JvmTestSuite::class) {
            dependencies {
                implementation(testFixtures(project(":verification")))
            }
        }
        val conformanceTest by getting(JvmTestSuite::class) {
            dependencies {
                implementation(testFixtures(project(":verification")))
                implementation(project(":portfolios"))
            }
        }
    }
}
