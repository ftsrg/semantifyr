/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
    id("hu.bme.mit.semantifyr.gradle.conventions.integration")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":gamma.lang"))
    api(project(":verifier"))

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)

    testFixturesApi(project(":gamma.lang"))
    testFixturesApi(testFixtures(project(":gamma.lang")))
}

val syncGammaLibrary by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("../models/libraries"))
    into(layout.buildDirectory.dir("libraries"))
}

val syncGammaTestModels by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("../models/examples"))
    into(layout.buildDirectory.dir("test-models"))
}

tasks.processResources {
    from(syncGammaLibrary)
}

tasks.withType<Test>().configureEach {
    inputs.files(syncGammaTestModels)
}

testing {
    suites {
        val verificationTest by getting(JvmTestSuite::class) {
            dependencies {
                implementation(project(":portfolios"))
            }
        }
    }
}
