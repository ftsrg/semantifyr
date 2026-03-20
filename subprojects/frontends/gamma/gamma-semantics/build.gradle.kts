/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":gamma.lang"))

    testRuntimeOnly(libs.slf4j.log4j)

    testFixturesApi(project(":gamma.lang"))
    testFixturesApi(testFixtures(project(":gamma.lang")))

    testFixturesApi(project(":xsts-verifier"))
    testFixturesApi(testFixtures(project(":xsts-verifier")))
}
