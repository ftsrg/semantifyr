/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.readsServedExamples

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.integration")
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

    testFixturesApi(libs.kotlinx.serialization.json)
}

tasks.named<Test>("integrationTest") {
    readsServedExamples()
    inputs.dir(rootProject.layout.projectDirectory.dir("oxsts-test-models/tutorial")).withPathSensitivity(PathSensitivity.RELATIVE)
}
