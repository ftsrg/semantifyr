/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.registerCliReference
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.distribution-test")
    kotlin("jvm")
}

dependencies {
    implementation(project(":backend"))
    implementation(project(":compiler"))
    implementation(project(":logging"))
    implementation(project(":oxsts.lang"))
    implementation(project(":oxsts.model"))
    implementation(project(":portfolios"))
    implementation(project(":verifier"))
    implementation(libs.clikt)
    implementation(libs.clikt.core)
    implementation(libs.ecore)
    implementation(libs.guice)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    implementation(libs.xtext.core)

    testImplementation(testFixtures(project(":cli-common")))
    testImplementation(testFixtures(project(":verifier")))
    testImplementation(libs.log4j.api)
    testImplementation(libs.log4j.core)
    testImplementation(libs.mordant.core)
}

application {
    mainClass = "hu.bme.mit.semantifyr.cli.SemantifyrCliKt"
    applicationName = "semantifyr"
}

registerCliReference(
    mapOf(
        "help" to listOf("--help"),
        "verify" to listOf("verify", "--help"),
        "compile" to listOf("compile", "--help"),
        "list" to listOf("list", "--help"),
        "metrics" to listOf("metrics", "--help"),
        "portfolios-help" to listOf("portfolios", "--help"),
        "portfolios" to listOf("portfolios"),
    ),
)

val rootDirectory = isolated.rootProject.projectDirectory

tasks.withType<Test>().configureEach {
    inputs.dir(rootDirectory.dir("oxsts-test-models")).withPathSensitivity(PathSensitivity.RELATIVE)
}

distributions.main {
    contents {
        from(rootDirectory.dir("oxsts-test-models/smoke")) {
            into("models/smoke")
        }
    }
}

val phasesModel = rootDirectory.file("oxsts-test-models/phases/room.semantifyr")
val phasesDirectory = layout.buildDirectory.dir("phases")

val phases = tasks.register<JavaExec>("phases") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = application.mainClass
    inputs.file(phasesModel).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(phasesDirectory)
    outputs.cacheIf {
        true
    }
    val outputDirectory = phasesDirectory.get().asFile
    workingDir = outputDirectory
    args("compile", "--artifacts=debug", "--disable-artifact=mapping", "--artifact-directory=.", "--output=compiled.$oxstsFileExtension", phasesModel.asFile.relativeTo(outputDirectory).invariantSeparatorsPath)
    doFirst {
        outputDirectory.deleteRecursively()
        outputDirectory.mkdirs()
    }
    doLast {
        outputDirectory.walkTopDown().filter {
            it.isFile
        }.forEach {
            it.writeText(it.readText().replace("\r\n", "\n"))
        }
    }
}

configurations.consumable("phaseWalkElements") {
    attributes(semantifyrArtifact("phase-walk"))
    outgoing.artifact(phasesDirectory) {
        builtBy(phases)
    }
}
