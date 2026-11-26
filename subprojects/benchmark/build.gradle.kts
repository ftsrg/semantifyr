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
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)

    implementation(project(":logging"))
    implementation(libs.slf4j.api)
}
