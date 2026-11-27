/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.runsWithTheta

plugins {
    id("hu.bme.mit.semantifyr.backend")
    id("hu.bme.mit.semantifyr.benchmark")
    id("hu.bme.mit.semantifyr.theta")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val benchmark = sourceSets["benchmark"]

dependencies {
    api(project(":cex.lang"))
    api(project(":xsts.lang"))
    api(project(":theta-executor"))

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)

    add(benchmark.apiConfigurationName, project(":compiler")) {
        capabilities {
            requireFeature("benchmark")
        }
    }
    add(benchmark.implementationConfigurationName, project(":logging"))
    add(benchmark.implementationConfigurationName, project(":verifier"))
    add(benchmark.implementationConfigurationName, libs.kotlinx.coroutines.core)
}

testing {
    suites {
        named<JvmTestSuite>("semanticTest") {
            dependencies {
                implementation(testFixtures(project(":oxsts.lang")))
            }
        }
    }
}

val sysmlCompiledCorpus = artifactsOf("compiled-corpus", ":sysml-wrapper")
val sysmlLibrary = artifactsOf("sysml-library", ":sysml-frontend")
val gammaCompiledExamples = artifactsOf("compiled-examples", ":gamma-frontend")
val gammaLibrary = artifactsOf("gamma-library", ":gamma-frontend")

val syncBenchmarkModels = tasks.register<Sync>("syncBenchmarkModels") {
    from(sysmlCompiledCorpus) {
        include("crossroads.$oxstsFileExtension")
        into("sysml")
    }
    from(sysmlLibrary) {
        into("sysml/library")
    }
    from(gammaCompiledExamples) {
        include("LevelCrossing.$oxstsFileExtension")
        into("gamma")
    }
    from(gammaLibrary) {
        into("gamma/library")
    }
    from(isolated.rootProject.projectDirectory.dir("oxsts-test-models/tutorial")) {
        include("extending-the-library-nested-states.$oxstsFileExtension")
        into("tutorial")
    }
    into(layout.buildDirectory.dir("benchmark-models"))
}

tasks.named<JavaExec>("benchmark") {
    inputs.files(syncBenchmarkModels)
    runsWithTheta()
}
