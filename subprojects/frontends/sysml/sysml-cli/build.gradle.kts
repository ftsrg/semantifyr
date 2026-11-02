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
    implementation(project(":sysml-frontend"))
    implementation(project(":sysml-wrapper"))
    implementation(project(":logging"))

    testImplementation(testFixtures(project(":cli-common")))
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.sysml.cli.SemantifyrSysMLCliKt"
    applicationName = "semantifyr-sysml"
}

val rootDirectory = isolated.rootProject.projectDirectory

tasks.named("test") {
    inputs.dir(layout.projectDirectory.dir("../models/tutorial"))
        .withPropertyName("sysmlTutorialModels")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootDirectory.dir("oxsts-test-models/semantic"))
        .withPropertyName("oxstsSemanticModels")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
