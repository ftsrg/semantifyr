/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    kotlin("jvm")
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":portfolios"))

    implementation(libs.clikt)
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass = "hu.bme.mit.semantifyr.cli.SemantifyrCliKt"
    applicationName = "semantifyr"
}
