/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
}

// Theta on the tests' path; a module that ships Theta takes it through a scope of its own
val testTheta = configurations.dependencyScope("testTheta")
val testThetaFiles = configurations.resolvable("testThetaFiles") {
    extendsFrom(testTheta.get())
    attributes(semantifyrArtifact("theta-xsts-cli"))
}

dependencies {
    testTheta(project(":theta-executor"))
}

val cloneTheta = tasks.register<Sync>("cloneTheta") {
    from(testThetaFiles)
    into(layout.buildDirectory.dir("theta-xsts-cli"))
}

tasks.withType<Test>().configureEach {
    inputs.files(cloneTheta)

    val thetaCliDir = cloneTheta.map { it.destinationDir.absolutePath }

    doFirst {
        environment("PATH", "${thetaCliDir.get()}${File.pathSeparator}${System.getenv("PATH")}")
    }
}
