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
    implementation(project(":gamma-frontend"))
    implementation(project(":logging"))
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
