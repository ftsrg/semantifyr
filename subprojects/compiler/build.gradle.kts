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
    api(libs.ecore)
    api(libs.emf.common)
    api(libs.guice)
    api(libs.guice.extensions.assistedinject)
    api(libs.kotlinx.serialization.core)
    api(libs.xtext.core)

    api(project(":guice-common"))
    api(project(":oxsts.lang"))
    api(project(":oxsts.model"))

    implementation(project(":logging"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    implementation(libs.xtext.util)

    testImplementation(testFixtures(project(":oxsts.lang")))
    testImplementation(libs.xtext.testing)

    add(benchmark.apiConfigurationName, libs.guice)
    add(benchmark.apiConfigurationName, libs.kotlinx.serialization.core)
    add(benchmark.implementationConfigurationName, project(":logging"))
    add(benchmark.implementationConfigurationName, project(":oxsts.model"))
    add(benchmark.implementationConfigurationName, libs.ecore)
    add(benchmark.implementationConfigurationName, libs.emf.common)
    add(benchmark.implementationConfigurationName, libs.slf4j.api)
}

listOf(
    "test",
    "integrationTest",
).forEach {
    tasks.named(it) {
        inputs.dir(isolated.rootProject.projectDirectory.dir("oxsts-test-models")).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

testing {
    suites {
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project(":oxsts.model"))
                implementation(testFixtures(project(":oxsts.lang")))
                implementation(libs.ecore)
                implementation(libs.emf.common)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.xtext.core)
            }
        }
    }
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
