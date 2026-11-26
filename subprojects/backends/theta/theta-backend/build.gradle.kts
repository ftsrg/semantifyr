/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.backend")
    id("hu.bme.mit.semantifyr.benchmark")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":backend"))
    api(project(":cex.lang"))
    api(project(":xsts.lang"))
    api(project(":theta-executor"))
    api(libs.guice.extensions.assistedinject)

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project(":backend")))

    "benchmarkImplementation"(project(":verifier"))
    "benchmarkImplementation"(project(":compiler")) {
        capabilities {
            requireFeature("benchmark")
        }
    }
    "benchmarkImplementation"(libs.kotlinx.coroutines.core)
}

val benchmarkSysmlCorpus = configurations.dependencyScope("benchmarkSysmlCorpus")
val benchmarkSysmlCorpusFiles = configurations.resolvable("benchmarkSysmlCorpusFiles") {
    extendsFrom(benchmarkSysmlCorpus.get())
    attributes(semantifyrArtifact("compiled-corpus"))
}

val benchmarkGammaExamples = configurations.dependencyScope("benchmarkGammaExamples")
val benchmarkGammaExamplesFiles = configurations.resolvable("benchmarkGammaExamplesFiles") {
    extendsFrom(benchmarkGammaExamples.get())
    attributes(semantifyrArtifact("compiled-examples"))
}

dependencies {
    benchmarkSysmlCorpus(project(":sysml-wrapper"))
    benchmarkGammaExamples(project(":gamma-frontend"))
}

val syncBenchmarkModels = tasks.register<Sync>("syncBenchmarkModels") {
    from(benchmarkSysmlCorpusFiles) {
        include("crossroads.$oxstsFileExtension")
        into("sysml")
    }
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/libraries/default")) {
        into("sysml/library")
    }
    from(benchmarkGammaExamplesFiles) {
        include("LevelCrossing.$oxstsFileExtension")
        into("gamma")
    }
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/libraries/default")) {
        into("gamma/library")
    }
    from(rootProject.layout.projectDirectory.dir("oxsts-test-models/tutorial")) {
        include("extending-the-library-nested-states.$oxstsFileExtension")
        into("tutorial")
    }
    into(layout.buildDirectory.dir("benchmark-models"))
}

tasks.named<JavaExec>("benchmark") {
    inputs.files(syncBenchmarkModels)
}
