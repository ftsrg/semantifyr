/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
    maven("https://repo.eclipse.org/content/groups/viatra/")
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(distributionOutput.name, layout.buildDirectory.dir("install")) {
        builtBy(tasks.installDist)
    }
}

dependencies {
    implementation(project(":semantics"))
    implementation(project(":theta"))

    runtimeOnly(libs.slf4j.log4j)
    implementation(libs.clikt)
}

application {
    mainClass = "hu.bme.mit.semantifyr.cli.SemantifyrCliKt"
}
