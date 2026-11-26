/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

plugins {
    id("hu.bme.mit.semantifyr.suites")
}

tasks.withType<Test>().configureEach {
    inputs.dir(isolated.rootProject.projectDirectory.dir("oxsts-test-models")).withPathSensitivity(PathSensitivity.RELATIVE)
}
