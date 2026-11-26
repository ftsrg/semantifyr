/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.backend")
    kotlin("jvm")
}

dependencies {
    api(project(":backend"))
    api(project(":nuxmv-executor"))
    api(libs.guice.extensions.assistedinject)

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":utils"))

    testImplementation(testFixtures(project(":backend")))
}
