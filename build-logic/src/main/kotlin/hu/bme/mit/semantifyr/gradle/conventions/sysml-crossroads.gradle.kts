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
val nightlyTestSysmlCompiledCorpus = configurations.dependencyScope("nightlyTestSysmlCompiledCorpus")
val nightlyTestSysmlCompiledCorpusFiles = configurations.resolvable("nightlyTestSysmlCompiledCorpusFiles") {
    extendsFrom(nightlyTestSysmlCompiledCorpus.get())
    attributes(semantifyrArtifact("compiled-corpus"))
}

dependencies {
    nightlyTestSysmlCompiledCorpus(project(":sysml-wrapper"))
}

val syncSysmlCrossroads = tasks.register<Sync>("syncSysmlCrossroads") {
    from(nightlyTestSysmlCompiledCorpusFiles) {
        include("crossroads.$oxstsFileExtension")
    }
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/libraries/default")) {
        into("library")
    }
    into(layout.buildDirectory.dir("sysml-crossroads"))
}

tasks.named("nightlyTest") {
    inputs.files(syncSysmlCrossroads)
}
