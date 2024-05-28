/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    kotlin("jvm") version "1.9.10"
}

kotlin {
    jvmToolchain(17)
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

tasks.withType(Test::class.java) {
    inputs.dir("Test Models")

    dependsOn(downloadTheta)
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
