/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    `kotlin-dsl`
}

dependencies {
    // https://github.com/gradle/gradle/issues/15383
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(libs.node.plugin)
    implementation(libs.spotless.plugin)
    implementation(libs.sonarqube.plugin)
}
