/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.conventions.registerRenderPictures
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.conventions.stampOnSuccess
import hu.bme.mit.semantifyr.gradle.conventions.sysmlCorpusModels

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

val sysmlWorker = configurations.dependencyScope("sysmlWorker")
val sysmlWorkerFiles = configurations.resolvable("sysmlWorkerFiles") {
    extendsFrom(sysmlWorker.get())
    attributes(semantifyrArtifact("sysml-worker"))
}

dependencies {
    editorCommon(project(":semantifyr-editor-common"))
    gammaCompiledExamples(project(":gamma-frontend"))
    sysmlCompiledExamples(project(":sysml-wrapper"))
    sysmlWorker(project(":sysml-wrapper"))
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

val sysmlCorpusDir = rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/examples")

val cloneSysmlv2TestModels = tasks.register<Sync>("cloneSysmlv2TestModels") {
    from(sysmlCorpusDir) {
        include("manifest.json")
        include(
            sysmlCorpusModels(sysmlCorpusDir).filter {
                it.isExample
            }.map {
                it.file
            },
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
    from(sysmlWorkerFiles) {
        include("sysml.tmLanguage.json")
        into("syntaxes")
    }
    into(importedVscodeDir)
}

val cloneSysmlWorker = tasks.register<Sync>("cloneSysmlWorker") {
    from(sysmlWorkerFiles)
    into(layout.buildDirectory.dir("sysml-worker"))
}

val renderBrandingPictures = registerRenderPictures(
    "renderBrandingPictures",
    layout.buildDirectory.dir("branding").get(),
    listOf(
        "favicon.ico=semantifyr-logo@16,32,48",
        "pwa-192.png=semantifyr-logo@192",
        "pwa-512.png=semantifyr-logo@512",
        "apple-touch-icon.png=semantifyr-logo@180",
        "social-card.png=social-card@1200",
    ),
)

val cloneBrandingAssets = tasks.register<Sync>("cloneBrandingAssets") {
    from(renderBrandingPictures)
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
    inputs.files(cloneBrandingAssets)
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    group = "build"

    configureSharedInputs()
    inputs.dir(project.layout.projectDirectory.dir("public"))
    inputs.file(project.layout.projectDirectory.file("index.html"))

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

    stampOnSuccess()
}

tasks.register<NpmTask>("runDev") {
    group = "application"

    configureSharedInputs()

    npmCommand.set(listOf("run", "dev"))
}

tasks.register<NpmTask>("loadTest") {
    group = "verification"

    inputs.files(tasks.npmInstall)

    npmCommand.set(listOf("run", "load-test", "--"))
    args.set(
        providers.gradleProperty("loadTest.args").map { value ->
            value.split(" ").filter {
                it.isNotBlank()
            }
        }.orElse(emptyList()),
    )
}

tasks {
    assemble {
        dependsOn(npmAssemble, cloneSysmlWorker)
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
