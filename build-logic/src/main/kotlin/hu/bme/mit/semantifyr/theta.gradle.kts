/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import hu.bme.mit.semantifyr.gradle.runsWithTheta

plugins {
    id("hu.bme.mit.semantifyr.jvm")
}

tasks.withType<Test>().configureEach {
    runsWithTheta()
}
