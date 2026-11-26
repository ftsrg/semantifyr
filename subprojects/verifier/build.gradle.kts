/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.writesStandardOutputTo

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val generate = sourceSets.create("generate")

dependencies {
    api(project(":backend"))
    api(project(":compiler"))
    api(project(":oxsts.lang"))
    api(project(":oxsts.model"))
    api(libs.guice)
    api(libs.guice.extensions.assistedinject)
    api(libs.kotlinx.serialization.core)
    api(libs.slf4j.api)
    api(libs.xtext.core)

    implementation(project(":logging"))
    implementation(libs.ecore)
    implementation(libs.emf.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":backend")))
    testImplementation(testFixtures(project(":oxsts.lang")))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.opentest4j)

    testFixturesApi(project(":backend"))
    testFixturesApi(project(":compiler"))
    testFixturesApi(libs.guice)
    testFixturesApi(libs.junit.params)
    testFixturesApi(libs.kotlinx.serialization.core)
    testFixturesApi(libs.slf4j.api)
    testFixturesImplementation(project(":guice-common"))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":oxsts.model"))
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.ecore)
    testFixturesImplementation(libs.emf.common)
    testFixturesImplementation(libs.junit.api)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.kotlinx.serialization.json)

    add(generate.implementationConfigurationName, project())
    add(generate.implementationConfigurationName, project(":compiler"))
    add(generate.implementationConfigurationName, project(":oxsts.lang"))
    add(generate.implementationConfigurationName, project(":oxsts.model"))
    add(generate.implementationConfigurationName, libs.guice)
    add(generate.implementationConfigurationName, libs.kotlinx.serialization.core)
    add(generate.implementationConfigurationName, libs.kotlinx.serialization.json)
}

val witnessDirectory = isolated.rootProject.projectDirectory.dir("oxsts-test-models/witness")

tasks.test {
    inputs.dir(isolated.rootProject.projectDirectory.dir("oxsts-test-models/simple")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(witnessDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
}

val generatedReferenceDirectory = layout.buildDirectory.dir("generated-reference")

// what the website's pages show of a type, generated from it
val generatedReference = tasks.register<JavaExec>("generatedReference_report-fields") {
    classpath = generate.runtimeClasspath
    mainClass = "hu.bme.mit.semantifyr.verifier.GenerateReferenceKt"
    args("report-fields")
    writesStandardOutputTo(
        generatedReferenceDirectory.map {
            it.file("report-fields.json")
        },
    )
    outputs.cacheIf {
        true
    }
}

configurations.consumable("generatedReferenceElements") {
    attributes(semantifyrArtifact("generated-reference"))
    outgoing.artifact(generatedReferenceDirectory) {
        builtBy(generatedReference)
    }
}

// the witness model the verifier derives from the stored timed report
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
    // relative paths keep the arguments, and so the cache key, the same in every checkout
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
