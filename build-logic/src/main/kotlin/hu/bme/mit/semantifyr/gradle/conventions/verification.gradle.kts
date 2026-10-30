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

val jacocoVerificationTestReport = tasks.register<JacocoReport>("jacocoVerificationTestReport") {
    executionData.from(tasks.named<Test>("verificationTest").map { it.the<JacocoTaskExtension>().destinationFile!! })
}

testing {
    suites {
        register<JvmTestSuite>("verificationTest") {
            useJUnitJupiter(libs.versions.junit)

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

tasks.named("check") {
    dependsOn(testing.suites.named("verificationTest"))
}
