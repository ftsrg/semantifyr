/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
}

/*
 * Simple Build service that acts as a lock or throttling device preventing parallel execution
 */
abstract class VerificationTestService : BuildService<BuildServiceParameters.None>

val verificationTestServiceName = "verificationTestService"
val verificationTestServiceProvider = gradle.sharedServices.registerIfAbsent(verificationTestServiceName, VerificationTestService::class) {
    // only allow at most 1 verification task per Gradle build
    maxParallelUsages.set(1)
}

val libs = the<LibrariesForLibs>()

testing {
    suites {
        val verificationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation(testFixtures(project()))

                runtimeOnly(libs.slf4j.log4j)
            }

            targets.all {
                testTask.configure {
                    group = "verification"
                    usesService(verificationTestServiceProvider)
                    failFast = true
                    maxParallelForks = 1

                    minHeapSize = "512m"
                    maxHeapSize = "4G"

                    shouldRunAfter(suites.named("test"))
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("verificationTest"))
}
