/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.jvm")
}

val theta = configurations.dependencyScope("theta")
val thetaFiles = configurations.resolvable("thetaFiles") {
    extendsFrom(theta.get())
    attributes(semantifyrArtifact("theta-xsts-cli"))
}

dependencies {
    theta(project(":theta-executor"))
}

val thetaDirectory = files(thetaFiles)

fun <T> T.runsWithTheta() where T : Task, T : ProcessForkOptions {
    inputs.files(thetaDirectory)
    val directory = thetaDirectory
    doFirst {
        environment("PATH", "${directory.singleFile.absolutePath}${File.pathSeparator}${System.getenv("PATH")}")
    }
}

tasks.withType<Test>().configureEach {
    runsWithTheta()
}

tasks.withType<JavaExec>().configureEach {
    runsWithTheta()
}
