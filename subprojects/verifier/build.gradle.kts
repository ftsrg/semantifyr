/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
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

val witnessDirectory = isolated.rootProject.projectDirectory.dir("oxsts-test-models/witness")

tasks.test {
    inputs.dir(isolated.rootProject.projectDirectory.dir("oxsts-test-models/simple")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(witnessDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
}

val generatedWitnessDirectory = layout.buildDirectory.dir("generated-witness")

val generatedWitness = tasks.register<JavaExec>("generatedWitness") {
    classpath = generate.runtimeClasspath
    mainClass = "hu.bme.mit.semantifyr.verifier.GenerateWitnessKt"
    inputs.file(witnessDirectory.file("blinker.semantifyr")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(witnessDirectory.file("blinker.report.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(generatedWitnessDirectory)
    outputs.cacheIf {
        true
    }
    val outputDirectory = generatedWitnessDirectory.get().asFile
    workingDir = outputDirectory
    args(witnessDirectory.file("blinker.semantifyr").asFile.relativeTo(outputDirectory).invariantSeparatorsPath, witnessDirectory.file("blinker.report.json").asFile.relativeTo(outputDirectory).invariantSeparatorsPath, "blinker.witness.$oxstsFileExtension")
    doFirst {
        outputDirectory.deleteRecursively()
        outputDirectory.mkdirs()
    }
}

configurations.consumable("generatedWitnessElements") {
    attributes(semantifyrArtifact("generated-witness"))
    outgoing.artifact(generatedWitnessDirectory) {
        builtBy(generatedWitness)
    }
}
