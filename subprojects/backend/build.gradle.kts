/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":compiler"))
    api(libs.docker.java.api)
    api(libs.slf4j.api)

    implementation(project(":utils"))
    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport)
    implementation(libs.docker.java.transport.httpclient5)
    implementation(libs.commons.io)

    testFixturesApi(libs.xtext.testing)

    testImplementation(testFixtures(project(":oxsts.lang")))
    testImplementation(libs.kotlinx.coroutines.test)
}
