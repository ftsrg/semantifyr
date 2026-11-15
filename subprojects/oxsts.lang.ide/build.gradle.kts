/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
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

    ideGenerated(project(":oxsts.lang"))

    testImplementation(libs.xtext.testing)
    testRuntimeOnly(libs.slf4j.log4j)
}

application {
    mainClass = "hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeSetup"
}

tasks.named("test") {
    listOf(
        "oxsts-test-models/tutorial",
        "oxsts-test-models/phases",
        "subprojects/oxsts.lang/src/main/resources/hu/bme/mit/semantifyr/oxsts/lang/library",
        "subprojects/frontends/gamma/models/libraries",
        "subprojects/frontends/sysml/models/libraries",
    ).forEach {
        inputs.dir(rootProject.layout.projectDirectory.dir(it))
            .withPropertyName(it)
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
    inputs.file(layout.projectDirectory.file("../semantifyr-vscode/syntaxes/oxsts.tmLanguage.json"))
        .withPropertyName("oxstsTextMateGrammar")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
