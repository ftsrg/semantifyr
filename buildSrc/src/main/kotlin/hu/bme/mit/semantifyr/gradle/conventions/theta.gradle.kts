/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
}

val thetaClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    thetaClasspath(project(":theta-executor", configuration = "thetaOutput"))
}

tasks.withType<Test>().configureEach {
    inputs.files(thetaClasspath)

    val thetaCliDir = project(":theta-executor").layout.buildDirectory.dir("theta-xsts-cli").get().asFile
    val existingPath = environment["PATH"] ?: System.getenv("PATH") ?: ""

    environment("PATH", "${thetaCliDir.absolutePath}${File.pathSeparator}$existingPath")
}
