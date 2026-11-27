/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.readsServedExamples
import hu.bme.mit.semantifyr.gradle.readsTestModels

plugins {
    id("hu.bme.mit.semantifyr.theta")
    id("hu.bme.mit.semantifyr.suites")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":logging"))

    api(project(":verifier"))
    api(project(":theta-backend"))
    api(project(":uppaal-backend"))
    api(project(":nuxmv-backend"))
    api(project(":spin-backend"))

    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)

    testFixturesApi(libs.kotlinx.serialization.json)
}

tasks.named<Test>("integrationTest") {
    readsServedExamples()
    readsTestModels("tutorial")
}
