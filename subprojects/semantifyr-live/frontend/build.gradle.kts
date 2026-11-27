/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.ExampleDescriptions
import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.commitHash
import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.readsWorkspace
import hu.bme.mit.semantifyr.gradle.registerBrandingAssets
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.servedExamples
import org.gradle.api.file.RegularFile

plugins {
    id("hu.bme.mit.semantifyr.nodejs")
}

val editorCommon = artifactsOf("npm-package", ":semantifyr-editor-common")
val gammaCompiledExamples = artifactsOf("compiled-examples", ":gamma-frontend")
val sysmlCompiledExamples = artifactsOf("compiled-examples", ":sysml-wrapper")
val sysmlCompiledTutorial = artifactsOf("compiled-tutorial", ":sysml-wrapper")
val sysmlWorker = artifactsOf("sysml-worker", ":sysml-wrapper")
val grammars = artifactsOf("textmate-grammar", ":oxsts.lang", ":gamma.lang")
val languageConfiguration = artifactsOf("language-configuration", ":semantifyr-vscode")

val importedSnippetsDirectory = layout.projectDirectory.dir("src/snippets/imported")
val importedVscodeDirectory = layout.projectDirectory.dir("src/vscode/imported")
val publicDirectory = layout.projectDirectory.dir("public")

fun servedFiles(language: String): List<RegularFile> {
    return servedExamples(language).map {
        isolated.rootProject.projectDirectory.file(it.file)
    }
}

// live's libraries show the compiled form of the served models alone
fun compiledFiles(language: String): List<String> {
    return servedExamples(language).map {
        "${it.name}.$oxstsFileExtension"
    }
}

val cloneGammaExamples = tasks.register<Sync>("cloneGammaExamples") {
    from(servedFiles("gamma"))
    from(gammaCompiledExamples) {
        include(compiledFiles("gamma"))
    }
    into(importedSnippetsDirectory.dir("gamma"))
}

val cloneSysmlExamples = tasks.register<Sync>("cloneSysmlExamples") {
    from(servedFiles("sysml"))
    from(sysmlCompiledExamples) {
        include(compiledFiles("sysml"))
    }
    into(importedSnippetsDirectory.dir("sysmlv2"))
}

val cloneTutorialSnippets = tasks.register<Sync>("cloneTutorialSnippets") {
    from(servedFiles("oxsts"))
    into(importedSnippetsDirectory.dir("tutorial"))
}

// live's menu shows the first line of a model's comment under its label
val exampleDescriptions = tasks.register<ExampleDescriptions>("exampleDescriptions") {
    models.from(servedFiles("sysml"), servedFiles("gamma"), servedFiles("oxsts"))
    outputDirectory = importedSnippetsDirectory.dir("descriptions")
}

// the load test sends a served SysML v2 model as the OXSTS it compiles to, the tutorial's among them
val cloneLoadTestModels = tasks.register<Sync>("cloneLoadTestModels") {
    from(sysmlCompiledExamples) {
        include(compiledFiles("sysml"))
    }
    from(sysmlCompiledTutorial) {
        include(compiledFiles("sysml"))
    }
    into(layout.buildDirectory.dir("load-test-models/sysml"))
}

val cloneVscodeLanguageAssets = tasks.register<Sync>("cloneVscodeLanguageAssets") {
    from(languageConfiguration)
    from(grammars) {
        into("syntaxes")
    }
    from(sysmlWorker) {
        include("sysml.tmLanguage.json")
        into("syntaxes")
    }
    into(importedVscodeDirectory)
}

val cloneSysmlWorker = tasks.register<Sync>("cloneSysmlWorker") {
    from(sysmlWorker)
    into(layout.buildDirectory.dir("sysml-worker"))
}

val cloneBrandingAssets = registerBrandingAssets(publicDirectory)

fun NpmTask.configureSharedInputs() {
    readsWorkspace()
    inputs.dir(layout.projectDirectory.dir("src")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("vite.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("tsconfig.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("eslint.config.js")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(isolated.rootProject.projectDirectory.file("gradle.properties")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(editorCommon).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGammaExamples).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneSysmlExamples).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneTutorialSnippets).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(exampleDescriptions).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneVscodeLanguageAssets).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneBrandingAssets).withPathSensitivity(PathSensitivity.RELATIVE)
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    group = LifecycleBasePlugin.BUILD_GROUP

    configureSharedInputs()
    inputs.file(layout.projectDirectory.file("index.html")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("commit", commitHash())

    npmCommand = listOf("run", "assemble")
    outputs.dir(layout.projectDirectory.dir("dist"))
    outputs.cacheIf {
        true
    }
}

configurations.consumable("webDistributionElements") {
    attributes(semantifyrArtifact("web-distribution"))
    outgoing.artifact(layout.projectDirectory.dir("dist")) {
        builtBy(npmAssemble)
    }
}

val npmCheck = tasks.register<NpmTask>("npmCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    configureSharedInputs()
    // an example's page is tested against live's head and the rights the REUSE file gives, and the load test's models are read as it sends them
    inputs.file(layout.projectDirectory.file("index.html")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(isolated.rootProject.projectDirectory.file("REUSE.toml")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneLoadTestModels).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "check")

    outputs.file(layout.buildDirectory.file("tsconfig.tsbuildinfo"))
    outputs.file(layout.buildDirectory.file("eslint.json"))
    outputs.file(layout.buildDirectory.file("test-results/unit.xml"))
    outputs.cacheIf {
        true
    }
}

tasks.register<NpmTask>("integrationTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    configureSharedInputs()

    npmCommand = listOf("run", "test:integration")

    outputs.file(layout.buildDirectory.file("test-results/integration.xml"))
    outputs.cacheIf {
        true
    }
}

fun NpmTask.runsInBrowser(browser: String, suite: String, configuration: String) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    configureSharedInputs()
    inputs.file(layout.projectDirectory.file(configuration)).withPathSensitivity(PathSensitivity.RELATIVE)

    val report = "build/test-results/$suite-$browser.xml"
    npmCommand = listOf("run", suite, "--", "--reporter=default", "--reporter=junit", "--outputFile.junit=$report")
    environment.put("TEST_BROWSER", browser)

    outputs.file(layout.projectDirectory.file(report))
    outputs.cacheIf {
        true
    }
}

listOf(
    "chromium",
    "firefox",
    "webkit",
).forEach { browser ->
    val taskSuffix = browser.replaceFirstChar {
        it.uppercase()
    }

    val screenTest = tasks.register<NpmTask>("screenTest$taskSuffix") {
        runsInBrowser(browser, "screens", "vitest.screen.config.ts")
        outputs.dir(layout.buildDirectory.dir("screens/$browser"))
    }

    val applicationTest = tasks.register<NpmTask>("applicationTest$taskSuffix") {
        runsInBrowser(browser, "ui", "vitest.ui.config.ts")
        inputs.files(npmAssemble).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(cloneSysmlWorker).withPathSensitivity(PathSensitivity.RELATIVE)
    }

    tasks.register("uiTest$taskSuffix") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(screenTest, applicationTest)
    }
}

val screenshots = tasks.register<NpmTask>("screenshots") {
    group = LifecycleBasePlugin.BUILD_GROUP

    configureSharedInputs()
    inputs.file(layout.projectDirectory.file("vitest.screenshots.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(npmAssemble).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneSysmlWorker).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "screenshots")
    environment.put("TEST_BROWSER", "chromium")
    val screenshotsDirectory = layout.buildDirectory.dir("screenshots")
    outputs.dir(screenshotsDirectory)
    outputs.cacheIf {
        true
    }
    doFirst {
        screenshotsDirectory.get().asFile.deleteRecursively()
    }
}

configurations.consumable("screenshotsElements") {
    attributes(semantifyrArtifact("screenshots"))
    outgoing.artifact(layout.buildDirectory.dir("screenshots")) {
        builtBy(screenshots)
    }
}

tasks.register<NpmTask>("runDev") {
    group = ApplicationPlugin.APPLICATION_GROUP

    configureSharedInputs()

    npmCommand = listOf("run", "dev")
}

tasks.register<NpmTask>("loadTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    readsWorkspace()
    inputs.files(cloneLoadTestModels)

    npmCommand = listOf("run", "load-test", "--")
    args = providers.gradleProperty("loadTest.args").map { value ->
        value.split(" ").filter {
            it.isNotBlank()
        }
    }.orElse(emptyList())
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(npmAssemble, cloneSysmlWorker)
}

tasks.register("continuousIntegration") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.named("integrationTest"))
}

tasks.named("check") {
    dependsOn(npmCheck)
}

tasks.named<Delete>("clean") {
    delete("dist")
    delete(importedSnippetsDirectory)
    delete(importedVscodeDirectory)
    delete(publicDirectory)
}
