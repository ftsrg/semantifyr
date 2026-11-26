/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.writesStandardOutputTo

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val generate = sourceSets.create("generate")

dependencies {
    api(project(":portfolios"))
    api(libs.clikt)

    implementation(project(":logging"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.log4j.core)

    testFixturesApi(testFixtures(project(":verifier")))
    testFixturesApi(libs.kotlinx.serialization.json)

    add(generate.implementationConfigurationName, project())
    add(generate.implementationConfigurationName, libs.kotlinx.serialization.json)
}

val generatedReferenceDirectory = layout.buildDirectory.dir("generated-reference")

// what the website's pages show of a type, generated from it
val generatedReference = listOf(
    "exit-codes",
    "verdicts",
).map { name ->
    tasks.register<JavaExec>("generatedReference_$name") {
        classpath = generate.runtimeClasspath
        mainClass = "hu.bme.mit.semantifyr.cli.common.GenerateReferenceKt"
        args(name)
        writesStandardOutputTo(
            generatedReferenceDirectory.map {
                it.file("$name.json")
            },
        )
        outputs.cacheIf {
            true
        }
    }
}

configurations.consumable("generatedReferenceElements") {
    attributes(semantifyrArtifact("generated-reference"))
    outgoing.artifact(generatedReferenceDirectory) {
        builtBy(generatedReference)
    }
}
