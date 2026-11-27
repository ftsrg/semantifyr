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
    api(project(":uppaal-executor"))

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":utils"))
}
