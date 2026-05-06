/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
    id("hu.bme.mit.semantifyr.gradle.conventions.integration")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":portfolios"))

    implementation(project(":sysml-wrapper"))
    implementation(project(":logging"))
    implementation(project(":utils"))
    implementation(libs.kotlinx.coroutines.core)

    testFixturesApi(project(":portfolios"))
    testFixturesApi(testFixtures(project(":verifier")))
}

testing {
    suites {
        val verificationTest by getting(JvmTestSuite::class) {
            dependencies {
                implementation(project(":portfolios"))
                implementation(testFixtures(project(":verifier")))
            }
        }
    }
}

val syncSysmlLibrary by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("../models/libraries"))
    into(layout.buildDirectory.dir("libraries"))
}

val syncSysmlTestModels by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("../models/examples"))
    into(layout.buildDirectory.dir("test-models"))
}

tasks.processResources {
    from(syncSysmlLibrary)
}

tasks.withType<Test>().configureEach {
    inputs.files(syncSysmlTestModels)
}
