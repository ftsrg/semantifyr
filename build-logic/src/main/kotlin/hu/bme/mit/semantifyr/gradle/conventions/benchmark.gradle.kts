/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
}

val libs = the<LibrariesForLibs>()

val benchmark = sourceSets.create("benchmark")

dependencies {
    "benchmarkImplementation"(project())
    "benchmarkImplementation"(project(":benchmark"))
    "benchmarkImplementation"(project(":logging"))
    "benchmarkRuntimeOnly"(libs.slf4j.log4j)
}

tasks.named("check") {
    dependsOn(benchmark.classesTaskName)
}

val runexec = providers.gradleProperty("runexec")

tasks.register<JavaExec>("benchmark") {
    group = "benchmark"
    description = "Runs the benchmark class named by --args."
    classpath = benchmark.runtimeClasspath
    mainClass = "hu.bme.mit.semantifyr.benchmark.BenchmarkRunnerKt"
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("log4j2.configurationFile", "log4j2-benchmark.properties")
    systemProperty("benchmark.output", layout.buildDirectory.dir("benchmark").get().asFile.absolutePath)
    if (runexec.isPresent) {
        systemProperty("benchmark.runexec", runexec.get())
    }
    doNotTrackState("a benchmark always runs and its numbers never come from the build cache")
}

tasks.register<JavaExec>("benchmarkProgram") {
    group = "benchmark"
    description = "Runs the program named by the first of --args over the benchmark files named by the rest."
    classpath = benchmark.runtimeClasspath
    mainClass = "hu.bme.mit.semantifyr.benchmark.BenchmarkProgramKt"
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("log4j2.configurationFile", "log4j2-benchmark-program.properties")
    doNotTrackState("a program reads benchmark files that are no declared input")
}
