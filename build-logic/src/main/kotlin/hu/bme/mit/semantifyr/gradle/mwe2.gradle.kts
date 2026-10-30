/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import hu.bme.mit.semantifyr.gradle.conventions.javaRuntimeLibrary
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
}

val mwe2 = configurations.dependencyScope("mwe2")
configurations.resolvable("mwe2Classpath") {
    extendsFrom(configurations.implementation.get(), mwe2.get())
    attributes(javaRuntimeLibrary())
}

val libs = the<LibrariesForLibs>()

dependencies {
    mwe2(libs.mwe2.launch)
}
