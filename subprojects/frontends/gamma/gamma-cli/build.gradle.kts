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
    implementation(project(":gamma-frontend"))
    implementation(project(":logging"))
    implementation(project(":portfolios"))

    testImplementation(testFixtures(project(":cli-common")))
    testImplementation(libs.mordant.core)
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.cli.SemantifyrGammaCliKt"
    applicationName = "semantifyr-gamma"
}

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
