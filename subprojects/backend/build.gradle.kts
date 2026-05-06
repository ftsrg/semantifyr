/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":compiler"))

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport)

    testFixturesApi(testFixtures(project(":compiler")))
}
