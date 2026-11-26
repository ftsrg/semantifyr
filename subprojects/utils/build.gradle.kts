/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    kotlin("jvm")
}

dependencies {
    implementation(project(":logging"))
    implementation(libs.slf4j.api)
}
