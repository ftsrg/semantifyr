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
    implementation(project(":backend"))
    implementation(project(":compiler"))
    implementation(project(":gamma-frontend"))
    implementation(project(":gamma.lang"))
    implementation(project(":lang-ide-common"))
    implementation(project(":oxsts.lang"))
    implementation(project(":verifier"))
    implementation(libs.antlr.runtime)
    implementation(libs.ecore)
    implementation(libs.emf.common)
    implementation(libs.guava)
    implementation(libs.guice)
    implementation(libs.kotlin.stdlib)
    implementation(libs.lsp4j)
    implementation(libs.slf4j.api)
    implementation(libs.xtext.core)
    implementation(libs.xtext.ide)
    implementation(libs.xtext.util)

    testImplementation(project(":portfolios"))
    testImplementation(libs.gson)
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.lang.ide.GammaIdeSetup"
}

tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("../models/examples")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("../models/etc")).withPathSensitivity(PathSensitivity.RELATIVE)
}
