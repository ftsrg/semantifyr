/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":semantics"))
    api(project(":xsts.lang"))
    api(project(":theta-wrapper"))

    testRuntimeOnly(libs.slf4j.log4j)

    testFixturesApi(project(":semantics"))
    testFixturesApi(testFixtures(project(":semantics")))
}
