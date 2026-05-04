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

val cloneOxstsTestModels by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("oxsts-test-models"))
    into(layout.buildDirectory.dir("test-models"))
}

testing {
    suites {
        val conformanceTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation(testFixtures(project()))

                implementation(testFixtures(project(":verification")))
                implementation(project(":portfolios"))

                runtimeOnly(libs.slf4j.log4j)
            }

            targets.all {
                testTask.configure {
                    group = "verification"
                    usesService(verificationTestServiceProvider)

                    maxParallelForks = 1

                    inputs.files(cloneOxstsTestModels)
                    workingDir = layout.projectDirectory.asFile

                    shouldRunAfter(suites.named("test"))

                    finalizedBy(tasks.named("jacocoConformanceTestReport"))
                }
            }
        }
    }
}

val jacocoConformanceTestReport by tasks.registering(JacocoReport::class) {
    val conformanceTestSuite = testing.suites.named("conformanceTest", JvmTestSuite::class)
    inputs.files(
        conformanceTestSuite.flatMap {
            it.targets.first().testTask.flatMap { it.outputs.files.elements }
        },
    )
    executionData(layout.buildDirectory.file("jacoco/conformanceTest.exec"))
    sourceSets(sourceSets.main.get())
    reports {
        xml.required.set(true)
    }
}
