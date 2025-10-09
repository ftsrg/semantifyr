/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    alias(libs.plugins.kotlin.jvm)
}

tasks.withType(Test::class.java) {
    inputs.dir("TestModels")

    if (environment["thetaVersion"] == null) {
        environment("thetaVersion", findProperty("thetaVersion")!!)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.eclipse.org/content/groups/viatra/")
}

dependencies {
    api(project(":oxsts.lang"))

    api(libs.guice)
//    implementation(libs.kotlinx.coroutines.core)
//    implementation(libs.viatra.query.language) {
//        exclude("com.google.inject", "guice")
//    }

//    runtimeOnly(libs.viatra.query.runtime)
//    runtimeOnly(libs.viatra.transformation.runtime)

    testFixturesApi(libs.slf4j.api)
    testFixturesApi(project(":oxsts.lang"))
    testFixturesApi(testFixtures(project(":oxsts.lang")))
}
