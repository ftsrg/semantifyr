/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
}

val verificationTestServiceProvider = gradle.sharedServices.registerVerificationTestService()

val libs = the<LibrariesForLibs>()

testing {
    suites {
        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()

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

                    shouldRunAfter(suites.named("test"))

                    finalizedBy(jacocoIntegrationTestReport)
                }
            }
        }
    }
}

val jacocoIntegrationTestReport by tasks.registering(JacocoReport::class) {
    val integrationTestSuite = tasks.named<Test>("integrationTest").get()
    inputs.files(integrationTestSuite)
    executionData(integrationTestSuite)
}
