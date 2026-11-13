/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.nightly")
}

// the timed case is one of crossroads' further cases, so the model is compiled with them
val sysmlCompiledCorpus = configurations.dependencyScope("sysmlCompiledCorpus")
val sysmlCompiledCorpusFiles = configurations.resolvable("sysmlCompiledCorpusFiles") {
    extendsFrom(sysmlCompiledCorpus.get())
    attributes(semantifyrArtifact("compiled-corpus"))
}

dependencies {
    sysmlCompiledCorpus(project(":sysml-wrapper"))
}

val syncSysmlCrossroads = tasks.register<Sync>("syncSysmlCrossroads") {
    from(sysmlCompiledCorpusFiles) {
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
