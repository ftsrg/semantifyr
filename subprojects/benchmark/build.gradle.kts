/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
