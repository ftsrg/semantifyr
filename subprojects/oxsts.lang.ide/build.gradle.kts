/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.cli")
    id("hu.bme.mit.semantifyr.xtext-ide")
    id("hu.bme.mit.semantifyr.suites")
}

dependencies {
    api(project(":oxsts.lang"))
    api(project(":lang-ide-common"))
    implementation(project(":portfolios"))

    ideGenerated(project(":oxsts.lang"))

    testImplementation(libs.xtext.testing)
}

application {
    mainClass = "hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeSetup"
}

tasks.named("test") {
    listOf(
        "oxsts-test-models/tutorial",
        "oxsts-test-models/phases",
        "oxsts-test-models/smoke",
        "subprojects/oxsts.lang/src/main/resources/hu/bme/mit/semantifyr/oxsts/lang/library",
        "subprojects/frontends/gamma/models/libraries",
        "subprojects/frontends/sysml/models/libraries",
    ).forEach {
        inputs.dir(rootProject.layout.projectDirectory.dir(it))
            .withPropertyName(it)
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

tasks.named("integrationTest") {
    inputs.dir(rootProject.layout.projectDirectory.dir("oxsts-test-models/smoke"))
        .withPropertyName("oxstsSmokeModels")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
