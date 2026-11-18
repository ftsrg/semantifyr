/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    application
}

val libs = the<LibrariesForLibs>()

dependencies {
    runtimeOnly(libs.slf4j.log4j)
}

application {
    // JNA (Mordant's terminal, docker-java's socket) loads native code, Guice's bytecode generation calls sun.misc.Unsafe; both warn on every run
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-Dguice_bytecode_gen_option=DISABLED")
}

val distributionArchives = setOf(
    tasks.distZip.name,
    tasks.distTar.name,
)

// the distribution plugin publishes the main distribution's archives, which assemble builds with every published artifact
tasks.assemble {
    setDependsOn(
        dependsOn.map { dependency ->
            if (dependency is TaskDependency) {
                TaskDependency { task ->
                    dependency.getDependencies(task).filterNot {
                        it.name in distributionArchives
                    }.toSet()
                }
            } else {
                dependency
            }
        },
    )
}

tasks.named("pack") {
    dependsOn(tasks.distZip, tasks.distTar)
}

configurations.consumable("distributionElements") {
    attributes(semantifyrArtifact("application-distribution"))
    outgoing.artifact(layout.buildDirectory.dir("install")) {
        builtBy(tasks.installDist)
    }
}
