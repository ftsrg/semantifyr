/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs

class BenchmarkSourceSetsArgumentProvider(
    @get:Classpath val sourceSets: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        return listOf("-Dbenchmark.sourceSets=${sourceSets.asPath}")
    }
}

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
}

val libs = the<LibrariesForLibs>()

val benchmark = sourceSets.create("benchmark")

// another module's benchmarks combine this module's blocks
java {
    registerFeature("benchmark") {
        usingSourceSet(benchmark)
        disablePublication()
    }
}

dependencies {
    "benchmarkApi"(project())
    "benchmarkApi"(project(":benchmark"))
    "benchmarkRuntimeOnly"(libs.slf4j.log4j)
}

tasks.named("check") {
    dependsOn(benchmark.classesTaskName)
}

val runexec = providers.gradleProperty("runexec")

// a module's benchmark feature arrives with the capability that registerFeature names after the module
val benchmarkFeatures = configurations.named(benchmark.runtimeClasspathConfigurationName).flatMap { configuration ->
    configuration.incoming.artifacts.resolvedArtifacts.map { artifacts ->
        artifacts.filter { artifact ->
            val owner = artifact.variant.owner
            owner is ProjectComponentIdentifier && artifact.variant.capabilities.any {
                it.name == "${owner.projectName}-benchmark"
            }
        }.map {
            it.file
        }
    }
}

tasks.register<JavaExec>("benchmark") {
    group = "benchmark"
    description = "Runs the benchmark class named by the first of --args and its analysis, or only its analysis over the files named by the rest."
    classpath = benchmark.runtimeClasspath
    mainClass = "hu.bme.mit.semantifyr.benchmark.BenchmarkRunnerKt"
    workingDir = layout.projectDirectory.asFile
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    jvmArgumentProviders.add(BenchmarkSourceSetsArgumentProvider(files(benchmark.output.classesDirs, benchmarkFeatures)))
    systemProperty("log4j2.configurationFile", "log4j2-benchmark.properties")
    systemProperty("benchmark.output", layout.buildDirectory.dir("benchmark").get().asFile.absolutePath)
    if (runexec.isPresent) {
        systemProperty("benchmark.runexec", runexec.get())
    }
    doNotTrackState("a benchmark always runs and its numbers never come from the build cache")
}
