/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.javaRuntimeLibrary
import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.theta")
    id("hu.bme.mit.semantifyr.suites")
    kotlin("jvm")
}

dependencies {
    api(project(":gamma.lang"))
    api(project(":verifier"))

    implementation(project(":logging"))
    implementation(project(":utils"))

    testImplementation(testFixtures(project(":gamma.lang")))
    testImplementation(testFixtures(project(":verifier")))
    testImplementation(libs.kotlinx.serialization.json)

    testFixturesApi(libs.junit.params)
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":portfolios"))
    testFixturesImplementation(testFixtures(project(":verifier")))
    testFixturesImplementation(libs.junit.api)
}

val exampleLogging = configurations.dependencyScope("exampleLogging")
val exampleLoggingClasspath = configurations.resolvable("exampleLoggingClasspath") {
    extendsFrom(exampleLogging.get())
    shouldResolveConsistentlyWith(configurations.runtimeClasspath.get())
    attributes(javaRuntimeLibrary())
}

dependencies {
    exampleLogging(libs.slf4j.log4j)
}

val examplesDirectory = layout.projectDirectory.dir("../models/examples")
val librariesDirectory = layout.projectDirectory.dir("../models/libraries")

tasks.processResources {
    from(librariesDirectory)
}

tasks.withType<Test>().configureEach {
    inputs.files(
        layout.projectDirectory.dir("../models").asFileTree.matching {
            include(
                "corpus/**",
                "semantics/**",
                "smoke/**",
            )
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE)
}

listOf(
    "integrationTest",
    "nightlyTest",
).forEach {
    tasks.named(it) {
        inputs.dir(examplesDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

val smokeSourceFile = layout.projectDirectory.file("../models/smoke/smoke.gamma")
val compiledSmokeFile = layout.buildDirectory.file("compiled-smoke/smoke.$oxstsFileExtension")

val compileGammaSmoke = tasks.register<JavaExec>("compileGammaSmoke") {
    // the argument provider takes locals, since the configuration cache cannot serialize the script's own vals
    val sourceFile = smokeSourceFile
    val targetFile = compiledSmokeFile
    inputs.file(sourceFile).withPathSensitivity(PathSensitivity.NAME_ONLY)
    outputs.file(targetFile)
    outputs.cacheIf {
        true
    }
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.examples.CompileGammaExampleKt"
    classpath = files(sourceSets["main"].runtimeClasspath, exampleLoggingClasspath)
    argumentProviders += CommandLineArgumentProvider {
        listOf(sourceFile.asFile.absolutePath, targetFile.get().asFile.absolutePath)
    }
}

configurations.consumable("compiledSmokeElements") {
    attributes(semantifyrArtifact("compiled-smoke"))
    outgoing.artifact(compiledSmokeFile) {
        builtBy(compileGammaSmoke)
    }
}
