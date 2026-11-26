/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.readsServedExamples

plugins {
    id("hu.bme.mit.semantifyr.theta")
    id("hu.bme.mit.semantifyr.suites")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":logging"))
    implementation(libs.slf4j.api)

    api(project(":backend"))
    api(project(":verifier"))
    api(project(":theta-backend"))
    api(project(":uppaal-backend"))
    api(project(":nuxmv-backend"))
    api(project(":spin-backend"))
    api(libs.guice)

    testImplementation(project(":compiler"))
    testImplementation(project(":oxsts.model"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)

    testFixturesApi(project(":backend"))
    testFixturesApi(libs.kotlinx.serialization.core)
    testFixturesApi(libs.kotlinx.serialization.json)
    testFixturesImplementation(project(":nuxmv-executor"))
    testFixturesImplementation(project(":uppaal-executor"))
}

testing {
    suites {
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project(":backend"))
                implementation(project(":compiler"))
                implementation(project(":oxsts.lang"))
                implementation(libs.guice)
            }
        }
    }
}

tasks.named<Test>("integrationTest") {
    readsServedExamples()
    inputs.dir(isolated.rootProject.projectDirectory.dir("oxsts-test-models/tutorial")).withPathSensitivity(PathSensitivity.RELATIVE)
}
