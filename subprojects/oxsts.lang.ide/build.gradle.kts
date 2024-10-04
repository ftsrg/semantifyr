/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.xtext-generated")
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
}

val distributionOutput by configurations.creating

artifacts {
    add(distributionOutput.name, tasks.distTar)
}

dependencies {
    api(project(":oxsts.lang"))

    implementation(libs.xtext.ide)
    runtimeOnly(libs.slf4j.log4j)
}
