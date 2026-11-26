/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import hu.bme.mit.semantifyr.gradle.registerBackendRunService

plugins {
    id("hu.bme.mit.semantifyr.jvm")
}

val backendRuns = providers.gradleProperty("backendRuns").map {
    it.toInt()
}.orElse(1)

val backendRunServiceProvider = gradle.sharedServices.registerBackendRunService(backendRuns)

testing {
    suites {
        listOf(
            "adapterTest",
            "semanticTest",
            "integrationTest",
            "endToEndTest",
            "nightlyTest",
        ).forEach { suite ->
            register<JvmTestSuite>(suite) {
                dependencies {
                    implementation(testFixtures(project()))
                    implementation(testFixtures(project(":verifier")))
                    if (isolated.path != ":portfolios") {
                        implementation(project(":portfolios"))
                    }
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
}

tasks.named("check") {
    dependsOn(testing.suites.named("adapterTest"))
}

tasks.register("continuousIntegration") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(
        testing.suites.named("semanticTest"),
        testing.suites.named("integrationTest"),
        testing.suites.named("endToEndTest"),
    )
}
