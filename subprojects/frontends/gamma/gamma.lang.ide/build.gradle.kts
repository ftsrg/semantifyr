/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.xtext-ide")
}

dependencies {
    api(project(":lang-ide-common"))
    api(project(":gamma.lang"))
    implementation(project(":gamma-frontend"))

    ideGeneratedClasspath(project(":gamma.lang", configuration = "ideGeneratedOutput"))
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.lang.ide.GammaIdeSetup"
}
