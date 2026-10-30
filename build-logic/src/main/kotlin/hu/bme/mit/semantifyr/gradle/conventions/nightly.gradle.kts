/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.integration")
}

val verificationTestServiceProvider = gradle.sharedServices.registerVerificationTestService()

val libs = the<LibrariesForLibs>()

val jacocoNightlyTestReport = tasks.register<JacocoReport>("jacocoNightlyTestReport") {
    executionData.from(tasks.named<Test>("nightlyTest").map { it.the<JacocoTaskExtension>().destinationFile!! })
}

testing {
    suites {
        register<JvmTestSuite>("nightlyTest") {
            useJUnitJupiter(libs.versions.junit)

            dependencies {
                implementation(project())
                implementation(testFixtures(project()))

                implementation(testFixtures(project(":verifier")))
                implementation(project(":portfolios"))

                runtimeOnly(libs.slf4j.log4j)
            }

            targets.all {
                testTask.configure {
                    usesService(verificationTestServiceProvider)

                    shouldRunAfter(suites.named("integrationTest"))

                    finalizedBy(jacocoNightlyTestReport)
                }
            }
        }
    }
}
