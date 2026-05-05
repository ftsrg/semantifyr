/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.xtext.ide)
    api(libs.lsp4j)
    api(libs.lsp4j.jsonrpc)
    api(libs.guice)

    api(project(":verification"))
    api(project(":backend"))
    api(project(":compiler"))
    api(project(":portfolios"))
    api(project(":theta-backend"))

    implementation(project(":logging"))
}
