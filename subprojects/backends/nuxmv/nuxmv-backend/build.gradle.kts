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
    api(project(":nuxmv-executor"))
    api(libs.guice.extensions.assistedinject)

    implementation(project(":logging"))
    implementation(project(":utils"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":backend")))
}
