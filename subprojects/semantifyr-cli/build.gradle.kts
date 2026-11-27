/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

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

val rootDirectory = isolated.rootProject.projectDirectory

tasks.withType<Test>().configureEach {
    inputs.dir(rootDirectory.dir("oxsts-test-models")).withPathSensitivity(PathSensitivity.RELATIVE)
}

distributions.main {
    contents {
        from(rootDirectory.dir("oxsts-test-models/smoke")) {
            into("models/smoke")
        }
    }
}
