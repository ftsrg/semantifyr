/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
    id("hu.bme.mit.semantifyr.gradle.conventions.integration")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":portfolios"))
    api(libs.kotlinx.serialization.json)

    implementation(project(":logging"))
    implementation(project(":sysml-frontend"))
    implementation(project(":sysml-wrapper"))
    implementation(testFixtures(project(":sysml-frontend")))
    implementation(libs.kotlinx.coroutines.core)

    runtimeOnly(libs.slf4j.log4j)

    testImplementation(testFixtures(project(":verifier")))
    testImplementation(libs.kotlinx.coroutines.core)
}
