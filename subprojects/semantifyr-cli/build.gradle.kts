/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.readsTestModels

plugins {
    id("hu.bme.mit.semantifyr.distribution-test")
    kotlin("jvm")
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":portfolios"))

    testImplementation(testFixtures(project(":cli-common")))
    testImplementation(libs.log4j.api)
    testImplementation(libs.log4j.core)
    testImplementation(libs.mordant.core)
}

application {
    mainClass = "hu.bme.mit.semantifyr.cli.SemantifyrCliKt"
    applicationName = "semantifyr"
}

tasks.named<Test>("test") {
    readsTestModels()
}

tasks.named("endToEndTest") {
    inputs.file(isolated.rootProject.projectDirectory.file("oxsts-test-models/smoke/smoke.semantifyr")).withPathSensitivity(PathSensitivity.RELATIVE)
}
