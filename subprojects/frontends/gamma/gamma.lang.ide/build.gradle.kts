/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.cli")
    id("hu.bme.mit.semantifyr.xtext-ide")
}

dependencies {
    api(project(":lang-ide-common"))
    api(project(":gamma.lang"))
    implementation(project(":gamma-frontend"))
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.lang.ide.GammaIdeSetup"
}

tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("../models/examples")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("../models/etc")).withPathSensitivity(PathSensitivity.RELATIVE)
}
