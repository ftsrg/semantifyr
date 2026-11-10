/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.gradle.xtext")
}

val metamodel = configurations.dependencyScope("metamodel")
val metamodelFiles = configurations.resolvable("metamodelFiles") {
    extendsFrom(metamodel.get())
    attributes(semantifyrArtifact("metamodel"))
}

dependencies {
    api(project(":oxsts.model"))
    implementation(project(":utils"))

    testRuntimeOnly(libs.slf4j.log4j)
    // Xtext's FormatterTestHelper asserts through JUnit 4; the Xtext platform sets its version
    testRuntimeOnly(libs.junit4)

    metamodel(project(":oxsts.model"))
}

xtext {
    mweFile = "src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2"
    xtextFile = "src/main/java/hu/bme/mit/semantifyr/oxsts/lang/Oxsts.xtext"
}

val syncModel = tasks.register<Sync>("syncModel") {
    from(metamodelFiles)
    into("model")
}

tasks.named("generateXtextLanguage") {
    inputs.files(syncModel).withPathSensitivity(PathSensitivity.RELATIVE)
}
