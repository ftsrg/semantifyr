/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.theta")
    id("hu.bme.mit.semantifyr.suites")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":sysml-wrapper"))
    api(project(":verifier"))

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)

    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(testFixtures(project(":verifier")))
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.kotlinx.serialization.json)
}

val librariesDirectory = layout.projectDirectory.dir("../models/libraries")

tasks.processResources {
    from(librariesDirectory)
}

tasks.withType<Test>().configureEach {
    listOf("examples", "semantics", "tests").forEach {
        inputs.dir(layout.projectDirectory.dir("../models/$it")).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
