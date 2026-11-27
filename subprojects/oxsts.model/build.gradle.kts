/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    kotlin("jvm")
}

val metamodel = sourceSets.create("metamodel")

java {
    registerFeature("metamodel") {
        usingSourceSet(metamodel)
    }
}

dependencies {
    api(platform(libs.xtext.bom))
    api(libs.ecore)
    api(libs.emf.common)

    add(metamodel.apiConfigurationName, platform(libs.xtext.bom))
    add(metamodel.apiConfigurationName, libs.ecore)
    add(metamodel.implementationConfigurationName, libs.ecore.codegen)
    add(metamodel.implementationConfigurationName, libs.ecore.xmi)
    add(metamodel.implementationConfigurationName, libs.emf.common)
    add(metamodel.implementationConfigurationName, libs.mwe2.lib)
    add(metamodel.implementationConfigurationName, libs.mwe2.runtime)
    add(metamodel.runtimeOnlyConfigurationName, libs.mwe.utils)
    add(metamodel.runtimeOnlyConfigurationName, libs.reload4j)
}

val emfGeneratedDirectory = layout.projectDirectory.dir("src/main/emf-gen")
val modelDirectory = layout.buildDirectory.dir("model")

val generateEPackage = tasks.register<JavaExec>("generateEPackage") {
    classpath(metamodel.runtimeClasspath)
    mainClass = "hu.bme.mit.semantifyr.oxsts.model.metamodel.GenerateModelKt"

    outputs.dir(emfGeneratedDirectory)
    outputs.dir(modelDirectory)
    outputs.cacheIf {
        true
    }

    val projectDirectory = layout.projectDirectory.asFile
    val emfGeneratedPath = emfGeneratedDirectory.asFile.relativeTo(projectDirectory).path
    val modelPath = modelDirectory.map {
        it.asFile.relativeTo(projectDirectory).path
    }
    argumentProviders += CommandLineArgumentProvider {
        listOf(
            emfGeneratedPath,
            modelPath.get(),
        )
    }
}

sourceSets.main {
    java.srcDir(files(emfGeneratedDirectory).builtBy(generateEPackage))
}

configurations.consumable("metamodelElements") {
    attributes(semantifyrArtifact("metamodel"))
    outgoing.artifact(modelDirectory) {
        builtBy(generateEPackage)
    }
}

tasks.clean {
    delete(emfGeneratedDirectory)
}
