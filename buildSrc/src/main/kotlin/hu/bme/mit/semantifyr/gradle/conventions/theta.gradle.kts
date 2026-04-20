/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
}

val thetaClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    thetaClasspath(project(":theta-executor", configuration = "thetaOutput"))
}

testing {
    suites {
        val verificationTest by getting(JvmTestSuite::class) {
            targets.all {
                testTask.configure {
                    inputs.files(thetaClasspath)

                    val thetaCliDir = project(":theta-executor").layout.buildDirectory.dir("theta-xsts-cli").get().asFile
                    val existingPath = environment["PATH"] ?: System.getenv("PATH") ?: ""

                    environment("PATH", "${thetaCliDir.absolutePath}${File.pathSeparator}$existingPath")
                }
            }
        }
    }
}
