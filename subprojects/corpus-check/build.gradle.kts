/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.cli")
    kotlin("jvm")
}

dependencies {
    implementation(project(":cli-common"))
    implementation(testFixtures(project(":verifier")))
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass = "hu.bme.mit.semantifyr.corpuscheck.CorpusCheckKt"
}
