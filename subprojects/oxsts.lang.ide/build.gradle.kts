/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.xtext-ide")
    id("hu.bme.mit.semantifyr.suites")
}

dependencies {
    implementation(project(":lang-ide-common"))
    implementation(libs.eclipse.equinox.common)

    testImplementation(testFixtures(project(":oxsts.lang")))
}

application {
    mainClass = "hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeSetup"
}
