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
    api(project(":guice-common"))
    api(project(":oxsts.lang"))
    api(project(":oxsts.model"))
    api(libs.docker.java.api)
    api(libs.ecore)
    api(libs.guice)
    api(libs.kotlinx.serialization.core)
    api(libs.slf4j.api)

    implementation(project(":utils"))
    implementation(project(":logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport)
    implementation(libs.docker.java.transport.httpclient5)
    implementation(libs.commons.io)
    implementation(libs.emf.common)
    implementation(libs.xtext.core)

    testFixturesApi(project(":compiler"))
    testFixturesApi(project(":oxsts.model"))
    testFixturesApi(libs.guice)
    testFixturesApi(libs.xtext.core)
    testFixturesApi(libs.xtext.testing)
    testFixturesImplementation(libs.ecore)
    testFixturesImplementation(libs.emf.common)

    testImplementation(testFixtures(project(":oxsts.lang")))
    testImplementation(libs.kotlinx.coroutines.test)
}
