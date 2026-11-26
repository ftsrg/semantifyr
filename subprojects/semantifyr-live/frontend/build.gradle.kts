/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.ExampleDescriptions
import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.registerBrandingAssets
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.servedExamples
import hu.bme.mit.semantifyr.gradle.stampOnSuccess
import hu.bme.mit.semantifyr.gradle.testBrowser
import hu.bme.mit.semantifyr.gradle.testChromium
import org.gradle.api.file.RegularFile

plugins {
    id("hu.bme.mit.semantifyr.nodejs")
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

val sysmlCompiledTutorial = configurations.dependencyScope("sysmlCompiledTutorial")
val sysmlCompiledTutorialFiles = configurations.resolvable("sysmlCompiledTutorialFiles") {
    extendsFrom(sysmlCompiledTutorial.get())
    attributes(semantifyrArtifact("compiled-tutorial"))
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
    sysmlCompiledTutorial(project(":sysml-wrapper"))
    sysmlWorker(project(":sysml-wrapper"))
}

val importedSnippetsDirectory = layout.projectDirectory.dir("src/snippets/imported")
val importedVscodeDirectory = layout.projectDirectory.dir("src/vscode/imported")
val publicDirectory = layout.projectDirectory.dir("public")

fun servedFiles(language: String): List<RegularFile> {
    return servedExamples(language).map {
        rootProject.layout.projectDirectory.file(it.file)
    }
}

// live's libraries show the compiled form of the served models alone
fun compiledFiles(language: String): List<String> {
    return servedExamples(language).map {
        "${it.name}.$oxstsFileExtension"
    }
}

val cloneGammaTestModels = tasks.register<Sync>("cloneGammaTestModels") {
    from(servedFiles("gamma"))
    from(gammaCompiledExamplesFiles) {
        include(compiledFiles("gamma"))
    }
    into(importedSnippetsDirectory.dir("gamma"))
}

val cloneSysmlv2TestModels = tasks.register<Sync>("cloneSysmlv2TestModels") {
    from(servedFiles("sysml"))
    from(sysmlCompiledExamplesFiles) {
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
    from(sysmlCompiledExamplesFiles) {
        include(compiledFiles("sysml"))
    }
    from(sysmlCompiledTutorialFiles) {
        include(compiledFiles("sysml"))
    }
    into(layout.buildDirectory.dir("load-test-models/sysml"))
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
    into(importedVscodeDirectory)
}

val cloneSysmlWorker = tasks.register<Sync>("cloneSysmlWorker") {
    from(sysmlWorkerFiles)
    into(layout.buildDirectory.dir("sysml-worker"))
}

val cloneBrandingAssets = registerBrandingAssets(publicDirectory)

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
    inputs.files(cloneGammaTestModels)
    inputs.files(cloneSysmlv2TestModels)
    inputs.files(cloneTutorialSnippets)
    inputs.files(exampleDescriptions)
    inputs.files(cloneVscodeLanguageAssets)
    inputs.files(cloneBrandingAssets)
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    group = LifecycleBasePlugin.BUILD_GROUP

    configureSharedInputs()
    inputs.file(layout.projectDirectory.file("index.html"))

    npmCommand = listOf("run", "assemble")
    outputs.dir(layout.projectDirectory.dir("dist"))
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
    inputs.file(rootProject.layout.projectDirectory.file("REUSE.toml")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneLoadTestModels).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(project(":oxsts.lang").layout.projectDirectory.file("src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2")).withPathSensitivity(PathSensitivity.NONE)

    npmCommand = listOf("run", "check")

    stampOnSuccess()
}

tasks.register<NpmTask>("integrationTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    configureSharedInputs()

    npmCommand = listOf("run", "test:integration")

    stampOnSuccess()
}

val testBrowser = testBrowser()

fun NpmTask.runInTestBrowser(script: String, configuration: String) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    configureSharedInputs()
    inputs.file(layout.projectDirectory.file(configuration))

    npmCommand = listOf("run", script)
    environment.put("TEST_BROWSER", testBrowser)

    val buildDirectory = layout.buildDirectory
    val taskName = name
    val stamp = testBrowser.flatMap {
        buildDirectory.file("$taskName-$it.stamp")
    }
    outputs.file(stamp)
    doLast {
        stamp.get().asFile.writeText("")
    }
}

val screenTest = tasks.register<NpmTask>("screenTest") {
    runInTestBrowser("screens", "vitest.screen.config.ts")
    val buildDirectory = layout.buildDirectory
    outputs.dir(
        testBrowser.flatMap {
            buildDirectory.dir("screens/$it")
        },
    )
}

val applicationTest = tasks.register<NpmTask>("applicationTest") {
    runInTestBrowser("ui", "vitest.ui.config.ts")
    inputs.files(npmAssemble)
    inputs.files(cloneSysmlWorker)
}

val uiTest = tasks.register("uiTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(screenTest, applicationTest)
}

tasks.register("continuousIntegration") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(uiTest)
}

// a picture holds the fonts and the rendering of the browser and the system it is taken on
val screenshotBrowser = testChromium("The screenshots need")

val screenshots = tasks.register<NpmTask>("screenshots") {
    group = LifecycleBasePlugin.BUILD_GROUP

    configureSharedInputs()
    inputs.file(layout.projectDirectory.file("vitest.screenshots.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    // dist/ carries its build time, so the build's sources key the cache and the build itself only runs first
    inputs.file(layout.projectDirectory.file("index.html")).withPathSensitivity(PathSensitivity.RELATIVE)
    dependsOn(npmAssemble)
    inputs.files(cloneSysmlWorker).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("screenshotBrowser", screenshotBrowser)

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

    inputs.files(tasks.npmInstall)
    inputs.files(cloneLoadTestModels)

    npmCommand = listOf("run", "load-test", "--")
    args = providers.gradleProperty("loadTest.args").map { value ->
        value.split(" ").filter {
            it.isNotBlank()
        }
    }.orElse(emptyList())
}

tasks {
    register("pack") {
        dependsOn(npmAssemble, cloneSysmlWorker)
    }

    check {
        dependsOn(npmCheck)
    }

    clean {
        delete("dist")
        delete(importedSnippetsDirectory)
        delete(importedVscodeDirectory)
        delete(publicDirectory)
    }
}
