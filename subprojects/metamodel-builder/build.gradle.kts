/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    kotlin("jvm")
}

dependencies {
    api(platform(libs.xtext.bom))
    api(libs.ecore)

    implementation(libs.ecore.codegen)
    implementation(libs.ecore.xmi)
    implementation(libs.emf.common)
    implementation(libs.mwe2.lib)
    implementation(libs.mwe2.runtime)

    runtimeOnly(libs.mwe.utils)
    runtimeOnly(libs.reload4j)
}
