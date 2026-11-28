/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import hu.bme.mit.semantifyr.gradle.readsTestModels

plugins {
    id("hu.bme.mit.semantifyr.suites")
}

dependencies {
    testImplementation(testFixtures(project(":backend")))
    testImplementation(testFixtures(project(":oxsts.lang")))
}

val semanticModels = layout.projectDirectory.asFile.toPath().relativize(isolated.rootProject.projectDirectory.dir("oxsts-test-models/semantic").asFile.toPath()).joinToString("/")

tasks.withType<Test>().configureEach {
    readsTestModels()
    systemProperty("semantifyr.semanticModels", semanticModels)
}
