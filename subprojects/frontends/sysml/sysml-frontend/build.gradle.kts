/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.readsServedExamples
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.theta")
    id("hu.bme.mit.semantifyr.suites")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":verifier"))

    implementation(project(":sysml-wrapper"))
    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project(":portfolios")))

    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(testFixtures(project(":portfolios")))
    testFixturesImplementation(testFixtures(project(":verifier")))
    testFixturesImplementation(libs.assertj.core)
}

testing {
    suites {
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(testFixtures(project(":portfolios")))
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

configurations.consumable("sysmlExamplesElements") {
    attributes(semantifyrArtifact("sysml-examples"))
    outgoing.artifact(examplesDirectory)
}

val sysmlCorpus = artifactsOf("sysml-corpus", ":sysml-wrapper")

val syncSysmlTestModels = tasks.register<Sync>("syncSysmlTestModels") {
    from(sysmlCorpus)
    into(layout.buildDirectory.dir("test-models"))
}

tasks.processResources {
    from(librariesDirectory)
}

tasks.withType<Test>().configureEach {
    readsServedExamples()
    inputs.files(syncSysmlTestModels).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("../models/etc")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("../models/tutorial")).withPathSensitivity(PathSensitivity.RELATIVE)
}
