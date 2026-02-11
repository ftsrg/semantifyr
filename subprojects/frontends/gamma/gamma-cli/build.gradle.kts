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

dependencies {
    implementation(project(":gamma-semantics"))

    implementation(libs.clikt)

    runtimeOnly(libs.slf4j.log4j)
}

application {
    mainClass = "hu.bme.mit.semantifyr.frontends.gamma.cli.GammaFrontendCliKt"
}
