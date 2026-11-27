/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.artifactsOf

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

val sysmlCorpus = artifactsOf("sysml-corpus", ":sysml-wrapper")

val syncSysmlTestModels = tasks.register<Sync>("syncSysmlTestModels") {
    from(sysmlCorpus)
    into(layout.buildDirectory.dir("test-models"))
}

tasks.processResources {
    from(librariesDirectory)
}

tasks.withType<Test>().configureEach {
    inputs.files(syncSysmlTestModels).withPathSensitivity(PathSensitivity.RELATIVE)
}
