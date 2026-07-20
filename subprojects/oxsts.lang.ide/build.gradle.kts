/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.xtext-ide")
}

dependencies {
    api(project(":oxsts.lang"))
    api(project(":lang-ide-common"))
    implementation(project(":portfolios"))

    ideGeneratedClasspath(project(":oxsts.lang", configuration = "ideGeneratedOutput"))
}

application {
    mainClass = "hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeSetup"
}
