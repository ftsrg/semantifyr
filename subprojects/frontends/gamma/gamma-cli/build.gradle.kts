/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.cli")
    kotlin("jvm")
}

dependencies {
    implementation(project(":cli-common"))
    implementation(project(":gamma-frontend"))
    implementation(project(":logging"))

    testImplementation(testFixtures(project(":cli-common")))
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.cli.SemantifyrGammaCliKt"
    applicationName = "semantifyr-gamma"
}

tasks.named("test") {
    inputs.dir(layout.projectDirectory.dir("../models/examples"))
        .withPropertyName("gammaExamples")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
