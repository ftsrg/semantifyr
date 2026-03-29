/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    kotlin("jvm")
    kotlin("plugin.serialization")
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

    testImplementation(libs.kotlinx.serialization.json)

    cliClasspath(project(":sysmlv2-frontend", configuration = "cliOutput"))
}

val prepareCli by tasks.registering(Sync::class) {
    from(cliClasspath)
    into("build/cli")
}

tasks.withType(Test::class) {
    dependsOn(prepareCli)
}

//val benchmarkVerificationCases by tasks.registering(Test::class) {
//    group = "verification"
//    description = "Run each verification case N times and aggregate timing. Pass -Pbenchmark.runs=N to override (default 3)."
//
//    dependsOn(prepareCli)
//
//    useJUnitPlatform {
//        includeTags("benchmark")
//    }
//
//    testClassesDirs = sourceSets.test.get().output.classesDirs
//    classpath = sourceSets.test.get().runtimeClasspath
//
//    minHeapSize = "512m"
//    maxHeapSize = "4G"
//    maxParallelForks = 1
//    testLogging.showStandardStreams = true
//    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
//
//    val thetaCliPath = project(":theta-wrapper").layout.buildDirectory.dir("theta-xsts-cli").get().asFile.absolutePath
//    val existingPath = environment["PATH"] ?: System.getenv("PATH") ?: ""
//    environment("PATH", "$thetaCliPath${File.pathSeparator}$existingPath")
//
//    val benchmarkRuns = providers.gradleProperty("benchmark.runs").orElse("5")
//    systemProperty("benchmark.runs", benchmarkRuns.get())
//}
