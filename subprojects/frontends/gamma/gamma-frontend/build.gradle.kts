/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.corpusModels
import hu.bme.mit.semantifyr.gradle.javaRuntimeLibrary
import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.readsServedExamples
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    id("hu.bme.mit.semantifyr.theta")
    id("hu.bme.mit.semantifyr.suites")
    kotlin("jvm")
}

dependencies {
    api(project(":gamma.lang"))
    api(project(":verifier"))

    implementation(project(":logging"))
    implementation(project(":utils"))

    testImplementation(libs.kotlinx.serialization.json)

    testFixturesImplementation(project(":logging"))
    testFixturesApi(testFixtures(project(":gamma.lang")))
    testFixturesApi(testFixtures(project(":verifier")))
    testFixturesApi(project(":portfolios"))
    testFixturesApi(testFixtures(project(":portfolios")))
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

val syncGammaTestModels = tasks.register<Sync>("syncGammaTestModels") {
    from(layout.projectDirectory.dir("../models/examples"))
    into(layout.buildDirectory.dir("test-models"))
}

tasks.processResources {
    from(layout.projectDirectory.dir("../models/libraries"))
}

tasks.named<Test>("integrationTest") {
    readsServedExamples()
}

tasks.withType<Test>().configureEach {
    inputs.files(syncGammaTestModels)
    inputs.dir(layout.projectDirectory.dir("../models/etc")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("../models/constructs.tsv")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        layout.projectDirectory.dir("../models").asFileTree.matching {
            include(
                "corpus/**",
                "semantics/**",
            )
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE)
}

val compiledExamplesDirectory = layout.buildDirectory.dir("compiled-examples")
val examplesSourceDirectory = layout.projectDirectory.dir("../models/examples")

// a file that imports another holds a model's further cases, which nothing consumes compiled
val compileGammaExampleTasks = corpusModels(examplesSourceDirectory).filter { model ->
    providers.fileContents(examplesSourceDirectory.file(model.file)).asText.get().lines().none {
        it.startsWith("import ")
    }
}.map { model ->
    tasks.register<JavaExec>("compileGammaExample_${model.name}") {
        val sourceFile = examplesSourceDirectory.file("${model.name}.gamma")
        val targetFile = compiledExamplesDirectory.map {
            it.file("${model.name}.$oxstsFileExtension")
        }
        inputs.file(sourceFile).withPathSensitivity(PathSensitivity.NAME_ONLY)
        outputs.file(targetFile)
        outputs.cacheIf {
            true
        }
        mainClass = "hu.bme.mit.semantifyr.frontends.gamma.examples.CompileGammaExampleKt"
        classpath = files(sourceSets["main"].runtimeClasspath, exampleLoggingClasspath)
        argumentProviders += CommandLineArgumentProvider {
            listOf(
                sourceFile.asFile.absolutePath,
                targetFile.get().asFile.absolutePath,
            )
        }
    }
}

val compileGammaExamples = tasks.register("compileGammaExamples") {
    inputs.files(compileGammaExampleTasks)
}

configurations.consumable("gammaLibraryElements") {
    attributes(semantifyrArtifact("gamma-library"))
    outgoing.artifact(layout.projectDirectory.dir("../models/libraries/default"))
}

configurations.consumable("gammaExamplesElements") {
    attributes(semantifyrArtifact("gamma-examples"))
    outgoing.artifact(examplesSourceDirectory)
}

configurations.consumable("compiledExamplesElements") {
    attributes(semantifyrArtifact("compiled-examples"))
    outgoing.artifact(compiledExamplesDirectory) {
        builtBy(compileGammaExamples)
    }
}
