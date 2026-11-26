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
    implementation(project(":backend"))
    implementation(project(":cli-common"))
    implementation(testFixtures(project(":verifier")))
    implementation(libs.clikt)
    implementation(libs.clikt.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.mordant.core)
}

application {
    mainClass = "hu.bme.mit.semantifyr.corpuscheck.CorpusCheckKt"
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(tasks.distZip)
}
