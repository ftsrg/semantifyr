/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.readsServedExamples
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.writesStandardOutputTo

plugins {
    id("hu.bme.mit.semantifyr.theta")
    id("hu.bme.mit.semantifyr.suites")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val generate = sourceSets.create("generate")

dependencies {
    api(project(":portfolios"))

    implementation(project(":sysml-wrapper"))
    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)

    testFixturesApi(testFixtures(project(":portfolios")))
    testFixturesApi(testFixtures(project(":verifier")))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(libs.kotlinx.serialization.json)

    add(generate.implementationConfigurationName, testFixtures(project(":verifier")))
    add(generate.implementationConfigurationName, libs.kotlinx.serialization.json)
}

val librariesDirectory = layout.projectDirectory.dir("../models/libraries")
val examplesDirectory = layout.projectDirectory.dir("../models/examples")

configurations.consumable("sysmlLibraryElements") {
    attributes(semantifyrArtifact("sysml-library"))
    outgoing.artifact(librariesDirectory.dir("default"))
}

configurations.consumable("sysmlLibrariesElements") {
    attributes(semantifyrArtifact("sysml-libraries"))
    outgoing.artifact(librariesDirectory)
}

configurations.consumable("sysmlExamplesElements") {
    attributes(semantifyrArtifact("sysml-examples"))
    outgoing.artifact(examplesDirectory)
}

val sysmlCorpus = artifactsOf("sysml-corpus", ":sysml-wrapper")

// the corpus as the tests read it, each served model with its further cases appended
val syncSysmlTestModels = tasks.register<Sync>("syncSysmlTestModels") {
    from(sysmlCorpus)
    into(layout.buildDirectory.dir("test-models"))
}

val decisionsPage = artifactsOf("sysml-decisions-page", ":website")

val cloneDecisionsPage = tasks.register<Sync>("cloneDecisionsPage") {
    from(decisionsPage)
    into(layout.buildDirectory.dir("decisions-page"))
}

tasks.processResources {
    from(librariesDirectory)
}

tasks.withType<Test>().configureEach {
    readsServedExamples()
    inputs.files(syncSysmlTestModels)
    inputs.dir(layout.projectDirectory.dir("../models/etc")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("../models/tutorial")).withPathSensitivity(PathSensitivity.RELATIVE)
}

// the corpus test holds the manifest's decisions against the page that lists them
tasks.test {
    inputs.files(cloneDecisionsPage).withPathSensitivity(PathSensitivity.RELATIVE)
}

val generatedReferenceDirectory = layout.buildDirectory.dir("generated-reference")

// the cases the corpus manifest ties to each decision of the decisions page
val decisionCases = tasks.register<JavaExec>("generatedReference_decision-cases") {
    classpath = generate.runtimeClasspath
    mainClass = "hu.bme.mit.semantifyr.frontends.sysml.GenerateDecisionCasesKt"
    inputs.file(examplesDirectory.file("manifest.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(decisionsPage).withPathSensitivity(PathSensitivity.RELATIVE)
    writesStandardOutputTo(
        generatedReferenceDirectory.map {
            it.file("decision-cases.json")
        },
    )
    outputs.cacheIf {
        true
    }
    val module = projectDir
    argumentProviders += CommandLineArgumentProvider {
        listOf(examplesDirectory.asFile.relativeTo(module).invariantSeparatorsPath, decisionsPage.singleFile.relativeTo(module).invariantSeparatorsPath)
    }
}

configurations.consumable("generatedReferenceElements") {
    attributes(semantifyrArtifact("generated-reference"))
    outgoing.artifact(generatedReferenceDirectory) {
        builtBy(decisionCases)
    }
}
