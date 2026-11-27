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
    implementation(project(":backend"))
    implementation(project(":compiler"))
    implementation(project(":logging"))
    implementation(project(":oxsts.lang"))
    implementation(project(":oxsts.model"))
    implementation(project(":portfolios"))
    implementation(project(":verifier"))
    implementation(libs.clikt)
    implementation(libs.clikt.core)
    implementation(libs.ecore)
    implementation(libs.guice)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    implementation(libs.xtext.core)

    testImplementation(testFixtures(project(":cli-common")))
    testImplementation(testFixtures(project(":verifier")))
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
