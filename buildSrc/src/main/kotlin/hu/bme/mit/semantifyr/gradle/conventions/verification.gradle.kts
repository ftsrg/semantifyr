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

val verificationTestServiceProvider = gradle.sharedServices.registerVerificationTestService()

val libs = the<LibrariesForLibs>()

testing {
    suites {
        val verificationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation(testFixtures(project()))

                implementation(testFixtures(project(":verifier")))

                runtimeOnly(libs.slf4j.log4j)
            }

            targets.all {
                testTask.configure {
                    usesService(verificationTestServiceProvider)

                    shouldRunAfter(suites.named("test"))

                    finalizedBy(jacocoVerificationTestReport)
                }
            }
        }
    }
}

val jacocoVerificationTestReport by tasks.registering(JacocoReport::class) {
    val verificationTestSuite = tasks.named<Test>("verificationTest").get()
    inputs.files(verificationTestSuite)
    executionData(verificationTestSuite)
}

tasks.named("check") {
    dependsOn(testing.suites.named("verificationTest"))
}
