/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
}

val backendRunServiceProvider = gradle.sharedServices.registerBackendRunService()

val libs = the<LibrariesForLibs>()

testing {
    suites {
        register<JvmTestSuite>("endToEndTest") {
            useJUnitJupiter(libs.versions.junit)

            dependencies {
                implementation(project())
                implementation(testFixtures(project()))

                implementation(testFixtures(project(":verifier")))

                runtimeOnly(libs.slf4j.log4j)
            }

            targets.all {
                testTask.configure {
                    usesService(backendRunServiceProvider)

                    shouldRunAfter(suites.named("test"))
                }
            }
        }
    }
}
