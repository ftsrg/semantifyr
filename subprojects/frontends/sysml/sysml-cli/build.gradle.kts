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
    implementation(project(":logging"))
    implementation(project(":oxsts.lang"))
    implementation(project(":portfolios"))
    implementation(project(":sysml-frontend"))
    implementation(project(":sysml-wrapper"))
    implementation(project(":verifier"))
    implementation(libs.clikt)
    implementation(libs.clikt.core)
    implementation(libs.guice)
    implementation(libs.slf4j.api)
    implementation(libs.xtext.core)

    testFixturesApi(project(":verifier"))
    testFixturesApi(libs.clikt)
    testFixturesImplementation(project(":cli-common"))
    testFixturesImplementation(project(":oxsts.lang"))
    testFixturesImplementation(project(":portfolios"))
    testFixturesImplementation(libs.clikt.core)
    testFixturesImplementation(libs.guice)
    testFixturesImplementation(libs.junit.api)
    testFixturesImplementation(libs.mordant.core)
    testFixturesImplementation(libs.xtext.core)

    testImplementation(libs.mordant.core)
}

testing {
    suites {
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project(":backend"))
                implementation(testFixtures(project(":cli-common")))
                implementation(libs.clikt)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.sysml.cli.SemantifyrSysMLCliKt"
    applicationName = "semantifyr-sysml"
}

registerCliReference(
    mapOf(
        "sysml-help" to listOf("--help"),
        "sysml-verify" to listOf("verify", "--help"),
        "sysml-compile" to listOf("compile", "--help"),
    ),
)

val rootDirectory = isolated.rootProject.projectDirectory

distributions.main {
    contents {
        from(layout.projectDirectory.dir("../models/libraries/default")) {
            into("libraries/sysmlv2")
        }
        from(layout.projectDirectory.file("../models/tutorial/room.sysml")) {
            into("models/smoke")
        }
    }
}

listOf(
    "test",
    "integrationTest",
).forEach {
    tasks.named(it) {
        inputs.dir(layout.projectDirectory.dir("../models/tutorial")).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.dir(rootDirectory.dir("oxsts-test-models/semantic")).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
