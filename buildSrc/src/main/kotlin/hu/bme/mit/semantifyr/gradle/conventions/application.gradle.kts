/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    application
}

tasks.startScripts {
    classpath = files("%APP_HOME%/lib/*")
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(distributionOutput.name, layout.buildDirectory.dir("install")) {
        builtBy(tasks.installDist)
    }
}
