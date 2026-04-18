/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":semantics"))
    api(project(":theta-backend"))
}
