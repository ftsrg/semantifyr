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
    implementation(project(":sysml-frontend"))
    implementation(project(":sysml-wrapper"))
    implementation(project(":logging"))

    testFixturesApi(project(":cli-common"))
    testFixturesApi(testFixtures(project(":cli-common")))
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
