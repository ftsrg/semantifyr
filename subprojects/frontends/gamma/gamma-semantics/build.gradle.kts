/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

dependencies {
    api(project(":gamma.lang"))

    testFixturesApi(project(":gamma.lang"))
    testFixturesApi(testFixtures(project(":gamma.lang")))
}
