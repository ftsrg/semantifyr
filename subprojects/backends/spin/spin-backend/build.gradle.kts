/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
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
    api(project(":backend"))
    api(project(":oxsts.lang"))
    api(project(":spin-executor"))
    api(libs.guice.extensions.assistedinject)

    implementation(project(":logging"))
    implementation(project(":utils"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":compiler"))

    testFixturesApi(testFixtures(project(":backend")))
}

val cloneOxstsTestModels by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("oxsts-test-models"))
    into(layout.buildDirectory.dir("test-models"))
}

tasks.named("verificationTest") {
    inputs.files(cloneOxstsTestModels)
}
tasks.named("integrationTest") {
    inputs.files(cloneOxstsTestModels)
}
