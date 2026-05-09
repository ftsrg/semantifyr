/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.nodejs")
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val editorCommonDist by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val gammaCompiledExamples by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val sysmlCompiledExamples by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    editorCommonDist(project(":semantifyr-editor-common", configuration = "distributionOutput"))
    gammaCompiledExamples(project(":gamma-frontend", configuration = "compiledExamples"))
    sysmlCompiledExamples(project(":sysml-wrapper", configuration = "compiledExamples"))
}

val importedSnippetsDir = project.layout.projectDirectory.dir("src/snippets/imported")

val cloneGammaTestModels by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/examples")) {
        include("*.gamma")
    }
    from(gammaCompiledExamples) {
        include("*.oxsts")
    }
    into(importedSnippetsDir.dir("gamma"))
}

val cloneSysmlv2TestModels by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/examples")) {
        include(
            "compressedspacecraft.sysml",
            "crossroads.sysml",
            "door_access.sysml",
            "orion_protocol.sysml",
        )
    }
    from(sysmlCompiledExamples) {
        include("*.oxsts")
    }
    into(importedSnippetsDir.dir("sysmlv2"))
}

val cloneTestModels by tasks.registering {
    dependsOn(cloneGammaTestModels, cloneSysmlv2TestModels, cloneTutorialSnippets)
}

val cloneVscodeLanguageAssets by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("subprojects/semantifyr-vscode")) {
        include("language-configuration.json")
        include("syntaxes/*.tmLanguage.json")
    }
    into(importedVscodeDir)
}

// npm workspaces: install runs once at the repo root so all four TS packages share a single
// node_modules tree. The per-package package-lock.json files were retired with the workspace
// migration; the only lockfile is at the root.
tasks.npmInstall {
    workingDir = rootProject.projectDir
}

val assembleFrontend by tasks.registering(NpmTask::class) {
    group = "build"

    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.file(project.layout.projectDirectory.file("index.html"))
    inputs.file(project.layout.projectDirectory.file("vite.config.ts"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall)
    inputs.files(editorCommonDist)
    inputs.files(cloneTestModels)

    npmCommand.set(listOf("run", "build"))
    outputs.dir(project.layout.projectDirectory.dir("dist"))
}

artifacts {
    add(distributionOutput.name, project.layout.projectDirectory.dir("dist")) {
        builtBy(assembleFrontend)
    }
}

val test by tasks.registering(NpmTask::class) {
    group = "verification"

    inputs.files(tasks.npmInstall)
    inputs.files(editorCommonDist)
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.file(project.layout.projectDirectory.file("vite.config.ts"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.files(cloneTestModels)
    npmCommand.set(listOf("test"))
}

tasks {
    assemble {
        dependsOn(assembleFrontend)
    }

    check {
        dependsOn(test)
    }

    clean {
        delete("dist")
        delete(importedSnippetsDir)
    }
}
