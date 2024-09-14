/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.application")
    alias(libs.plugins.kotlin.jvm)
}

tasks.withType(Test::class.java) {
    inputs.dir("TestModels")
}

repositories {
    mavenCentral()
    maven("https://repo.eclipse.org/content/groups/viatra/")
}

dependencies {
    implementation(project(":oxsts.lang"))
    implementation(project(":oxsts.model"))

    implementation("com.google.inject:guice:7.0.0")
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.cli)
    implementation(libs.ecore.codegen)
    implementation(libs.viatra.query.language) {
        exclude("com.google.inject", "guice")
    }

    runtimeOnly(libs.viatra.query.runtime)
    runtimeOnly(libs.viatra.transformation.runtime)
    runtimeOnly(libs.slf4j.simple)
    runtimeOnly(libs.slf4j.log4j)

    testFixturesApi("commons-io:commons-io:2.14.0")
    testFixturesApi(project(":oxsts.lang"))
    testFixturesApi(testFixtures(project(":oxsts.lang")))
}
