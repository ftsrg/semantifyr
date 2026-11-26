/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hu.bme.mit.semantifyr.jvm")
}

val libs = the<LibrariesForLibs>()

val benchmark = sourceSets.create("benchmark")

java {
    registerFeature("benchmark") {
        usingSourceSet(benchmark)
    }
}

dependencies {
    add(benchmark.apiConfigurationName, project())
    add(benchmark.apiConfigurationName, project(":benchmark"))
    add(benchmark.runtimeOnlyConfigurationName, libs.slf4j.log4j)
}

tasks.named("check") {
    dependsOn(benchmark.classesTaskName)
}

val runexec = providers.gradleProperty("runexec")

tasks.register<JavaExec>("benchmark") {
    group = "benchmark"
    description = "Runs a benchmark and its analysis."
    classpath = benchmark.runtimeClasspath
    mainClass = "hu.bme.mit.semantifyr.benchmark.BenchmarkRunnerKt"
    systemProperty("log4j2.configurationFile", "log4j2-benchmark.properties")
    systemProperty("benchmark.output", layout.buildDirectory.dir("benchmark").get().asFile.absolutePath)
    if (runexec.isPresent) {
        systemProperty("benchmark.runexec", runexec.get())
    }
    doNotTrackState("a benchmark always runs and its numbers never come from the build cache")
}
