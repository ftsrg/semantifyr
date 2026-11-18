/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.adapter")
    id("hu.bme.mit.semantifyr.gradle.conventions.semantic")
    id("hu.bme.mit.semantifyr.gradle.conventions.integration")
    id("hu.bme.mit.semantifyr.gradle.conventions.nightly")
}

val cloneOxstsTestModels = tasks.register<Sync>("cloneOxstsTestModels") {
    from(rootProject.layout.projectDirectory.dir("oxsts-test-models"))
    into(layout.buildDirectory.dir("test-models"))
}

tasks.withType<Test>().configureEach {
    inputs.files(cloneOxstsTestModels)
}
