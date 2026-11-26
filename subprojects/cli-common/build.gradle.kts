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
    api(project(":backend"))
    api(project(":compiler"))
    api(project(":verifier"))
    api(libs.clikt)
    api(libs.clikt.core)
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)

    implementation(project(":logging"))
    implementation(project(":portfolios"))
    implementation(project(":theta-executor"))
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.slf4j.api)

    testFixturesApi(project(":backend"))
    testFixturesApi(project(":verifier"))
    testFixturesApi(testFixtures(project(":verifier")))
    testFixturesApi(libs.junit.api)
    testFixturesApi(libs.kotlinx.serialization.json)
    testFixturesImplementation(project(":theta-executor"))
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.kotlinx.serialization.core)

    testImplementation(libs.mordant.core)

    add(generate.implementationConfigurationName, project())
    add(generate.implementationConfigurationName, project(":backend"))
    add(generate.implementationConfigurationName, libs.kotlinx.serialization.core)
    add(generate.implementationConfigurationName, libs.kotlinx.serialization.json)
}

val generatedReferenceDirectory = layout.buildDirectory.dir("generated-reference")

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
