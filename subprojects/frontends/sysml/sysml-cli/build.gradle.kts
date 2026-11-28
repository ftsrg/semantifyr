/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
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
    implementation(project(":sysml-frontend"))

    testFixturesApi(project(":verifier"))
    testFixturesApi(libs.clikt)
    testFixturesImplementation(project(":cli-common"))
    testFixturesImplementation(project(":portfolios"))
    testFixturesImplementation(libs.junit.api)
    testFixturesImplementation(libs.mordant.core)

    testImplementation(testFixtures(project(":cli-common")))
    testImplementation(libs.mordant.core)
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.sysml.cli.SemantifyrSysMLCliKt"
    applicationName = "semantifyr-sysml"
}

distributions.main {
    contents {
        from(layout.projectDirectory.dir("../models/libraries/default")) {
            into("libraries/sysmlv2")
        }
    }
}

listOf(
    "test",
    "endToEndTest",
).forEach {
    tasks.named(it) {
        inputs.file(layout.projectDirectory.file("../models/smoke/smoke.sysml")).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
