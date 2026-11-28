/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.pnpm.task.PnpmTask
import groovy.json.JsonSlurper
import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.readsPackage
import hu.bme.mit.semantifyr.gradle.readsWorkspace
import hu.bme.mit.semantifyr.gradle.registerPnpmCheck
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.servedExampleSources

plugins {
    id("hu.bme.mit.semantifyr.theta")
    id("hu.bme.mit.semantifyr.suites")
    id("hu.bme.mit.semantifyr.nodejs")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val editorCommon = artifactsOf("npm-package", ":semantifyr-editor-common")
val sysmlWorker = artifactsOf("sysml-worker", ":sysml-wrapper")
val grammars = artifactsOf("textmate-grammar", ":oxsts.lang", ":gamma.lang")
val liveScreenshots = artifactsOf("screenshots", ":semantifyr-live-frontend")
val webBranding = artifactsOf("web-branding", ":branding")

val tutorialDirectory = layout.projectDirectory.dir("models/tutorial")

dependencies {
    testImplementation(project(":compiler"))
    testImplementation(testFixtures(project(":oxsts.lang")))
    testImplementation(libs.kotlinx.serialization.json)

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

val importedBrandingDirectory = layout.projectDirectory.dir("static-imported-branding")

val cloneBrandingAssets = tasks.register<Sync>("cloneBrandingAssets") {
    from(webBranding)
    into(importedBrandingDirectory.dir("img"))
}

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

fun PnpmTask.configureSharedInputs() {
    inputs.files(editorCommon).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("docusaurus.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("sidebars.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(sourceAliasesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGrammars).withPathSensitivity(PathSensitivity.RELATIVE)
}

val pnpmAssemble = tasks.register<PnpmTask>("pnpmAssemble") {
    group = LifecycleBasePlugin.BUILD_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.property("liveBackendUrl", providers.environmentVariable("SEMANTIFYR_LIVE_URL").orElse(""))
    inputs.files(sourceAliasDirectories).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(servedExampleSources()).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneBrandingAssets).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneScreenshots).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneApiDocs).withPathSensitivity(PathSensitivity.RELATIVE)

    pnpmCommand = listOf("run", "assemble")

    outputs.dir(layout.buildDirectory.dir("docusaurus"))
    outputs.cacheIf {
        true
    }
}

tasks.register<PnpmTask>("serve") {
    group = ApplicationPlugin.APPLICATION_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.files(cloneBrandingAssets).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneScreenshots).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneApiDocs).withPathSensitivity(PathSensitivity.RELATIVE)

    pnpmCommand = listOf("run", "dev")
}

registerPnpmCheck {
    configureSharedInputs()
}

val extractFences = tasks.register<PnpmTask>("extractFences") {
    readsWorkspace()
    inputs.dir(layout.projectDirectory.dir("src/docs")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/plugins")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)

    pnpmCommand = listOf("run", "fences")

    outputs.dir(layout.buildDirectory.dir("fences"))
    outputs.cacheIf {
        true
    }
}

tasks.named<Test>("test") {
    inputs.files(extractFences).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("models")).withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.named("integrationTest") {
    inputs.dir(layout.projectDirectory.dir("models/examples")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(tutorialDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
}

configurations.consumable("oxstsTutorialElements") {
    attributes(semantifyrArtifact("oxsts-tutorial"))
    outgoing.artifact(tutorialDirectory)
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(pnpmAssemble)
}

tasks.named<Delete>("clean") {
    delete(
        ".docusaurus",
        importedBrandingDirectory,
    )
}
