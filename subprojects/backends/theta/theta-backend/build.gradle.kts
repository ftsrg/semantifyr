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
    api(project(":backend"))
    api(project(":cex.lang"))
    api(project(":compiler"))
    api(project(":oxsts.lang"))
    api(project(":oxsts.model"))
    api(project(":xsts.lang"))
    api(project(":theta-executor"))
    api(libs.ecore)
    api(libs.emf.common)
    api(libs.guice)
    api(libs.guice.extensions.assistedinject)

    implementation(project(":guice-common"))
    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    implementation(libs.xtext.core)

    testImplementation(testFixtures(project(":backend")))
    testImplementation(testFixtures(project(":oxsts.lang")))

    add(benchmark.apiConfigurationName, project(":backend"))
    add(benchmark.apiConfigurationName, project(":compiler"))
    add(benchmark.apiConfigurationName, project(":compiler")) {
        capabilities {
            requireFeature("benchmark")
        }
    }
    add(benchmark.apiConfigurationName, project(":theta-executor"))
    add(benchmark.apiConfigurationName, libs.kotlinx.serialization.core)
    add(benchmark.implementationConfigurationName, project(":logging"))
    add(benchmark.implementationConfigurationName, project(":oxsts.model"))
    add(benchmark.implementationConfigurationName, project(":verifier"))
    add(benchmark.implementationConfigurationName, libs.guice)
    add(benchmark.implementationConfigurationName, libs.kotlinx.coroutines.core)
    add(benchmark.implementationConfigurationName, libs.kotlinx.serialization.json)
    add(benchmark.implementationConfigurationName, libs.slf4j.api)
}

testing {
    suites {
        named<JvmTestSuite>("adapterTest") {
            dependencies {
                implementation(project(":compiler"))
                implementation(project(":oxsts.lang"))
                implementation(libs.guice)
            }
        }

        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project(":oxsts.lang"))
                implementation(libs.guice)
            }
        }

        named<JvmTestSuite>("nightlyTest") {
            dependencies {
                implementation(project(":compiler"))
                implementation(project(":oxsts.lang"))
                implementation(libs.guice)
            }
        }

        named<JvmTestSuite>("semanticTest") {
            dependencies {
                implementation(project(":compiler"))
                implementation(project(":oxsts.lang"))
                implementation(testFixtures(project(":oxsts.lang")))
                implementation(libs.guice)
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
