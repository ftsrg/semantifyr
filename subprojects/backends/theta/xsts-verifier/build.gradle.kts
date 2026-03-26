/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":semantics"))
    api(project(":xsts.lang"))
    api(project(":theta-wrapper"))

    implementation(libs.kotlinx.serialization.json)

    testRuntimeOnly(libs.slf4j.log4j)

    testFixturesApi(project(":semantics"))
    testFixturesApi(testFixtures(project(":semantics")))
}
