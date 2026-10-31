/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":portfolios"))
    api(libs.kotlinx.serialization.json)

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)

    runtimeOnly(libs.slf4j.log4j)

    testImplementation(testFixtures(project(":verifier")))
    testImplementation(libs.kotlinx.coroutines.core)
}
