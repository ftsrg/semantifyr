/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    kotlin("jvm")
}

val cliClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

repositories {
    mavenCentral()
}

dependencies {
    testRuntimeOnly(libs.slf4j.log4j)

    testFixturesApi(project(":xsts-verifier"))
    testFixturesApi(testFixtures(project(":xsts-verifier")))

    cliClasspath(project(":sysmlv2-frontend", configuration = "cliOutput"))
}

val prepareCli by tasks.registering(Sync::class) {
    from(cliClasspath)
    into("build/cli")
}

tasks.withType(Test::class) {
    dependsOn(prepareCli)
}
