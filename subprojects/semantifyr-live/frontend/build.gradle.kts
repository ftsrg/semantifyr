/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.pnpm.task.PnpmTask
import com.github.gradle.node.variant.VariantComputer
import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.commitHash
import hu.bme.mit.semantifyr.gradle.prependToPath
import hu.bme.mit.semantifyr.gradle.readsPackage
import hu.bme.mit.semantifyr.gradle.registerPnpmCheck
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("hu.bme.mit.semantifyr.nodejs")
}

val editorCommon = artifactsOf("npm-package", ":semantifyr-editor-common")
val gammaCli = artifactsOf("application-distribution", ":gamma-cli")
val sysmlCli = artifactsOf("application-distribution", ":sysml-cli")
val webBranding = artifactsOf("web-branding", ":branding")
val sysmlWorker = artifactsOf("sysml-worker", ":sysml-wrapper")
val grammars = artifactsOf("textmate-grammar", ":oxsts.lang", ":gamma.lang")
val languageConfiguration = artifactsOf("language-configuration", ":semantifyr-vscode")

val importedDirectory = layout.buildDirectory.dir("imported").get()
val importedSnippetsDirectory = importedDirectory.dir("snippets")
val importedVscodeDirectory = importedDirectory.dir("vscode")
val publicDirectory = importedDirectory.dir("public")

val gammaExamples = fileTree(isolated.rootProject.projectDirectory.dir("subprojects/frontends/gamma/models/examples")) {
    include("*.gamma")
}
val sysmlExamples = fileTree(isolated.rootProject.projectDirectory.dir("subprojects/frontends/sysml/models/examples")) {
    include("*.sysml")
    exclude("Verification.sysml")
}
val oxstsExamples = files(
    listOf(
        "subprojects/website/models/examples",
        "subprojects/website/models/tutorial",
    ).map {
        fileTree(isolated.rootProject.projectDirectory.dir(it)) {
            include("*.semantifyr")
        }
    },
)

val compiledExamplesDirectory = layout.buildDirectory.dir("compiled-examples").get()

fun compileExamples(
    language: String,
    examples: FileCollection,
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
    val compiles = examples.files.sorted().map { example ->
        val name = example.nameWithoutExtension
        val targetDirectory = compiledExamplesDirectory.dir("$language/$name")
        val targetFile = targetDirectory.file("$name.semantifyr")
        tasks.register<Exec>("compile${taskLanguage}Example_$name") {
            inputs.files(
                fileTree(example.parentFile) {
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
            args("compile", example.relativeTo(root.asFile).invariantSeparatorsPath, "-o", targetFile.asFile.relativeTo(root.asFile).invariantSeparatorsPath)
            doFirst {
                executable = startScripts.files.single {
                    (it.extension == "bat") == windows
                }.absolutePath
            }
            configuration()
        }
    }
    return files(compiles).asFileTree.matching {
        include("*.semantifyr")
    }
}

fun Exec.runsWithNode() {
    val nodeBinDirectory = VariantComputer().computeNodeBinDir(node.resolvedNodeDir, node.resolvedPlatform)
    dependsOn(tasks.nodeSetup)
    inputs.property("nodeVersion", node.version)
    doFirst {
        prependToPath(nodeBinDirectory.get().asFile)
    }
}

val gammaCompiledExamples = compileExamples("gamma", gammaExamples, gammaCli, "*.gamma", "*.gcd")

val sysmlCompiledExamples = compileExamples("sysml", sysmlExamples, sysmlCli, "**/*.sysml", "**/*.kerml") {
    runsWithNode()
}

val cloneGammaExamples = tasks.register<Sync>("cloneGammaExamples") {
    from(gammaExamples)
    from(gammaCompiledExamples)
    into(importedSnippetsDirectory.dir("gamma"))
}

val cloneSysmlExamples = tasks.register<Sync>("cloneSysmlExamples") {
    from(sysmlExamples)
    from(sysmlCompiledExamples)
    into(importedSnippetsDirectory.dir("sysml"))
}

val cloneOxstsExamples = tasks.register<Sync>("cloneOxstsExamples") {
    from(oxstsExamples)
    into(importedSnippetsDirectory.dir("oxsts"))
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

val cloneBrandingAssets = tasks.register<Sync>("cloneBrandingAssets") {
    from(webBranding)
    into(publicDirectory)
}

fun PnpmTask.configureSharedInputs() {
    inputs.files(editorCommon).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGammaExamples).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneSysmlExamples).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneOxstsExamples).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneVscodeLanguageAssets).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneBrandingAssets).withPathSensitivity(PathSensitivity.RELATIVE)
}

fun PnpmTask.passesVersionAndCommit() {
    environment.put("SEMANTIFYR_VERSION", project.version.toString())
    environment.put("SEMANTIFYR_COMMIT", commitHash())
}

val pnpmAssemble = tasks.register<PnpmTask>("pnpmAssemble") {
    group = LifecycleBasePlugin.BUILD_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.file(layout.projectDirectory.file("vite.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("index.html")).withPathSensitivity(PathSensitivity.RELATIVE)
    passesVersionAndCommit()

    pnpmCommand = listOf("run", "assemble")
    outputs.dir(layout.projectDirectory.dir("dist"))
    outputs.cacheIf {
        true
    }
}

configurations.consumable("webDistributionElements") {
    attributes(semantifyrArtifact("web-distribution"))
    outgoing.artifact(layout.projectDirectory.dir("dist")) {
        builtBy(pnpmAssemble)
    }
}

registerPnpmCheck {
    configureSharedInputs()
    inputs.file(layout.projectDirectory.file("index.html")).withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.register<PnpmTask>("integrationTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.file(layout.projectDirectory.file("vite.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)

    pnpmCommand = listOf("run", "test:integration")

    outputs.file(layout.buildDirectory.file("test-results/integration.xml"))
    outputs.cacheIf {
        true
    }
}

fun PnpmTask.runsInBrowser(
    browser: String,
    suite: String,
    configuration: String,
) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.file(layout.projectDirectory.file(configuration)).withPathSensitivity(PathSensitivity.RELATIVE)

    val report = "build/test-results/$suite-$browser.xml"
    pnpmCommand = listOf("run", suite, "--", "--reporter=default", "--reporter=junit", "--outputFile.junit=$report")
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

    val screenTest = tasks.register<PnpmTask>("screenTest$taskSuffix") {
        runsInBrowser(browser, "screens", "vitest.screen.config.ts")
        val screensDirectory = layout.buildDirectory.dir("screens/$browser")
        outputs.dir(screensDirectory)
        doFirst {
            screensDirectory.get().asFile.deleteRecursively()
        }
    }

    val applicationTest = tasks.register<PnpmTask>("applicationTest$taskSuffix") {
        runsInBrowser(browser, "ui", "vitest.ui.config.ts")
        inputs.files(pnpmAssemble).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(cloneSysmlWorker).withPathSensitivity(PathSensitivity.RELATIVE)
    }

    tasks.register("uiTest$taskSuffix") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(screenTest, applicationTest)
    }
}

tasks.register<PnpmTask>("runDev") {
    group = ApplicationPlugin.APPLICATION_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.file(layout.projectDirectory.file("vite.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    passesVersionAndCommit()

    pnpmCommand = listOf("run", "dev")
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(pnpmAssemble)
}

tasks.register("continuousIntegration") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.named("integrationTest"))
}

tasks.named<Delete>("clean") {
    delete("dist")
}
