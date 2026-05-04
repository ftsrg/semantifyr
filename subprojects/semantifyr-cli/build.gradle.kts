/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":portfolios"))

    implementation(libs.clikt)
    implementation(libs.kotlinx.coroutines.core)

    runtimeOnly(libs.slf4j.log4j)
}

application {
    mainClass = "hu.bme.mit.semantifyr.cli.SemantifyrCliKt"
    applicationName = "semantifyr"
}
