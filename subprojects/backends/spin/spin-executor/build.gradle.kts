/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":backend"))
    api(libs.guice)

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
}
