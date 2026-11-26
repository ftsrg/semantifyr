/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.xtext")
}

dependencies {
    api(project(":oxsts.model"))
    implementation(project(":utils"))

    testImplementation(libs.log4j.core)
}

xtext {
    mweFile = "src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2"
    xtextFile = "src/main/java/hu/bme/mit/semantifyr/oxsts/lang/Oxsts.xtext"
}

val metamodel = artifactsOf("metamodel", ":oxsts.model")

val syncModel = tasks.register<Sync>("syncModel") {
    from(metamodel)
    into("model")
}

val textMateDirectory = layout.buildDirectory.dir("generated/textmate")

val generateXtextLanguage = tasks.named("generateXtextLanguage") {
    inputs.files(syncModel).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(textMateDirectory)
}

configurations.consumable("textMateGrammarElements") {
    attributes(semantifyrArtifact("textmate-grammar"))
    outgoing.artifact(
        textMateDirectory.map {
            it.file("oxsts.tmLanguage.json")
        },
    ) {
        builtBy(generateXtextLanguage)
    }
}

configurations.consumable("oxstsLibraryElements") {
    attributes(semantifyrArtifact("oxsts-library"))
    outgoing.artifact(layout.projectDirectory.dir("src/main/resources/hu/bme/mit/semantifyr/oxsts/lang/library"))
}
