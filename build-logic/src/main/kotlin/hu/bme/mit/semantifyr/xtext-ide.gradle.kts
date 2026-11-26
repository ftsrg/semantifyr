/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import hu.bme.mit.semantifyr.gradle.artifactsOf

plugins {
    id("hu.bme.mit.semantifyr.jvm")
}

val ideGeneratedFiles = artifactsOf("xtext-ide-sources", path.removeSuffix(".ide"))

val xtextGeneratedDirectory = layout.projectDirectory.dir("src/main/xtext-gen")

sourceSets {
    main {
        java.srcDir(xtextGeneratedDirectory)
        resources.srcDir(xtextGeneratedDirectory)
    }
}

val cloneIdeGenerated = tasks.register<Sync>("cloneIdeGenerated") {
    from(ideGeneratedFiles)
    into(xtextGeneratedDirectory)
}

tasks.compileJava {
    inputs.files(cloneIdeGenerated)
}

tasks.processResources {
    inputs.files(cloneIdeGenerated)
}

tasks.clean {
    delete(xtextGeneratedDirectory)
}
