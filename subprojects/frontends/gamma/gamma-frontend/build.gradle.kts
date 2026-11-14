/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.corpusModels
import hu.bme.mit.semantifyr.gradle.conventions.readsServedExamples
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.integration")
    id("hu.bme.mit.semantifyr.gradle.conventions.nightly")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":gamma.lang"))
    api(project(":verifier"))

    implementation(project(":logging"))
    implementation(project(":utils"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.serialization.json)

    testFixturesImplementation(libs.kotlinx.serialization.json)
    testFixturesApi(project(":gamma.lang"))
    testFixturesApi(testFixtures(project(":gamma.lang")))
    testFixturesApi(testFixtures(project(":verifier")))
    testFixturesApi(project(":portfolios"))
    testFixturesApi(testFixtures(project(":portfolios")))
}

val syncGammaLibrary = tasks.register<Sync>("syncGammaLibrary") {
    from(layout.projectDirectory.dir("../models/libraries"))
    into(layout.buildDirectory.dir("libraries"))
}

val syncGammaTestModels = tasks.register<Sync>("syncGammaTestModels") {
    from(layout.projectDirectory.dir("../models/examples"))
    into(layout.buildDirectory.dir("test-models"))
}

tasks.processResources {
    from(syncGammaLibrary)
}

tasks.named<Test>("integrationTest") {
    readsServedExamples()
}

tasks.withType<Test>().configureEach {
    inputs.files(syncGammaTestModels)
    inputs.dir(layout.projectDirectory.dir("../models/etc")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("../models/constructs.tsv")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(layout.projectDirectory.dir("../models").asFileTree.matching { include("corpus/**", "semantics/**") }).withPathSensitivity(PathSensitivity.RELATIVE)
}

val compiledExamplesDir = layout.buildDirectory.dir("compiled-examples")
val examplesSourceDir = layout.projectDirectory.dir("../models/examples")

fun gammaExampleTask(name: String) = tasks.register<JavaExec>("compileGammaExample_$name") {
    val sourceFile = examplesSourceDir.file("$name.gamma")
    val targetFile = compiledExamplesDir.map { it.file("$name.oxsts") }
    inputs.file(sourceFile).withPathSensitivity(PathSensitivity.NAME_ONLY)
    // a model's imports resolve in the files beside it
    inputs.dir(examplesSourceDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sourceSets["main"].runtimeClasspath).withNormalizer(ClasspathNormalizer::class)
    outputs.file(targetFile)
    outputs.cacheIf { true }
    mainClass.set("hu.bme.mit.semantifyr.frontends.gamma.examples.CompileGammaExampleKt")
    classpath = sourceSets["main"].runtimeClasspath
    argumentProviders += CommandLineArgumentProvider {
        listOf(sourceFile.asFile.absolutePath, targetFile.get().asFile.absolutePath)
    }
}

val compileGammaExampleTasks = corpusModels(examplesSourceDir).map {
    gammaExampleTask(it.name)
}

val compileGammaExamples = tasks.register("compileGammaExamples") {
    inputs.files(compileGammaExampleTasks)
}

configurations.consumable("compiledExamplesElements") {
    attributes(semantifyrArtifact("compiled-examples"))
    outgoing.artifact(compiledExamplesDir) {
        builtBy(compileGammaExamples)
    }
}
