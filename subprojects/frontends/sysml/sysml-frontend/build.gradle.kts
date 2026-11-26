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
    api(project(":backend"))
    api(project(":compiler"))
    api(project(":verifier"))
    api(libs.guice)

    implementation(project(":oxsts.lang"))
    implementation(project(":sysml-wrapper"))
    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    implementation(libs.xtext.core)

    testImplementation(project(":oxsts.model"))
    testImplementation(testFixtures(project(":portfolios")))
    testImplementation(libs.ecore)
    testImplementation(libs.emf.common)

    testFixturesApi(project(":backend"))
    testFixturesApi(project(":compiler"))
    testFixturesApi(project(":verifier"))
    testFixturesApi(libs.kotlinx.serialization.core)
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":oxsts.lang"))
    testFixturesImplementation(testFixtures(project(":portfolios")))
    testFixturesImplementation(testFixtures(project(":verifier")))
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.guice)
    testFixturesImplementation(libs.kotlinx.serialization.json)
    testFixturesImplementation(libs.slf4j.api)

    add(generate.implementationConfigurationName, testFixtures(project(":verifier")))
    add(generate.implementationConfigurationName, libs.kotlinx.serialization.core)
    add(generate.implementationConfigurationName, libs.kotlinx.serialization.json)
}

testing {
    suites {
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project(":backend"))
                implementation(project(":compiler"))
                implementation(testFixtures(project(":portfolios")))
            }
        }

        named<JvmTestSuite>("nightlyTest") {
            dependencies {
                implementation(project(":backend"))
                implementation(project(":compiler"))
            }
        }

        named<JvmTestSuite>("semanticTest") {
            dependencies {
                implementation(project(":backend"))
                implementation(project(":compiler"))
                implementation(project(":oxsts.lang"))
                implementation(libs.guice)
                implementation(libs.xtext.core)
            }
        }
    }
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
    val examples = examplesDirectory
    val page = decisionsPage
    argumentProviders += CommandLineArgumentProvider {
        listOf(examples.asFile.relativeTo(module).invariantSeparatorsPath, page.singleFile.relativeTo(module).invariantSeparatorsPath)
    }
}

configurations.consumable("generatedReferenceElements") {
    attributes(semantifyrArtifact("generated-reference"))
    outgoing.artifact(generatedReferenceDirectory) {
        builtBy(decisionCases)
    }
}
