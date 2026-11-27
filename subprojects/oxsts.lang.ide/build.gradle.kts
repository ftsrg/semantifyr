/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.artifactsOf

plugins {
    id("hu.bme.mit.semantifyr.cli")
    id("hu.bme.mit.semantifyr.xtext-ide")
    id("hu.bme.mit.semantifyr.suites")
}

dependencies {
    implementation(project(":lang-ide-common"))
    implementation(libs.eclipse.equinox.common)

    testImplementation(libs.xtext.testing)
}

testing {
    suites {
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project(":lang-ide-common"))
            }
        }
    }
}

application {
    mainClass = "hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeSetup"
}

val oxstsLibrary = artifactsOf("oxsts-library", ":oxsts.lang")
val gammaLibrary = artifactsOf("gamma-library", ":gamma-frontend")
val sysmlLibraries = artifactsOf("sysml-libraries", ":sysml-frontend")

val cloneLibraries = tasks.register<Sync>("cloneLibraries") {
    from(oxstsLibrary) {
        into("oxsts")
    }
    from(gammaLibrary) {
        into("gamma")
    }
    from(sysmlLibraries) {
        into("sysml")
    }
    into(layout.buildDirectory.dir("libraries"))
}

tasks.named("test") {
    inputs.files(cloneLibraries).withPathSensitivity(PathSensitivity.RELATIVE)
    listOf(
        "oxsts-test-models/tutorial",
        "oxsts-test-models/phases",
        "oxsts-test-models/smoke",
    ).forEach {
        inputs.dir(isolated.rootProject.projectDirectory.dir(it)).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

tasks.named("integrationTest") {
    inputs.dir(isolated.rootProject.projectDirectory.dir("oxsts-test-models/smoke")).withPathSensitivity(PathSensitivity.RELATIVE)
}
