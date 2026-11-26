/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.xtext")
    kotlin("jvm")
}

val metamodel = sourceSets.create("metamodel")

dependencies {
    add(metamodel.implementationConfigurationName, project(":oxsts.model")) {
        capabilities {
            requireFeature("metamodel")
        }
    }
}

val emfGeneratedDirectory = layout.projectDirectory.dir("src/main/emf-gen")
val modelDirectory = layout.projectDirectory.dir("model")

val generateEPackage = tasks.register<JavaExec>("generateEPackage") {
    classpath(metamodel.runtimeClasspath)
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.lang.metamodel.GenerateGammaModelKt"

    outputs.dir(emfGeneratedDirectory)
    outputs.file(modelDirectory.file("gamma.ecore"))
    outputs.file(modelDirectory.file("gamma.genmodel"))
    outputs.cacheIf {
        true
    }

    val projectDirectory = layout.projectDirectory.asFile
    args(
        emfGeneratedDirectory.asFile.relativeTo(projectDirectory).path,
        modelDirectory.asFile.relativeTo(projectDirectory).path,
    )
}

sourceSets.main {
    java.srcDir(files(emfGeneratedDirectory).builtBy(generateEPackage))
}

xtext {
    mweFile = "src/main/java/hu/bme/mit/semantifyr/frontends/gamma/lang/GenerateGamma.mwe2"
    xtextFile = "src/main/java/hu/bme/mit/semantifyr/frontends/gamma/lang/Gamma.xtext"
}

val textMateDirectory = layout.buildDirectory.dir("generated/textmate")

val generateXtextLanguage = tasks.named("generateXtextLanguage") {
    inputs.files(generateEPackage).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(textMateDirectory)
}

configurations.consumable("textMateGrammarElements") {
    attributes(semantifyrArtifact("textmate-grammar"))
    outgoing.artifact(
        textMateDirectory.map {
            it.file("gamma.tmLanguage.json")
        },
    ) {
        builtBy(generateXtextLanguage)
    }
}

tasks.clean {
    delete(emfGeneratedDirectory)
}

tasks.named("test") {
    inputs.dir(layout.projectDirectory.dir("../models/corpus")).withPathSensitivity(PathSensitivity.RELATIVE)
}
