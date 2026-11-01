/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.nightly")
}

val sysmlCompiledExamples = configurations.dependencyScope("sysmlCompiledExamples")
val sysmlCompiledExamplesFiles = configurations.resolvable("sysmlCompiledExamplesFiles") {
    extendsFrom(sysmlCompiledExamples.get())
    attributes(semantifyrArtifact("compiled-examples"))
}

dependencies {
    sysmlCompiledExamples(project(":sysml-wrapper"))
}

val syncSysmlCrossroads = tasks.register<Sync>("syncSysmlCrossroads") {
    from(sysmlCompiledExamplesFiles) {
        include("crossroads.oxsts")
    }
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/libraries/default")) {
        into("library")
    }
    into(layout.buildDirectory.dir("sysml-crossroads"))
}

tasks.named("nightlyTest") {
    inputs.files(syncSysmlCrossroads)
}
