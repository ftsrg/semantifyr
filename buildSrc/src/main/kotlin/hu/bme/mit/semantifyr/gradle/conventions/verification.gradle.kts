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
        val verificationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation(testFixtures(project()))

                implementation(testFixtures(project(":verification")))

                runtimeOnly(libs.slf4j.log4j)
            }

            targets.all {
                testTask.configure {
                    group = "verification"
                    usesService(verificationTestServiceProvider)

                    maxParallelForks = 1

                    useJUnitPlatform {
                        excludeTags("slow")
                    }

                    shouldRunAfter(suites.named("test"))

                    finalizedBy(tasks.named("jacocoVerificationTestReport"))
                }
            }
        }
    }
}

val jacocoVerificationTestReport by tasks.registering(JacocoReport::class) {
    val verificationTestSuite = testing.suites.named("verificationTest", JvmTestSuite::class)
    inputs.files(
        verificationTestSuite.flatMap {
            it.targets.first().testTask.flatMap { it.outputs.files.elements }
        },
    )
    executionData(layout.buildDirectory.file("jacoco/verificationTest.exec"))
    sourceSets(sourceSets.main.get())
    reports {
        xml.required.set(true)
    }
}

val slowVerificationTest by tasks.registering(Test::class) {
    description = "Runs the verification cases tagged with @Tag(\"slow\"). Sequenced with other verification tasks."
    group = "verification"
    usesService(verificationTestServiceProvider)

    val verificationTestSuite = testing.suites.named("verificationTest", JvmTestSuite::class)
    val target = verificationTestSuite.get().targets.first()
    testClassesDirs = target.testTask.get().testClassesDirs
    classpath = target.testTask.get().classpath

    maxParallelForks = 1

    useJUnitPlatform {
        includeTags("slow")
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("verificationTest"))
}
