/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

plugins {
    id("hu.bme.mit.semantifyr.suites")
}

val cloneOxstsTestModels = tasks.register<Sync>("cloneOxstsTestModels") {
    from(isolated.rootProject.projectDirectory.dir("oxsts-test-models"))
    into(layout.buildDirectory.dir("test-models"))
}

tasks.withType<Test>().configureEach {
    inputs.files(cloneOxstsTestModels)
}
