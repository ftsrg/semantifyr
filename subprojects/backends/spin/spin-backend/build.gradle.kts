/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.backend")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":backend"))
    api(project(":oxsts.lang"))
    api(project(":spin-executor"))
    api(libs.guice.extensions.assistedinject)

    implementation(project(":logging"))
    implementation(project(":utils"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":compiler"))

    testFixturesApi(testFixtures(project(":backend")))
}
