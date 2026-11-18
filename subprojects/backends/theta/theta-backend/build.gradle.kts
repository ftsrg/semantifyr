/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.backend")
    id("hu.bme.mit.semantifyr.gradle.conventions.benchmark")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":backend"))
    api(project(":xsts.lang"))
    api(project(":theta-executor"))
    api(libs.guice.extensions.assistedinject)

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":backend")))

    "benchmarkImplementation"(project(":verifier"))
    "benchmarkImplementation"(project(":compiler")) {
        capabilities {
            requireFeature("benchmark")
        }
    }
    "benchmarkImplementation"(libs.kotlinx.coroutines.core)
}

tasks.named("test") {
    inputs.files(tasks.named("cloneOxstsTestModels"))
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

val cloneTheta = tasks.named<Sync>("cloneTheta")

tasks.named<JavaExec>("benchmark") {
    inputs.files(syncBenchmarkModels)
    inputs.files(cloneTheta)
    val thetaDirectory = cloneTheta.map {
        it.destinationDir.absolutePath
    }
    doFirst {
        environment("PATH", "${thetaDirectory.get()}${File.pathSeparator}${System.getenv("PATH")}")
    }
}
