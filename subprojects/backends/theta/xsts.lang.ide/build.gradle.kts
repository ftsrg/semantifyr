/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.xtext-ide")
}

dependencies {
    implementation(project(":xsts.lang"))

    ideGeneratedClasspath(project(":xsts.lang", configuration = "ideGeneratedOutput"))
}

application {
    mainClass = "hu.bme.mit.semantifyr.xsts.lang.ide.XstsIdeSetup"
}
