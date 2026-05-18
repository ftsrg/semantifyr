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

val cloneTheta by tasks.registering(Sync::class) {
    from(thetaClasspath)
    into(layout.buildDirectory.dir("theta-xsts-cli"))
}

tasks.withType<Test>().configureEach {
    inputs.files(cloneTheta)

    val thetaCliDir = cloneTheta.map { it.destinationDir.absolutePath }

    doFirst {
        environment("PATH", "${thetaCliDir.get()}${File.pathSeparator}${System.getenv("PATH")}")
    }
}
