/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.suites")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(libs.guice.extensions.assistedinject)
    api(libs.kotlinx.serialization.core)

    api(project(":guice-common"))
    api(project(":oxsts.lang"))

    implementation(project(":logging"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":oxsts.lang")))
}
