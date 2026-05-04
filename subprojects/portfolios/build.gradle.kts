/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":logging"))

    api(project(":verifier"))
    api(project(":theta-backend"))
    api(project(":uppaal-backend"))
    api(project(":nuxmv-backend"))
    api(project(":spin-backend"))
}
