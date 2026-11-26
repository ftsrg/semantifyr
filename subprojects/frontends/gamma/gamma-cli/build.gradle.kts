/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.registerCliReference

plugins {
    id("hu.bme.mit.semantifyr.distribution-test")
    kotlin("jvm")
}

dependencies {
    implementation(project(":backend"))
    implementation(project(":compiler"))
    implementation(project(":gamma-frontend"))
    implementation(project(":gamma.lang"))
    implementation(project(":logging"))
    implementation(project(":oxsts.lang"))
    implementation(project(":portfolios"))
    implementation(project(":verifier"))
    implementation(libs.clikt)
    implementation(libs.clikt.core)
    implementation(libs.guice)
    implementation(libs.slf4j.api)
    implementation(libs.xtext.core)

    testImplementation(testFixtures(project(":cli-common")))
    testImplementation(libs.kotlinx.serialization.core)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.mordant.core)
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.cli.SemantifyrGammaCliKt"
    applicationName = "semantifyr-gamma"
}

registerCliReference(
    mapOf(
        "gamma-help" to listOf("--help"),
        "gamma-verify" to listOf("verify", "--help"),
        "gamma-compile" to listOf("compile", "--help"),
    ),
)

distributions.main {
    contents {
        from(layout.projectDirectory.dir("../models/libraries/default")) {
            into("libraries/gamma")
        }
        from(layout.projectDirectory.file("../models/examples/Simple.gamma")) {
            into("models/smoke")
        }
    }
}

tasks.named("test") {
    inputs.dir(layout.projectDirectory.dir("../models/examples")).withPathSensitivity(PathSensitivity.RELATIVE)
}
