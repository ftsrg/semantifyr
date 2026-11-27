/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.corpusModels
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
    inputs.dir(examplesDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("../models/etc")).withPathSensitivity(PathSensitivity.RELATIVE)
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

val compiledExamplesElements = configurations.consumable("compiledExamplesElements") {
    attributes(semantifyrArtifact("compiled-examples"))
}

// a file that imports another holds a model's further cases, which nothing consumes compiled
corpusModels(examplesDirectory).filter { model ->
    providers.fileContents(examplesDirectory.file(model.file)).asText.get().lines().none {
        it.startsWith("import ")
    }
}.forEach { model ->
    val sourceFile = examplesDirectory.file(model.file)
    val targetFile = compiledExamplesDirectory.map {
        it.file("${model.name}.$oxstsFileExtension")
    }
    val compileGammaExample = tasks.register<JavaExec>("compileGammaExample_${model.name}") {
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
    compiledExamplesElements.configure {
        outgoing.artifact(targetFile) {
            builtBy(compileGammaExample)
        }
    }
}

configurations.consumable("gammaLibraryElements") {
    attributes(semantifyrArtifact("gamma-library"))
    outgoing.artifact(librariesDirectory.dir("default"))
}

configurations.consumable("gammaExamplesElements") {
    attributes(semantifyrArtifact("gamma-examples"))
    outgoing.artifact(examplesDirectory)
}
