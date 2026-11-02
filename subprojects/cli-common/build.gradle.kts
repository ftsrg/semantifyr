/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    kotlin("jvm")
}

dependencies {
    api(project(":portfolios"))
    api(libs.clikt)

    implementation(project(":logging"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.log4j.core)

    testFixturesApi(testFixtures(project(":verifier")))
    testFixturesApi(libs.kotlinx.serialization.json)
}
