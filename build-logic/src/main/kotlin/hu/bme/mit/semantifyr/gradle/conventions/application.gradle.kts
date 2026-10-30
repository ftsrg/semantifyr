/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    application
}

val libs = the<LibrariesForLibs>()

dependencies {
    runtimeOnly(libs.slf4j.log4j)
}

configurations.consumable("distributionElements") {
    attributes(semantifyrArtifact("application-distribution"))
    outgoing.artifact(layout.buildDirectory.dir("install")) {
        builtBy(tasks.installDist)
    }
}
