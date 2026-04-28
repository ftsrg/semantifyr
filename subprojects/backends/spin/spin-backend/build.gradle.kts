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
    api(project(":spin-executor"))
    api(libs.guice.extensions.assistedinject)

    implementation(project(":logging"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":compiler"))
    testRuntimeOnly(libs.slf4j.log4j)

    testFixturesApi(project(":verification"))
    testFixturesApi(testFixtures(project(":verification")))
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
                implementation(project(":portfolios"))
            }
        }
    }
}
