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
    api(project(":backend"))
    api(project(":compiler"))
    api(project(":verifier"))
    api(libs.clikt)
    api(libs.clikt.core)
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)

    implementation(project(":logging"))
    implementation(project(":portfolios"))
    implementation(project(":theta-executor"))
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.slf4j.api)

    testFixturesApi(project(":backend"))
    testFixturesApi(project(":verifier"))
    testFixturesApi(testFixtures(project(":verifier")))
    testFixturesApi(libs.junit.api)
    testFixturesApi(libs.kotlinx.serialization.json)
    testFixturesImplementation(project(":theta-executor"))
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.kotlinx.serialization.core)

    testImplementation(libs.mordant.core)
}
