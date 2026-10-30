/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.theta")
    id("hu.bme.mit.semantifyr.gradle.conventions.verification")
    id("hu.bme.mit.semantifyr.gradle.conventions.integration")
    id("hu.bme.mit.semantifyr.gradle.conventions.nightly")
}

val cloneOxstsTestModels = tasks.register<Sync>("cloneOxstsTestModels") {
    from(rootProject.layout.projectDirectory.dir("oxsts-test-models"))
    into(layout.buildDirectory.dir("test-models"))
}

tasks.named("verificationTest") {
    inputs.files(cloneOxstsTestModels)
}

val integrationTest = tasks.named("integrationTest") {
    inputs.files(cloneOxstsTestModels)
}

tasks.named("nightlyTest") {
    inputs.files(cloneOxstsTestModels)
}

tasks.register("backendIntegrationTest") {
    group = "verification"
    inputs.files(integrationTest)
}
