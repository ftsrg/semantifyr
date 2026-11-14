/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.conventions.ExampleDescriptions
import hu.bme.mit.semantifyr.gradle.conventions.registerRenderPictures
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.conventions.servedExamples
import hu.bme.mit.semantifyr.gradle.conventions.stampOnSuccess
import org.gradle.api.file.RegularFile

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

fun servedFiles(language: String): List<RegularFile> {
    return servedExamples(language).map {
        rootProject.layout.projectDirectory.file(it.file)
    }
}

// live's libraries show the compiled form of the served models alone
fun compiledFiles(language: String): List<String> {
    return servedExamples(language).map {
        "${it.name}.oxsts"
    }
}

val cloneGammaTestModels = tasks.register<Sync>("cloneGammaTestModels") {
    from(servedFiles("gamma"))
    from(gammaCompiledExamplesFiles) {
        include(compiledFiles("gamma"))
    }
    into(importedSnippetsDir.dir("gamma"))
}

val cloneSysmlv2TestModels = tasks.register<Sync>("cloneSysmlv2TestModels") {
    from(servedFiles("sysml"))
    from(sysmlCompiledExamplesFiles) {
        include(compiledFiles("sysml"))
    }
    into(importedSnippetsDir.dir("sysmlv2"))
}

val cloneTutorialSnippets = tasks.register<Sync>("cloneTutorialSnippets") {
    from(servedFiles("oxsts"))
    into(importedSnippetsDir.dir("tutorial"))
}

// live's menu shows the first line of a model's comment under its label
val exampleDescriptions = tasks.register<ExampleDescriptions>("exampleDescriptions") {
    models.from(servedFiles("sysml"), servedFiles("gamma"), servedFiles("oxsts"))
    outputDirectory.set(importedSnippetsDir.dir("descriptions"))
}

val cloneTestModels = tasks.register("cloneTestModels") {
    dependsOn(cloneGammaTestModels, cloneSysmlv2TestModels, cloneTutorialSnippets, exampleDescriptions)
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

val testBrowser = providers.gradleProperty("testBrowser").orElse("chromium")

fun NpmTask.runInTestBrowser(script: String, configuration: String) {
    group = "verification"

    configureSharedInputs()
    inputs.file(project.layout.projectDirectory.file(configuration))
    inputs.property("testBrowser", testBrowser)

    npmCommand.set(listOf("run", script))
    environment.put("TEST_BROWSER", testBrowser)

    val buildDirectory = project.layout.buildDirectory
    val taskName = name
    val stamp = testBrowser.flatMap {
        buildDirectory.file("$taskName-$it.stamp")
    }
    outputs.file(stamp)
    doLast {
        stamp.get().asFile.writeText("")
    }
}

tasks.register<NpmTask>("screenTest") {
    runInTestBrowser("screens", "vitest.screen.config.ts")
    val buildDirectory = project.layout.buildDirectory
    outputs.dir(
        testBrowser.flatMap {
            buildDirectory.dir("screens/$it")
        },
    )
}

tasks.register<NpmTask>("uiTest") {
    runInTestBrowser("ui", "vitest.ui.config.ts")
    inputs.files(npmAssemble)
    inputs.files(cloneSysmlWorker)
}

// the pages' screenshots are taken from live itself, on Playwright's Chromium unless SCREEN_TEST_CHROMIUM names one
val installScreenshotBrowser = tasks.register<NpmTask>("installScreenshotBrowser") {
    dependsOn(tasks.npmInstall)
    val localChromium = providers.environmentVariable("SCREEN_TEST_CHROMIUM")
    onlyIf {
        !localChromium.isPresent
    }
    // a Linux runner of CI lacks the libraries the browser needs, which Playwright installs there
    val dependencies = if (providers.environmentVariable("CI").isPresent && System.getProperty("os.name") == "Linux") {
        listOf("--with-deps")
    } else {
        emptyList()
    }
    npmCommand.set(
        listOf(
            "exec",
            "--",
            "playwright",
            "install",
        ) + dependencies + listOf("chromium"),
    )
}

val screenshots = tasks.register<NpmTask>("screenshots") {
    group = "build"

    configureSharedInputs()
    inputs.file(project.layout.projectDirectory.file("vitest.screenshots.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    // dist/ carries its build time, so the build's sources key the cache and the build itself only runs first
    inputs.dir(project.layout.projectDirectory.dir("public")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(project.layout.projectDirectory.file("index.html")).withPathSensitivity(PathSensitivity.RELATIVE)
    dependsOn(npmAssemble)
    inputs.files(cloneSysmlWorker).withPathSensitivity(PathSensitivity.RELATIVE)
    dependsOn(installScreenshotBrowser)

    npmCommand.set(listOf("run", "screenshots"))
    environment.put("TEST_BROWSER", "chromium")
    val screenshotsDirectory = project.layout.buildDirectory.dir("screenshots")
    outputs.dir(screenshotsDirectory)
    outputs.cacheIf { true }
    doFirst {
        screenshotsDirectory.get().asFile.deleteRecursively()
    }
}

configurations.consumable("screenshotsElements") {
    attributes(semantifyrArtifact("screenshots"))
    outgoing.artifact(project.layout.buildDirectory.dir("screenshots")) {
        builtBy(screenshots)
    }
}

tasks.register<NpmTask>("runDev") {
    group = "application"

    configureSharedInputs()

    npmCommand.set(listOf("run", "dev"))
}

tasks.register<NpmTask>("loadTest") {
    group = "verification"

    inputs.files(tasks.npmInstall)
    // a SysML v2 example goes to the server as the OXSTS the build compiles it to
    dependsOn(cloneTestModels)

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
