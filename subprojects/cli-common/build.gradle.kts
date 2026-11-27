/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":verifier"))
    api(libs.clikt)
    api(libs.clikt.core)
    api(libs.kotlinx.serialization.json)

    implementation(project(":logging"))
    implementation(project(":portfolios"))
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)

    testFixturesApi(testFixtures(project(":verifier")))
    testFixturesApi(libs.junit.api)
    testFixturesImplementation(project(":theta-executor"))
    testFixturesImplementation(libs.assertj.core)

    testImplementation(libs.mordant.core)
}
