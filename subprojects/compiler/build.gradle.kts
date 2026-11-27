/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.oxstsFileExtension

plugins {
    id("hu.bme.mit.semantifyr.suites")
    id("hu.bme.mit.semantifyr.benchmark")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val benchmark = sourceSets["benchmark"]

dependencies {
    api(libs.guice.extensions.assistedinject)
    api(libs.kotlinx.serialization.core)

    api(project(":guice-common"))
    api(project(":oxsts.lang"))

    implementation(project(":logging"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":oxsts.lang")))

    add(benchmark.implementationConfigurationName, project(":logging"))
}

val sysmlCompiledCorpus = artifactsOf("compiled-corpus", ":sysml-wrapper")
val sysmlLibrary = artifactsOf("sysml-library", ":sysml-frontend")

val syncBenchmarkModels = tasks.register<Sync>("syncBenchmarkModels") {
    from(sysmlCompiledCorpus) {
        include("crossroads.$oxstsFileExtension")
        include("compressedspacecraft.$oxstsFileExtension")
    }
    from(sysmlLibrary) {
        into("library")
    }
    into(layout.buildDirectory.dir("benchmark-models"))
}

tasks.named("benchmark") {
    inputs.files(syncBenchmarkModels)
}
