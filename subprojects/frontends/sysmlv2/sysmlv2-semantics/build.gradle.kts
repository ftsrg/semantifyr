/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    testRuntimeOnly(libs.slf4j.log4j)

    testFixturesApi(project(":xsts-verifier"))
    testFixturesApi(testFixtures(project(":xsts-verifier")))
}
