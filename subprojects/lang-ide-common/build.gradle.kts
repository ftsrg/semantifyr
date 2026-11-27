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
    api(libs.gson)
    api(libs.kotlin.stdlib)
    api(libs.xtext.xbase.lib)

    api(project(":verifier"))

    implementation(project(":portfolios"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":verifier")))
}
