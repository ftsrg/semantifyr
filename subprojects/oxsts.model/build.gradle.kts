/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    kotlin("jvm")
}

val metamodel = sourceSets.create("metamodel")

java {
    registerFeature("metamodel") {
        usingSourceSet(metamodel)
    }
}

dependencies {
    api(libs.ecore)

    add(metamodel.implementationConfigurationName, libs.ecore)
    add(metamodel.implementationConfigurationName, libs.ecore.codegen)
    add(metamodel.implementationConfigurationName, libs.mwe2.lib)
    add(metamodel.runtimeOnlyConfigurationName, libs.mwe.utils)
    add(metamodel.runtimeOnlyConfigurationName, libs.reload4j)
}

val emfGenDirectory = layout.projectDirectory.dir("src/main/emf-gen")
val modelDirectory = layout.buildDirectory.dir("model")

val generateEPackage = tasks.register<JavaExec>("generateEPackage") {
    classpath(metamodel.runtimeClasspath)
    mainClass.set("hu.bme.mit.semantifyr.oxsts.model.metamodel.GenerateModelKt")

    outputs.dir(emfGenDirectory)
    outputs.dir(modelDirectory)

    args(emfGenDirectory.asFile.path, modelDirectory.get().asFile.path)
}

sourceSets.main {
    java.srcDir(files(emfGenDirectory).builtBy(generateEPackage))
}

configurations.consumable("metamodelElements") {
    attributes(semantifyrArtifact("metamodel"))
    outgoing.artifact(modelDirectory) {
        builtBy(generateEPackage)
    }
}

tasks.clean {
    delete(emfGenDirectory)
}
