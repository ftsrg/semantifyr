/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.benchmark")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(libs.guice)
    api(libs.guice.extensions.assistedinject)

    api(project(":guice-common"))
    api(project(":oxsts.lang"))

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testFixturesApi(project(":oxsts.lang"))
    testFixturesApi(testFixtures(project(":oxsts.lang")))
}

tasks.named("test") {
    inputs.dir(rootProject.layout.projectDirectory.dir("oxsts-test-models"))
        .withPropertyName("oxstsTestModels")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

val benchmarkSysmlCorpus = configurations.dependencyScope("benchmarkSysmlCorpus")
val benchmarkSysmlCorpusFiles = configurations.resolvable("benchmarkSysmlCorpusFiles") {
    extendsFrom(benchmarkSysmlCorpus.get())
    attributes(semantifyrArtifact("compiled-corpus"))
}

dependencies {
    benchmarkSysmlCorpus(project(":sysml-wrapper"))
}

val syncBenchmarkModels = tasks.register<Sync>("syncBenchmarkModels") {
    from(benchmarkSysmlCorpusFiles) {
        include("crossroads.$oxstsFileExtension")
        include("compressedspacecraft.$oxstsFileExtension")
    }
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/libraries/default")) {
        into("library")
    }
    into(layout.buildDirectory.dir("benchmark-models"))
}

tasks.named("benchmark") {
    inputs.files(syncBenchmarkModels)
}
