/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.cli")
    kotlin("jvm")
}

dependencies {
    implementation(project(":cli-common"))
    implementation(project(":logging"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":cli-common")))
}

application {
    mainClass = "hu.bme.mit.semantifyr.cli.SemantifyrCliKt"
    applicationName = "semantifyr"
}

val rootDirectory = isolated.rootProject.projectDirectory

tasks.named("test") {
    inputs.dir(rootDirectory.dir("oxsts-test-models"))
        .withPropertyName("oxstsTestModels")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
