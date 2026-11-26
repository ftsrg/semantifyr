/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import groovy.json.JsonSlurper
import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.commitHash
import hu.bme.mit.semantifyr.gradle.readsServedExamples
import hu.bme.mit.semantifyr.gradle.readsWorkspace
import hu.bme.mit.semantifyr.gradle.registerBrandingAssets
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.servedExampleSources

plugins {
    id("hu.bme.mit.semantifyr.jvm")
    id("hu.bme.mit.semantifyr.nodejs")
}

val editorCommon = artifactsOf("npm-package", ":semantifyr-editor-common")
val sysmlConstructs = artifactsOf("sysml-constructs", ":sysml-wrapper")
val sysmlWorker = artifactsOf("sysml-worker", ":sysml-wrapper")
val grammars = artifactsOf("textmate-grammar", ":oxsts.lang", ":gamma.lang")
val liveScreenshots = artifactsOf("screenshots", ":semantifyr-live-frontend")
val liveWebDistribution = artifactsOf("web-distribution", ":semantifyr-live-frontend")
val liveUiMock = artifactsOf("live-ui-mock", ":semantifyr-live-frontend")
val vscodeManifest = artifactsOf("vscode-manifest", ":semantifyr-vscode")
val cliReference = artifactsOf("cli-reference", ":semantifyr-cli", ":sysml-cli", ":gamma-cli")
val generatedReference = artifactsOf("generated-reference", ":cli-common", ":verifier", ":sysml-frontend")
val generatedWitness = artifactsOf("generated-witness", ":verifier")
val phaseWalk = artifactsOf("phase-walk", ":semantifyr-cli")

dependencies {
    testImplementation(project(":oxsts.lang"))
    testImplementation(project(":oxsts.model"))
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

// the directories the pages import sources from, which the site's configuration and the test read too
val sourceAliasesFile = layout.projectDirectory.file("source-aliases.json")

@Suppress("UNCHECKED_CAST")
val sourceAliasDirectories = (JsonSlurper().parseText(providers.fileContents(sourceAliasesFile).asText.get()) as Map<String, String>).values.map {
    layout.projectDirectory.dir(it)
}

// one list for the site links test and the check's inputs, so that a file the test reads cannot be missing from the cache key
val linkingFilesFile = layout.projectDirectory.file("linking-files.json")

@Suppress("UNCHECKED_CAST")
val linkingFiles = (JsonSlurper().parseText(providers.fileContents(linkingFilesFile).asText.get()) as List<String>).map {
    isolated.rootProject.projectDirectory.file(it)
}

val cloneCliReference = tasks.register<Sync>("cloneCliReference") {
    from(cliReference)
    into(layout.buildDirectory.dir("cli-reference"))
}

// written for the alias @generated-reference: what the pages show of a type, generated from it
val cloneGeneratedReference = tasks.register<Sync>("cloneGeneratedReference") {
    from(generatedReference)
    into(layout.buildDirectory.dir("generated-reference"))
}

// written for the alias @generated-witness: the witness model the verifier derives from the stored timed report
val cloneGeneratedWitness = tasks.register<Sync>("cloneGeneratedWitness") {
    from(generatedWitness)
    into(layout.buildDirectory.dir("generated-witness"))
}

val clonePhaseWalk = tasks.register<Sync>("clonePhaseWalk") {
    from(phaseWalk)
    into(layout.buildDirectory.dir("phases"))
}

val importedBrandingDirectory = layout.projectDirectory.dir("static-imported-branding")

val cloneBrandingAssets = registerBrandingAssets(importedBrandingDirectory.dir("img"))

// written for the alias @sysml-constructs
val cloneSysmlConstructs = tasks.register<Sync>("cloneSysmlConstructs") {
    from(sysmlConstructs)
    into(layout.buildDirectory.dir("sysml-constructs"))
}

// written for the alias @grammars: the grammars live's editor loads
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

fun NpmTask.configureSharedInputs() {
    readsWorkspace()
    inputs.dir(layout.projectDirectory.dir("src")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("docusaurus.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("sidebars.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("babel.config.cts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("tsconfig.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("eslint.config.mjs")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("vitest.config.mts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(isolated.rootProject.projectDirectory.file("gradle.properties")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(isolated.rootProject.projectDirectory.file(".nvmrc")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(isolated.rootProject.projectDirectory.file("deploy/live/env.example")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(vscodeManifest).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(sourceAliasesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGeneratedWitness).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sourceAliasDirectories).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(servedExampleSources()).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGrammars).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneBrandingAssets).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(editorCommon).withPathSensitivity(PathSensitivity.RELATIVE)
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    configureSharedInputs()
    inputs.property("liveBackendUrl", providers.environmentVariable("SEMANTIFYR_LIVE_URL").orElse(""))
    environment.put("SEMANTIFYR_SITE_COMMIT", commitHash())
    // the deploying workflow names its tag when it is a release
    inputs.property("siteTag", providers.environmentVariable("SEMANTIFYR_SITE_TAG").orElse(""))
    inputs.files(cloneCliReference).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGeneratedReference).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(clonePhaseWalk).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneSysmlConstructs).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneScreenshots).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "assemble")
    inputs.files(cloneApiDocs).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(layout.buildDirectory.dir("docusaurus"))
    outputs.cacheIf {
        true
    }
}

val npmCheck = tasks.register<NpmTask>("npmCheck") {
    configureSharedInputs()
    inputs.file(linkingFilesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(linkingFiles).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(liveUiMock).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "check")

    outputs.file(layout.buildDirectory.file("tsconfig.tsbuildinfo"))
    outputs.file(layout.buildDirectory.file("eslint.json"))
    outputs.file(layout.buildDirectory.file("test-results/unit.xml"))
    outputs.cacheIf {
        true
    }
}

listOf(
    "Chromium",
    "Firefox",
    "Webkit",
).forEach { browser ->
    val report = "build/test-results/walk-${browser.lowercase()}.xml"
    tasks.register<NpmTask>("uiTest$browser") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP

        configureSharedInputs()
        inputs.file(layout.projectDirectory.file("vitest.walk.config.mts")).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(npmAssemble).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(liveWebDistribution).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(liveUiMock).withPathSensitivity(PathSensitivity.RELATIVE)

        npmCommand = listOf("run", "walk", "--", "--reporter=default", "--reporter=junit", "--outputFile.junit=$report")
        environment.put("TEST_BROWSER", browser.lowercase())

        outputs.file(layout.projectDirectory.file(report))
        outputs.cacheIf {
            true
        }
    }
}

tasks.named<Test>("test") {
    inputs.dir(layout.projectDirectory.dir("src/docs")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(sourceAliasesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGeneratedWitness).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sourceAliasDirectories).withPathSensitivity(PathSensitivity.RELATIVE)
    readsServedExamples()
}

configurations.consumable("sysmlDecisionsPageElements") {
    attributes(semantifyrArtifact("sysml-decisions-page"))
    outgoing.artifact(layout.projectDirectory.file("src/docs/learn/frontends/sysml-v2/decisions.mdx"))
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
