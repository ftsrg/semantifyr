/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr

import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.jvm")
}

val ideGenerated = configurations.dependencyScope("ideGenerated")
val ideGeneratedFiles = configurations.resolvable("ideGeneratedFiles") {
    extendsFrom(ideGenerated.get())
    attributes(semantifyrArtifact("xtext-ide-sources"))
}

val xtextGeneratedDirectory = layout.projectDirectory.dir("src/main/xtext-gen")

sourceSets {
    main {
        java.srcDir(xtextGeneratedDirectory)
        resources.srcDir(xtextGeneratedDirectory)
    }
}

tasks {
    val cloneIdeGenerated = register<Sync>("cloneIdeGenerated") {
        from(ideGeneratedFiles)
        into(xtextGeneratedDirectory)
    }

    compileJava {
        inputs.files(cloneIdeGenerated)
    }

    processResources {
        inputs.files(cloneIdeGenerated)
    }

    clean {
        delete(xtextGeneratedDirectory)
    }
}
