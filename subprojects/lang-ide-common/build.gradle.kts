/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.jvm")
}

dependencies {
    api(libs.xtext.ide)
    api(libs.lsp4j)
    api(libs.lsp4j.jsonrpc)
    api(libs.guice)
    api(libs.ecore)
    api(libs.emf.common)
    api(libs.gson)
    api(libs.kotlin.stdlib)
    api(libs.xtext.core)
    api(libs.xtext.util)
    api(libs.xtext.xbase.lib)

    api(project(":verifier"))
    api(project(":backend"))
    api(project(":compiler"))

    implementation(project(":portfolios"))
    implementation(project(":theta-executor"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)

    testImplementation(testFixtures(project(":verifier")))
}
