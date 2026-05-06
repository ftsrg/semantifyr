/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.frontend")
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
    group = "build"
    description = "Stage the Gamma example models (sources + frontend-compiled OXSTS) into src/snippets/imported/gamma so the SPA can bundle them."
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/examples")) {
        include("*.gamma")
    }
    from(gammaCompiledExamples) {
        include("*.oxsts")
    }
    into(importedSnippetsDir.dir("gamma"))
}

val cloneSysmlv2TestModels by tasks.registering(Sync::class) {
    group = "build"
    description = "Stage the SysMLv2 example models (sources + frontend-compiled OXSTS) into src/snippets/imported/sysmlv2 so the SPA can bundle them."
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
    group = "build"
    description = "Aggregate sync of every upstream test-model snapshot consumed by the SPA."
    dependsOn(cloneGammaTestModels, cloneSysmlv2TestModels)
}

// npm workspaces: install runs once at the repo root so all four TS packages share a single
// node_modules tree. The per-package package-lock.json files were retired with the workspace
// migration; the only lockfile is at the root.
tasks.npmInstall {
    workingDir = rootProject.projectDir
}

val assembleFrontend by tasks.registering(NpmTask::class) {
    group = "build"
    description = "Build the production SPA bundle into dist/"

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
    description = "Run the frontend Vitest unit + integration tests"
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
