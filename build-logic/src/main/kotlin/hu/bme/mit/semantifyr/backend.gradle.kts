/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

plugins {
    id("hu.bme.mit.semantifyr.suites")
}

dependencies {
    testImplementation(testFixtures(project(":backend")))
    testImplementation(testFixtures(project(":oxsts.lang")))
}

tasks.withType<Test>().configureEach {
    inputs.dir(isolated.rootProject.projectDirectory.dir("oxsts-test-models")).withPathSensitivity(PathSensitivity.RELATIVE)
}
