/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.xtext")
    kotlin("jvm")
}

val metamodel = sourceSets.create("metamodel")

dependencies {
    add(metamodel.implementationConfigurationName, project(":oxsts.model")) {
        capabilities {
            requireFeature("metamodel")
        }
    }

    testRuntimeOnly(libs.slf4j.log4j)
}

val emfGenDirectory = layout.projectDirectory.dir("src/main/emf-gen")
val modelDirectory = layout.projectDirectory.dir("model")

val generateEPackage = tasks.register<JavaExec>("generateEPackage") {
    classpath(metamodel.runtimeClasspath)
    mainClass.set("hu.bme.mit.semantifyr.frontends.gamma.lang.metamodel.GenerateGammaModelKt")

    outputs.dir(emfGenDirectory)
    outputs.file(modelDirectory.file("gamma.ecore"))
    outputs.file(modelDirectory.file("gamma.genmodel"))

    args(emfGenDirectory.asFile.path, modelDirectory.asFile.path)
}

sourceSets.main {
    java.srcDir(files(emfGenDirectory).builtBy(generateEPackage))
}

xtext {
    mweFile = "src/main/java/hu/bme/mit/semantifyr/frontends/gamma/lang/GenerateGamma.mwe2"
    xtextFile = "src/main/java/hu/bme/mit/semantifyr/frontends/gamma/lang/Gamma.xtext"
}

tasks.named("generateXtextLanguage") {
    inputs.files(generateEPackage).withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.clean {
    delete(emfGenDirectory)
}

tasks.named("test") {
    inputs.dir(layout.projectDirectory.dir("../models/corpus"))
        .withPropertyName("gammaCorpus")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
