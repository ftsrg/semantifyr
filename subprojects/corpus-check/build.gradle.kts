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

    testImplementation(libs.mordant.core)
}

application {
    mainClass = "hu.bme.mit.semantifyr.corpuscheck.CorpusCheckKt"
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(tasks.distZip)
}
