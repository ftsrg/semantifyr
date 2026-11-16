/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.backend")
    id("hu.bme.mit.semantifyr.gradle.conventions.sysml-crossroads")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":backend"))
    api(project(":xsts.lang"))
    api(project(":theta-executor"))
    api(libs.guice.extensions.assistedinject)

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":backend")))
}

tasks.named("test") {
    inputs.files(tasks.named("cloneOxstsTestModels"))
}
