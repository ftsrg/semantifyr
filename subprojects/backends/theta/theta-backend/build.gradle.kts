/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.backend")
    id("hu.bme.mit.semantifyr.theta")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":cex.lang"))
    api(project(":xsts.lang"))
    api(project(":theta-executor"))

    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
}
