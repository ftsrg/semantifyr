/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.variant.VariantComputer
import hu.bme.mit.semantifyr.gradle.ExampleDescriptions
import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.commitHash
import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.readsPackage
import hu.bme.mit.semantifyr.gradle.registerBrandingAssets
import hu.bme.mit.semantifyr.gradle.registerNpmCheck
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.servedExamples
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.file.RegularFile
import java.io.File

plugins {
    id("hu.bme.mit.semantifyr.nodejs")
}

val editorCommon = artifactsOf("npm-package", ":semantifyr-editor-common")
val gammaCli = artifactsOf("application-distribution", ":gamma-cli")
val sysmlCli = artifactsOf("application-distribution", ":sysml-cli")
val sysmlWorker = artifactsOf("sysml-worker", ":sysml-wrapper")
val grammars = artifactsOf("textmate-grammar", ":oxsts.lang", ":gamma.lang")
val languageConfiguration = artifactsOf("language-configuration", ":semantifyr-vscode")

val importedDirectory = layout.buildDirectory.dir("imported").get()
val importedSnippetsDirectory = importedDirectory.dir("snippets")
val importedVscodeDirectory = importedDirectory.dir("vscode")
val publicDirectory = importedDirectory.dir("public")

fun servedFiles(language: String): List<RegularFile> {
    return servedExamples(language).map {
        isolated.rootProject.projectDirectory.file(it.file)
    }
}

val compiledExamplesDirectory = layout.buildDirectory.dir("compiled-examples").get()

fun compileServedExamples(
    language: String,
    distribution: FileCollection,
    vararg workspacePatterns: String,
    configuration: Exec.() -> Unit = {},
): FileCollection {
    val root = isolated.rootProject.projectDirectory
    val taskLanguage = language.replaceFirstChar {
        it.uppercase()
    }
    val windows = Os.isFamily(Os.FAMILY_WINDOWS)
    val startScripts = distribution.asFileTree.matching {
        include("*/bin/*")
    }
    val compiles = servedExamples(language).filter {
        !it.hidden
    }.map { example ->
        val targetDirectory = compiledExamplesDirectory.dir("$language/${example.name}")
        val targetFile = targetDirectory.file("${example.name}.$oxstsFileExtension")
        tasks.register<Exec>("compile${taskLanguage}Example_${example.name}") {
            inputs.files(
                fileTree(root.dir(example.file.substringBeforeLast('/'))) {
                    include(*workspacePatterns)
                },
            ).withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.files(startScripts).withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.files(
                distribution.asFileTree.matching {
                    include("*/lib/*.jar")
                },
            ).withNormalizer(ClasspathNormalizer::class.java)
            outputs.dir(targetDirectory)
            outputs.cacheIf {
                true
            }
            workingDir = root.asFile
            args("compile", example.file, "-o", targetFile.asFile.relativeTo(root.asFile).invariantSeparatorsPath)
            doFirst {
                executable = startScripts.files.single {
                    (it.extension == "bat") == windows
                }.absolutePath
            }
            configuration()
        }
    }
    return files(compiles).asFileTree.matching {
        include("*.$oxstsFileExtension")
    }
}

fun Exec.runsWithNode() {
    val nodeBinDirectory = VariantComputer().computeNodeBinDir(node.resolvedNodeDir, node.resolvedPlatform)
    dependsOn(tasks.nodeSetup)
    inputs.property("nodeVersion", node.version)
    doFirst {
        // Windows names the variable Path
        val pathVariable = environment.keys.firstOrNull {
            it.equals("PATH", ignoreCase = true)
        } ?: "PATH"
        environment(pathVariable, "${nodeBinDirectory.get().asFile.absolutePath}${File.pathSeparator}${environment[pathVariable]}")
    }
}

val gammaCompiledExamples = compileServedExamples("gamma", gammaCli, "*.gamma", "*.gcd")

val sysmlCompiledExamples = compileServedExamples("sysml", sysmlCli, "**/*.sysml", "**/*.kerml") {
    runsWithNode()
}

val cloneGammaExamples = tasks.register<Sync>("cloneGammaExamples") {
    from(servedFiles("gamma"))
    from(gammaCompiledExamples)
    into(importedSnippetsDirectory.dir("gamma"))
}

val cloneSysmlExamples = tasks.register<Sync>("cloneSysmlExamples") {
    from(servedFiles("sysml"))
    from(sysmlCompiledExamples)
    into(importedSnippetsDirectory.dir("sysml"))
}

val cloneOxstsExamples = tasks.register<Sync>("cloneOxstsExamples") {
    from(servedFiles("oxsts"))
    into(importedSnippetsDirectory.dir("oxsts"))
}

val exampleDescriptions = tasks.register<ExampleDescriptions>("exampleDescriptions") {
    models.from(servedFiles("sysml"), servedFiles("gamma"), servedFiles("oxsts"))
    outputDirectory = importedSnippetsDirectory.dir("descriptions")
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
    inputs.files(editorCommon).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGammaExamples).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneSysmlExamples).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneOxstsExamples).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(exampleDescriptions).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneVscodeLanguageAssets).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneBrandingAssets).withPathSensitivity(PathSensitivity.RELATIVE)
}

fun NpmTask.passesVersionAndCommit() {
    environment.put("SEMANTIFYR_VERSION", project.version.toString())
    environment.put("SEMANTIFYR_COMMIT", commitHash())
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    group = LifecycleBasePlugin.BUILD_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.file(layout.projectDirectory.file("vite.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("index.html")).withPathSensitivity(PathSensitivity.RELATIVE)
    passesVersionAndCommit()

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

registerNpmCheck {
    configureSharedInputs()
    // an example's page is tested against live's head and the rights the REUSE file gives
    inputs.file(layout.projectDirectory.file("index.html")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(isolated.rootProject.projectDirectory.file("REUSE.toml")).withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.register<NpmTask>("integrationTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.file(layout.projectDirectory.file("vite.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "test:integration")

    outputs.file(layout.buildDirectory.file("test-results/integration.xml"))
    outputs.cacheIf {
        true
    }
}

fun NpmTask.runsInBrowser(
    browser: String,
    suite: String,
    configuration: String,
) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    readsPackage()
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
        val screensDirectory = layout.buildDirectory.dir("screens/$browser")
        outputs.dir(screensDirectory)
        doFirst {
            screensDirectory.get().asFile.deleteRecursively()
        }
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

    readsPackage()
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

    readsPackage()
    configureSharedInputs()
    inputs.file(layout.projectDirectory.file("vite.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    passesVersionAndCommit()

    npmCommand = listOf("run", "dev")
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(npmAssemble)
}

tasks.register("continuousIntegration") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.named("integrationTest"))
}

tasks.named<Delete>("clean") {
    delete("dist")
}
