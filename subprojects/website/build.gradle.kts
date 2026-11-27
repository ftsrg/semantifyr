/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import groovy.json.JsonSlurper
import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.readsWorkspace
import hu.bme.mit.semantifyr.gradle.registerBrandingAssets
import hu.bme.mit.semantifyr.gradle.servedExampleSources

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    id("hu.bme.mit.semantifyr.nodejs")
}

val editorCommon = artifactsOf("npm-package", ":semantifyr-editor-common")
val sysmlWorker = artifactsOf("sysml-worker", ":sysml-wrapper")
val grammars = artifactsOf("textmate-grammar", ":oxsts.lang", ":gamma.lang")
val liveScreenshots = artifactsOf("screenshots", ":semantifyr-live-frontend")
val liveWebDistribution = artifactsOf("web-distribution", ":semantifyr-live-frontend")
val liveUiMock = artifactsOf("live-ui-mock", ":semantifyr-live-frontend")
val generatedWitness = artifactsOf("generated-witness", ":verifier")
val phases = artifactsOf("phases", ":semantifyr-cli")

dependencies {
    testImplementation(project(":oxsts.lang"))
    testImplementation(testFixtures(project(":oxsts.lang")))
    testImplementation(libs.ecore)
    testImplementation(libs.gson)
    testImplementation(libs.guice)
    testImplementation(libs.xtext.core)
    testImplementation(libs.xtext.testing)
    testImplementation(libs.xtext.util)

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

val linkingFilesFile = layout.projectDirectory.file("linking-files.json")

@Suppress("UNCHECKED_CAST")
val linkingFiles = (JsonSlurper().parseText(providers.fileContents(linkingFilesFile).asText.get()) as List<String>).map {
    isolated.rootProject.projectDirectory.file(it)
}

val cloneGeneratedWitness = tasks.register<Sync>("cloneGeneratedWitness") {
    from(generatedWitness)
    into(layout.buildDirectory.dir("generated-witness"))
}

val clonePhases = tasks.register<Sync>("clonePhases") {
    from(phases)
    into(layout.buildDirectory.dir("phases"))
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
    if (providers.gradleProperty("website.apiDocs").getOrElse("false").toBoolean()) {
        from(
            tasks.dokkaGeneratePublicationHtml.flatMap {
                it.outputDirectory
            },
        )
    }
    into(layout.buildDirectory.dir("api-static/api"))
}

fun NpmTask.readsPackage() {
    readsWorkspace()
    inputs.files(
        layout.projectDirectory.dir("src").asFileTree.matching {
            exclude("docs/**")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("tsconfig.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(isolated.rootProject.projectDirectory.file(".nvmrc")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(editorCommon).withPathSensitivity(PathSensitivity.RELATIVE)
}

fun NpmTask.readsSite() {
    readsPackage()
    inputs.file(layout.projectDirectory.file("docusaurus.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("sidebars.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(sourceAliasesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(linkingFilesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGrammars).withPathSensitivity(PathSensitivity.RELATIVE)
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    readsSite()
    inputs.dir(layout.projectDirectory.dir("src/docs")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("liveBackendUrl", providers.environmentVariable("SEMANTIFYR_LIVE_URL").orElse(""))
    inputs.files(linkingFiles).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGeneratedWitness).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sourceAliasDirectories).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(servedExampleSources()).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneBrandingAssets).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(clonePhases).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneScreenshots).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneApiDocs).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "assemble")

    outputs.dir(layout.buildDirectory.dir("docusaurus"))
    outputs.cacheIf {
        true
    }
}

val npmCheck = tasks.register<NpmTask>("npmCheck") {
    readsSite()
    inputs.file(layout.projectDirectory.file("eslint.config.mjs")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("vitest.config.mts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("vitest.ui.config.mts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(liveUiMock).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "check")

    outputs.file(layout.buildDirectory.file("tsconfig.tsbuildinfo"))
    outputs.file(layout.buildDirectory.file("eslint.json"))
    outputs.file(layout.buildDirectory.file("test-results/unit.xml"))
    outputs.cacheIf {
        true
    }
}

tasks.register<NpmTask>("uiTestChromium") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    readsPackage()
    inputs.file(layout.projectDirectory.file("vitest.ui.config.mts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(npmAssemble).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(liveWebDistribution).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(liveUiMock).withPathSensitivity(PathSensitivity.RELATIVE)

    val report = "build/test-results/ui-chromium.xml"
    npmCommand = listOf("run", "ui", "--", "--reporter=default", "--reporter=junit", "--outputFile.junit=$report")

    outputs.file(layout.projectDirectory.file(report))
    outputs.cacheIf {
        true
    }
}

val extractFences = tasks.register<NpmTask>("extractFences") {
    readsWorkspace()
    inputs.dir(layout.projectDirectory.dir("src/docs")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/plugins")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(sourceAliasesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(isolated.rootProject.projectDirectory.file(".nvmrc")).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "fences")

    outputs.dir(layout.buildDirectory.dir("fences"))
    outputs.cacheIf {
        true
    }
}

tasks.named<Test>("test") {
    inputs.files(extractFences).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGeneratedWitness).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sourceAliasDirectories).withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(npmAssemble)
}

tasks.named("check") {
    dependsOn(npmCheck)
}

tasks.named<Delete>("clean") {
    delete(".docusaurus")
    delete(importedBrandingDirectory)
}
