/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.nodejs")
}

val editorCommon = configurations.dependencyScope("editorCommon")
val editorCommonFiles = configurations.resolvable("editorCommonFiles") {
    extendsFrom(editorCommon.get())
    attributes(semantifyrArtifact("npm-package"))
}

val gammaCompiledExamples = configurations.dependencyScope("gammaCompiledExamples")
val gammaCompiledExamplesFiles = configurations.resolvable("gammaCompiledExamplesFiles") {
    extendsFrom(gammaCompiledExamples.get())
    attributes(semantifyrArtifact("compiled-examples"))
}

val sysmlCompiledExamples = configurations.dependencyScope("sysmlCompiledExamples")
val sysmlCompiledExamplesFiles = configurations.resolvable("sysmlCompiledExamplesFiles") {
    extendsFrom(sysmlCompiledExamples.get())
    attributes(semantifyrArtifact("compiled-examples"))
}

dependencies {
    editorCommon(project(":semantifyr-editor-common"))
    gammaCompiledExamples(project(":gamma-frontend"))
    sysmlCompiledExamples(project(":sysml-wrapper"))
}

val importedSnippetsDir = project.layout.projectDirectory.dir("src/snippets/imported")
val importedVscodeDir = project.layout.projectDirectory.dir("src/vscode/imported")
val brandingDir = rootProject.layout.projectDirectory.dir("branding")
val publicDir = project.layout.projectDirectory.dir("public")

val cloneGammaTestModels = tasks.register<Sync>("cloneGammaTestModels") {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/examples")) {
        include("*.gamma")
    }
    from(gammaCompiledExamplesFiles) {
        include("*.oxsts")
    }
    into(importedSnippetsDir.dir("gamma"))
}

val cloneSysmlv2TestModels = tasks.register<Sync>("cloneSysmlv2TestModels") {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/examples")) {
        include(
            "compressedspacecraft.sysml",
            "crossroads.sysml",
            "door_access.sysml",
            "orion_protocol.sysml",
        )
    }
    from(sysmlCompiledExamplesFiles) {
        include("*.oxsts")
    }
    into(importedSnippetsDir.dir("sysmlv2"))
}

val cloneTutorialSnippets = tasks.register<Sync>("cloneTutorialSnippets") {
    from(rootProject.layout.projectDirectory.dir("oxsts-test-models/tutorial"))
    into(importedSnippetsDir.dir("tutorial"))
}

val cloneTestModels = tasks.register("cloneTestModels") {
    dependsOn(cloneGammaTestModels, cloneSysmlv2TestModels, cloneTutorialSnippets)
}

val cloneVscodeLanguageAssets = tasks.register<Sync>("cloneVscodeLanguageAssets") {
    from(rootProject.layout.projectDirectory.dir("subprojects/semantifyr-vscode")) {
        include("language-configuration.json")
        include("syntaxes/*.tmLanguage.json")
    }
    into(importedVscodeDir)
}

val cloneBrandingAssets = tasks.register<Sync>("cloneBrandingAssets") {
    from(brandingDir) {
        include("favicon.ico")
    }
    from(brandingDir) {
        include("semantifyr-logo.svg")
        rename { "pwa.svg" }
    }
    from(brandingDir) {
        include("semantifyr-full-light.svg")
        rename { "logo-full-light.svg" }
    }
    from(brandingDir) {
        include("semantifyr-full-dark.svg")
        rename { "logo-full-dark.svg" }
    }
    from(brandingDir) {
        include("semantifyr-logo-192.png")
        rename { "pwa-192.png" }
    }
    from(brandingDir) {
        include("semantifyr-logo-512.png")
        rename { "pwa-512.png" }
    }
    from(brandingDir) {
        include("semantifyr-logo-180.png")
        rename { "apple-touch-icon.png" }
    }
    into(publicDir)
}

tasks.npmInstall {
    workingDir = rootProject.projectDir
}

fun NpmTask.configureSharedInputs() {
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.file(project.layout.projectDirectory.file("vite.config.ts"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("eslint.config.js"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tsconfig.base.json"))
    inputs.file(rootProject.layout.projectDirectory.file("eslint.config.base.js"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.file(rootProject.layout.projectDirectory.file("gradle.properties"))
    inputs.files(tasks.npmInstall)
    inputs.files(editorCommonFiles)
    inputs.files(cloneTestModels)
    inputs.files(cloneVscodeLanguageAssets)
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    group = "build"

    configureSharedInputs()
    inputs.dir(project.layout.projectDirectory.dir("public"))
    inputs.file(project.layout.projectDirectory.file("index.html"))
    inputs.files(cloneBrandingAssets)

    npmCommand.set(listOf("run", "assemble"))
    outputs.dir(project.layout.projectDirectory.dir("dist"))
}

configurations.consumable("webDistributionElements") {
    attributes(semantifyrArtifact("web-distribution"))
    outgoing.artifact(project.layout.projectDirectory.dir("dist")) {
        builtBy(npmAssemble)
    }
}

val npmCheck = tasks.register<NpmTask>("npmCheck") {
    group = "verification"

    configureSharedInputs()

    npmCommand.set(listOf("run", "check"))
}

tasks.register<NpmTask>("runDev") {
    group = "application"

    configureSharedInputs()

    npmCommand.set(listOf("run", "dev"))
}

tasks {
    assemble {
        dependsOn(npmAssemble)
    }

    check {
        dependsOn(npmCheck)
    }

    clean {
        delete("dist")
        delete(importedSnippetsDir)
        delete(importedVscodeDir)
        delete(publicDir)
    }
}
