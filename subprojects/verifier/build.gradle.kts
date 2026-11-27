/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.readsTestModels
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val generate = sourceSets.create("generate")

dependencies {
    api(project(":backend"))

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":backend")))
    testImplementation(testFixtures(project(":oxsts.lang")))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.opentest4j)

    testFixturesApi(libs.junit.params)
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.junit.api)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.kotlinx.serialization.json)

    add(generate.implementationConfigurationName, project())
    add(generate.implementationConfigurationName, libs.kotlinx.serialization.json)
}

tasks.test {
    readsTestModels("simple", "witness")
}

val witnessDirectory = isolated.rootProject.projectDirectory.dir("oxsts-test-models/witness")
val witnessModel = witnessDirectory.file("blinker.semantifyr")
val witnessReport = witnessDirectory.file("blinker.report.json")
val generatedWitnessDirectory = layout.buildDirectory.dir("generated-witness")
val generatedWitnessFile = generatedWitnessDirectory.map {
    it.file("blinker.witness.$oxstsFileExtension")
}

val generatedWitness = tasks.register<JavaExec>("generatedWitness") {
    classpath = generate.runtimeClasspath
    mainClass = "hu.bme.mit.semantifyr.verifier.GenerateWitnessKt"
    inputs.file(witnessModel).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(witnessReport).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(generatedWitnessDirectory)
    outputs.cacheIf {
        true
    }

    val projectDirectory = layout.projectDirectory.asFile
    val modelPath = witnessModel.asFile.relativeTo(projectDirectory).invariantSeparatorsPath
    val reportPath = witnessReport.asFile.relativeTo(projectDirectory).invariantSeparatorsPath
    val witnessPath = generatedWitnessFile.map {
        it.asFile.relativeTo(projectDirectory).invariantSeparatorsPath
    }
    argumentProviders += CommandLineArgumentProvider {
        listOf(modelPath, reportPath, witnessPath.get())
    }
}

configurations.consumable("generatedWitnessElements") {
    attributes(semantifyrArtifact("generated-witness"))
    outgoing.artifact(generatedWitnessDirectory) {
        builtBy(generatedWitness)
    }
}
