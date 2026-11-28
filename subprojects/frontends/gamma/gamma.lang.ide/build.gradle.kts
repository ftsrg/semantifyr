/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.xtext-ide")
}

dependencies {
    implementation(project(":gamma-frontend"))
    implementation(project(":lang-ide-common"))

    testImplementation(project(":portfolios"))
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.lang.ide.GammaIdeSetup"
}

tasks.withType<Test>().configureEach {
    inputs.file(layout.projectDirectory.file("../models/smoke/smoke.gamma")).withPathSensitivity(PathSensitivity.RELATIVE)
}
