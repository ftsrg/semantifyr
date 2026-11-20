/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    base
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Builds the packages of this project."
}
