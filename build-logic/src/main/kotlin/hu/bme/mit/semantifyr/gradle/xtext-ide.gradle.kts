/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
}

val ideGenerated = configurations.dependencyScope("ideGenerated")
val ideGeneratedFiles = configurations.resolvable("ideGeneratedFiles") {
    extendsFrom(ideGenerated.get())
    attributes(semantifyrArtifact("xtext-ide-sources"))
}

val libs = the<LibrariesForLibs>()

val extension = extensions.create<XtextIdePluginConfiguration>("xtext").apply {
    genPath.convention(layout.projectDirectory.dir("src/main/xtext-gen"))
}

dependencies {
    api(libs.xtext.ide)
}

sourceSets {
    main {
        java.srcDir(extension.genPath)
        resources.srcDir(extension.genPath)
    }
}

tasks {
    val cloneIdeGenerated = register<Sync>("cloneIdeGenerated") {
        from(ideGeneratedFiles)
        into(extension.genPath)
    }

    compileJava {
        inputs.files(cloneIdeGenerated)
    }

    processResources {
        inputs.files(cloneIdeGenerated)
    }

    clean {
        delete(extension.genPath)
    }
}

interface XtextIdePluginConfiguration {
    val genPath: DirectoryProperty
}
