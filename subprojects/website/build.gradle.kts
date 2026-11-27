/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import groovy.json.JsonSlurper
import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.readsPackage
import hu.bme.mit.semantifyr.gradle.readsWorkspace
import hu.bme.mit.semantifyr.gradle.registerBrandingAssets
import hu.bme.mit.semantifyr.gradle.registerNpmCheck
import hu.bme.mit.semantifyr.gradle.servedExampleSources

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    id("hu.bme.mit.semantifyr.nodejs")
}

val editorCommon = artifactsOf("npm-package", ":semantifyr-editor-common")
val sysmlWorker = artifactsOf("sysml-worker", ":sysml-wrapper")
val grammars = artifactsOf("textmate-grammar", ":oxsts.lang", ":gamma.lang")
val liveScreenshots = artifactsOf("screenshots", ":semantifyr-live-frontend")
val generatedWitness = artifactsOf("generated-witness", ":verifier")

dependencies {
    testImplementation(project(":compiler"))
    testImplementation(testFixtures(project(":oxsts.lang")))
    testImplementation(libs.gson)

    listOf(
        ":backend",
        ":benchmark",
        ":cex.lang",
        ":cli-common",
        ":compiler",
        ":gamma-cli",
        ":gamma-frontend",
        ":gamma.lang",
        ":gamma.lang.ide",
        ":guice-common",
        ":lang-ide-common",
        ":logging",
        ":nuxmv-backend",
        ":nuxmv-executor",
        ":oxsts.lang",
        ":oxsts.lang.ide",
        ":oxsts.model",
        ":portfolios",
        ":semantifyr-cli",
        ":semantifyr-live-backend",
        ":spin-backend",
        ":spin-executor",
        ":sysml-cli",
        ":sysml-frontend",
        ":sysml-wrapper",
        ":theta-backend",
        ":theta-executor",
        ":uppaal-backend",
        ":uppaal-executor",
        ":utils",
        ":verifier",
        ":xsts.lang",
    ).forEach {
        dokka(project(it))
    }
}

dokka {
    moduleName = "Semantifyr"
}

val sourceAliasesFile = layout.projectDirectory.file("source-aliases.json")

@Suppress("UNCHECKED_CAST")
val sourceAliasDirectories = (JsonSlurper().parseText(providers.fileContents(sourceAliasesFile).asText.get()) as Map<String, String>).values.map {
    layout.projectDirectory.dir(it)
}

val cloneGeneratedWitness = tasks.register<Sync>("cloneGeneratedWitness") {
    from(generatedWitness)
    into(layout.buildDirectory.dir("generated-witness"))
}

val importedBrandingDirectory = layout.projectDirectory.dir("static-imported-branding")

val cloneBrandingAssets = registerBrandingAssets(importedBrandingDirectory.dir("img"))

val cloneGrammars = tasks.register<Sync>("cloneGrammars") {
    from(grammars)
    from(sysmlWorker) {
        include("sysml.tmLanguage.json")
    }
    into(layout.buildDirectory.dir("grammars"))
}

val cloneScreenshots = tasks.register<Sync>("cloneScreenshots") {
    from(liveScreenshots)
    into(layout.buildDirectory.dir("live-screenshots/img/screenshots"))
}

val cloneApiDocs = tasks.register<Sync>("cloneApiDocs") {
    from(
        tasks.dokkaGeneratePublicationHtml.flatMap {
            it.outputDirectory
        },
    )
    into(layout.buildDirectory.dir("api-static/api"))
}

fun NpmTask.configureSharedInputs() {
    inputs.files(editorCommon).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("docusaurus.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("sidebars.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(sourceAliasesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGrammars).withPathSensitivity(PathSensitivity.RELATIVE)
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    group = LifecycleBasePlugin.BUILD_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.property("liveBackendUrl", providers.environmentVariable("SEMANTIFYR_LIVE_URL").orElse(""))
    inputs.files(cloneGeneratedWitness).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sourceAliasDirectories).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(servedExampleSources()).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneBrandingAssets).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneScreenshots).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneApiDocs).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "assemble")

    outputs.dir(layout.buildDirectory.dir("docusaurus"))
    outputs.cacheIf {
        true
    }
}

tasks.register<NpmTask>("serve") {
    group = ApplicationPlugin.APPLICATION_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.files(cloneGeneratedWitness).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneBrandingAssets).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneScreenshots).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneApiDocs).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "dev")
}

registerNpmCheck {
    configureSharedInputs()
}

val extractFences = tasks.register<NpmTask>("extractFences") {
    readsWorkspace()
    inputs.dir(layout.projectDirectory.dir("src/docs")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/plugins")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "fences")

    outputs.dir(layout.buildDirectory.dir("fences"))
    outputs.cacheIf {
        true
    }
}

tasks.named("test") {
    inputs.files(extractFences).withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(npmAssemble)
}

tasks.named<Delete>("clean") {
    delete(
        ".docusaurus",
        importedBrandingDirectory,
    )
}
