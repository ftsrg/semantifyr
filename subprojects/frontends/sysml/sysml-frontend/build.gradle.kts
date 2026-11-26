/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.readsServedExamples
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.theta")
    id("hu.bme.mit.semantifyr.suites")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":portfolios"))

    implementation(project(":sysml-wrapper"))
    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)

    testFixturesApi(testFixtures(project(":portfolios")))
    testFixturesApi(testFixtures(project(":verifier")))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(libs.kotlinx.serialization.json)
}

// the corpus as the tests read it, each served model with its further cases appended
val testSysmlCorpus = configurations.dependencyScope("testSysmlCorpus")
val testSysmlCorpusFiles = configurations.resolvable("testSysmlCorpusFiles") {
    extendsFrom(testSysmlCorpus.get())
    attributes(semantifyrArtifact("sysml-corpus"))
}

dependencies {
    testSysmlCorpus(project(":sysml-wrapper"))
}

val syncSysmlTestModels = tasks.register<Sync>("syncSysmlTestModels") {
    from(testSysmlCorpusFiles)
    into(layout.buildDirectory.dir("test-models"))
}

tasks.processResources {
    from(layout.projectDirectory.dir("../models/libraries"))
}

tasks.withType<Test>().configureEach {
    readsServedExamples()
    inputs.files(syncSysmlTestModels)
    inputs.dir(layout.projectDirectory.dir("../models/etc")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("../models/tutorial")).withPathSensitivity(PathSensitivity.RELATIVE)
}

// the corpus test holds the manifest's decisions against the page that lists them
tasks.test {
    inputs.file(rootProject.layout.projectDirectory.file("subprojects/website/src/docs/learn/frontends/sysml-v2/decisions.mdx"))
        .withPropertyName("decisionsPage")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
