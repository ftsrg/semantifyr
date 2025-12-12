/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":cex.lang"))
    api(libs.guice)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport)
}
