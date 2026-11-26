/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.diffplug.spotless")
}

val libs = the<LibrariesForLibs>()

spotless {
    kotlinGradle {
        target(
            fileTree(".") {
                include("*.gradle.kts")
            },
        )
        ktlint(libs.versions.ktlint.get())
    }
}
