/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.cli")
}

val ideGeneratedFiles = artifactsOf("xtext-ide-sources", path.removeSuffix(".ide"))

val xtextGeneratedDirectory = layout.projectDirectory.dir("src/main/xtext-gen")

val cloneIdeGenerated = tasks.register<Sync>("cloneIdeGenerated") {
    from(ideGeneratedFiles)
    into(xtextGeneratedDirectory)
}

sourceSets {
    main {
        java.srcDir(cloneIdeGenerated)
        resources.srcDir(cloneIdeGenerated)
    }
}

tasks.clean {
    delete(xtextGeneratedDirectory)
}

configurations.consumable("applicationDistributionElements") {
    attributes(semantifyrArtifact("application-distribution"))
    outgoing.artifact(layout.buildDirectory.dir("install")) {
        builtBy(tasks.installDist)
    }
}
