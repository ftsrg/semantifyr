/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    alias(libs.plugins.kotlin.jvm)
}

val downloadTheta = tasks.create<Exec>("downloadTheta") {
    inputs.files("scripts/Get-Theta.ps1")
    inputs.files("scripts/get-theta.sh")
    outputs.dir("theta")

    workingDir = File("theta")
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        commandLine("powershell", "../scripts/Get-Theta.ps1")
    } else {
        commandLine("sh", "../scripts/get-theta.sh")
    }
}

fun Test.addToVariable(key: String, value: String) {
    val pathSeparator = File.pathSeparator
    val old = environment[key]?.toString() ?: ""
    val newValue = if (old.isBlank()) value else "$old$pathSeparator$value"
    environment(key, newValue)
}

tasks.withType(Test::class.java) {
    inputs.dir("Test Models")
    inputs.files(downloadTheta.outputs)

    val thetaDir = layout.projectDirectory.dir("theta").toString()

    addToVariable("LD_LIBRARY_PATH", thetaDir)
    addToVariable("PATH", thetaDir)
}

repositories {
    mavenCentral()
    maven("https://repo.eclipse.org/content/groups/viatra/")
}

dependencies {
    implementation(project(":oxsts.lang"))
    implementation(project(":oxsts.model"))

    implementation("com.google.inject:guice:7.0.0")
    implementation(libs.kotlinx.cli)
    implementation(libs.ecore.codegen)
    implementation(libs.viatra.query.language) {
        exclude("com.google.inject", "guice")
    }
    implementation(libs.viatra.query.runtime)
    implementation(libs.viatra.transformation.runtime)

    testFixturesApi("commons-io:commons-io:2.14.0")
    testFixturesApi(project(":oxsts.lang"))
    testFixturesApi(testFixtures(project(":oxsts.lang")))
}
