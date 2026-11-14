/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import groovy.json.JsonSlurper
import hu.bme.mit.semantifyr.gradle.conventions.javaRuntimeLibrary
import hu.bme.mit.semantifyr.gradle.conventions.readsServedExamples
import hu.bme.mit.semantifyr.gradle.conventions.registerRenderPictures
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.conventions.servedExampleSources
import hu.bme.mit.semantifyr.gradle.conventions.stampOnSuccess

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.nodejs")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val generate = sourceSets.create("generate")

val editorCommon = configurations.dependencyScope("editorCommon")
val editorCommonFiles = configurations.resolvable("editorCommonFiles") {
    extendsFrom(editorCommon.get())
    attributes(semantifyrArtifact("npm-package"))
}

val cliDistribution = configurations.dependencyScope("cliDistribution")
val cliDistributionFiles = configurations.resolvable("cliDistributionFiles") {
    extendsFrom(cliDistribution.get())
    attributes(semantifyrArtifact("application-distribution"))
}

val sysmlConstructs = configurations.dependencyScope("sysmlConstructs")
val sysmlConstructsFiles = configurations.resolvable("sysmlConstructsFiles") {
    extendsFrom(sysmlConstructs.get())
    attributes(semantifyrArtifact("sysml-constructs"))
}

val sysmlWorker = configurations.dependencyScope("sysmlWorker")
val sysmlWorkerFiles = configurations.resolvable("sysmlWorkerFiles") {
    extendsFrom(sysmlWorker.get())
    attributes(semantifyrArtifact("sysml-worker"))
}

// the modules whose texts a launcher can print, which the terms test reads where they are raised
val launcherModules = configurations.dependencyScope("launcherModules")
val launcherModulesGraph = configurations.resolvable("launcherModulesGraph") {
    extendsFrom(launcherModules.get())
    attributes(javaRuntimeLibrary())
}

val liveScreenshots = configurations.dependencyScope("liveScreenshots")
val liveScreenshotsFiles = configurations.resolvable("liveScreenshotsFiles") {
    extendsFrom(liveScreenshots.get())
    attributes(semantifyrArtifact("screenshots"))
}

val apiDocs = configurations.dependencyScope("apiDocs")
val apiDocsFiles = configurations.resolvable("apiDocsFiles") {
    extendsFrom(apiDocs.get())
    attributes(semantifyrArtifact("api-docs"))
}

dependencies {
    add(generate.implementationConfigurationName, project(":cli-common"))
    add(generate.implementationConfigurationName, project(":verifier"))
    add(generate.implementationConfigurationName, libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(project(":oxsts.lang"))
    testImplementation(testFixtures(project(":oxsts.lang")))

    editorCommon(project(":semantifyr-editor-common"))
    launcherModules(project(":semantifyr-cli"))
    launcherModules(project(":sysml-cli"))
    launcherModules(project(":gamma-cli"))
    cliDistribution(project(":semantifyr-cli"))
    cliDistribution(project(":sysml-cli"))
    cliDistribution(project(":gamma-cli"))
    sysmlConstructs(project(":sysml-wrapper"))
    liveScreenshots(project(":semantifyr-live-frontend"))
    sysmlWorker(project(":sysml-wrapper"))
    if (providers.gradleProperty("website.apiDocs").getOrElse("false").toBoolean()) {
        apiDocs(project(":"))
    }
}

// the directories the pages import sources from, which the site's configuration and the test read too
val sourceAliasesFile = layout.projectDirectory.file("source-aliases.json")

@Suppress("UNCHECKED_CAST")
val sourceAliasDirectories = (JsonSlurper().parse(sourceAliasesFile.asFile) as Map<String, String>).values.map {
    layout.projectDirectory.dir(it)
}

// the files outside the site that link into it, which the site links test reads
val linkingFilesFile = layout.projectDirectory.file("linking-files.json")

@Suppress("UNCHECKED_CAST")
val linkingFiles = (JsonSlurper().parse(linkingFilesFile.asFile) as List<String>).map {
    rootProject.layout.projectDirectory.file(it)
}

val cliReferenceDir = layout.buildDirectory.dir("cli-reference")

class Launcher(val name: String, val mainClass: String)

val semantifyr = Launcher("semantifyr", "hu.bme.mit.semantifyr.cli.SemantifyrCliKt")
val semantifyrSysml = Launcher("semantifyr-sysml", "hu.bme.mit.semantifyr.frontends.sysml.cli.SemantifyrSysMLCliKt")
val semantifyrGamma = Launcher("semantifyr-gamma", "hu.bme.mit.semantifyr.frontends.gamma.cli.SemantifyrGammaCliKt")

fun cliReferenceTask(name: String, launcher: Launcher, vararg arguments: String) = tasks.register<JavaExec>("cliReference_$name") {
    val outputFile = cliReferenceDir.map { it.file("$name.txt") }
    classpath = cliDistributionFiles.get().asFileTree.matching {
        include("${launcher.name}/lib/*.jar")
    }
    mainClass = launcher.mainClass
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dguice_bytecode_gen_option=DISABLED")
    args(*arguments)
    outputs.file(outputFile)
    outputs.cacheIf { true }
    doFirst {
        standardOutput = outputFile.get().asFile.outputStream()
    }
    doLast {
        standardOutput.close()
        val file = outputFile.get().asFile
        file.writeText(file.readText().replace("\r\n", "\n"))
    }
}

val cliReference = listOf(
    cliReferenceTask("help", semantifyr, "--help"),
    cliReferenceTask("verify", semantifyr, "verify", "--help"),
    cliReferenceTask("compile", semantifyr, "compile", "--help"),
    cliReferenceTask("list", semantifyr, "list", "--help"),
    cliReferenceTask("metrics", semantifyr, "metrics", "--help"),
    cliReferenceTask("portfolios-help", semantifyr, "portfolios", "--help"),
    cliReferenceTask("portfolios", semantifyr, "portfolios"),
    cliReferenceTask("sysml-help", semantifyrSysml, "--help"),
    cliReferenceTask("sysml-verify", semantifyrSysml, "verify", "--help"),
    cliReferenceTask("sysml-compile", semantifyrSysml, "compile", "--help"),
    cliReferenceTask("gamma-help", semantifyrGamma, "--help"),
    cliReferenceTask("gamma-verify", semantifyrGamma, "verify", "--help"),
    cliReferenceTask("gamma-compile", semantifyrGamma, "compile", "--help"),
)

val generatedReferenceDir = layout.buildDirectory.dir("generated-reference")

// written for the alias @generated-reference: what the pages show of a type, generated from it
fun generatedReferenceTask(name: String) = tasks.register<JavaExec>("generatedReference_$name") {
    val outputFile = generatedReferenceDir.map { it.file("$name.json") }
    classpath = generate.runtimeClasspath
    mainClass = "hu.bme.mit.semantifyr.website.GenerateReferenceKt"
    args(name)
    outputs.file(outputFile)
    outputs.cacheIf { true }
    doFirst {
        standardOutput = outputFile.get().asFile.outputStream()
    }
    doLast {
        standardOutput.close()
    }
}

val generatedReference = listOf(
    generatedReferenceTask("exit-codes"),
    generatedReferenceTask("report-fields"),
    generatedReferenceTask("verdicts"),
    generatedReferenceTask("trace-fields"),
    generatedReferenceTask("witness-fields"),
)

tasks.npmInstall {
    workingDir = rootProject.projectDir
}

val brandingDir = rootProject.layout.projectDirectory.dir("branding")
val importedBrandingDir = project.layout.projectDirectory.dir("static-imported-branding")

val renderBrandingPictures = registerRenderPictures(
    "renderBrandingPictures",
    layout.buildDirectory.dir("branding").get(),
    listOf(
        "favicon.ico=semantifyr-logo@16,32,48",
        "img/logo-192.png=semantifyr-logo@192",
        "img/logo-512.png=semantifyr-logo@512",
        "img/apple-touch-icon.png=semantifyr-logo@180",
        "img/social-card.png=social-card@1200",
    ),
)

val cloneBrandingAssets = tasks.register<Sync>("cloneBrandingAssets") {
    from(renderBrandingPictures)
    from(brandingDir) {
        include("semantifyr-logo.svg")
        rename { "logo.svg" }
        into("img")
    }
    from(brandingDir) {
        include("semantifyr-full-light.svg")
        rename { "logo-full-light.svg" }
        into("img")
    }
    from(brandingDir) {
        include("semantifyr-full-dark.svg")
        rename { "logo-full-dark.svg" }
        into("img")
    }
    into(importedBrandingDir)
}

// written for the alias @sysml-constructs
val cloneSysmlConstructs = tasks.register<Sync>("cloneSysmlConstructs") {
    from(sysmlConstructsFiles)
    into(layout.buildDirectory.dir("sysml-constructs"))
}

// written for the alias @grammars: the grammars live's editor loads
val cloneGrammars = tasks.register<Sync>("cloneGrammars") {
    from(rootProject.layout.projectDirectory.dir("subprojects/semantifyr-vscode/syntaxes")) {
        include("*.tmLanguage.json")
    }
    from(sysmlWorkerFiles) {
        include("sysml.tmLanguage.json")
    }
    into(layout.buildDirectory.dir("grammars"))
}

// the static directory build/live-screenshots: live's screenshots, which its build takes
val cloneScreenshots = tasks.register<Sync>("cloneScreenshots") {
    from(liveScreenshotsFiles)
    into(layout.buildDirectory.dir("live-screenshots/img/screenshots"))
}

val cloneApiDocs = tasks.register<Sync>("cloneApiDocs") {
    from(apiDocsFiles)
    into(layout.buildDirectory.dir("api-static/api"))
}

fun NpmTask.configureSharedInputs() {
    inputs.dir(project.layout.projectDirectory.dir("src")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(project.layout.projectDirectory.file("docusaurus.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(project.layout.projectDirectory.file("sidebars.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(project.layout.projectDirectory.file("babel.config.cts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(project.layout.projectDirectory.file("tsconfig.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(project.layout.projectDirectory.file("eslint.config.mjs")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(project.layout.projectDirectory.file("vitest.config.mts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(project.layout.projectDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("tsconfig.base.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("eslint.config.base.js")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("gradle.properties")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file(".nvmrc")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("deploy/live/env.example")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("subprojects/semantifyr-vscode/package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(sourceAliasesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sourceAliasDirectories).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(servedExampleSources()).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGrammars).withPathSensitivity(PathSensitivity.RELATIVE)
    // the builds keep their own caches under node_modules/.cache, which say nothing of the result
    dependsOn(tasks.npmInstall)
    val installed = tasks.npmInstall.map {
        it.outputs.files.asFileTree.matching {
            exclude("**/.cache/**")
        }
    }
    inputs.files(installed).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneBrandingAssets).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(editorCommonFiles).withPathSensitivity(PathSensitivity.RELATIVE)
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    configureSharedInputs()
    inputs.property("liveBackendUrl", providers.environmentVariable("SEMANTIFYR_LIVE_URL").orElse(""))
    // the deploying workflow names the commit, and its tag when it is a release; any other build describes the development branch
    inputs.property("siteCommit", providers.environmentVariable("SEMANTIFYR_SITE_COMMIT").orElse(""))
    inputs.property("siteTag", providers.environmentVariable("SEMANTIFYR_SITE_TAG").orElse(""))
    inputs.files(cliReference).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(generatedReference).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneSysmlConstructs).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneScreenshots).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand.set(listOf("run", "assemble"))
    inputs.files(cloneApiDocs).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(project.layout.buildDirectory.dir("docusaurus"))
    outputs.cacheIf { true }
}

val npmCheck = tasks.register<NpmTask>("npmCheck") {
    configureSharedInputs()
    // the terms test reads live's texts too
    inputs.files(
        rootProject.fileTree("subprojects/semantifyr-live/frontend/src") {
            exclude("snippets/**", "vscode/**", "__*__/**")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("subprojects/semantifyr-live/frontend/index.html")).withPathSensitivity(PathSensitivity.RELATIVE)
    // the site links test reads the files that link into the site, which the list names
    inputs.file(linkingFilesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(linkingFiles).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand.set(listOf("run", "check"))

    stampOnSuccess()
    outputs.cacheIf { true }
}

// written for the alias @phases
val phasesModel = rootProject.layout.projectDirectory.file("oxsts-test-models/phases/room.oxsts")
val phasesDir = layout.buildDirectory.dir("phases")

val phases = tasks.register<JavaExec>("phases") {
    classpath = cliDistributionFiles.get().asFileTree.matching {
        include("${semantifyr.name}/lib/*.jar")
    }
    mainClass = semantifyr.mainClass
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dguice_bytecode_gen_option=DISABLED")
    inputs.file(phasesModel).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(phasesDir)
    outputs.cacheIf { true }
    val outputDirectory = phasesDir.get().asFile
    // relative paths keep the arguments, and so the cache key, the same in every checkout
    workingDir = outputDirectory
    args(
        "compile",
        "--artifacts=debug",
        "--disable-artifact=mapping",
        "--artifact-directory=.",
        "--output=compiled.oxsts",
        phasesModel.asFile.relativeTo(outputDirectory).invariantSeparatorsPath,
    )
    doFirst {
        outputDirectory.deleteRecursively()
        outputDirectory.mkdirs()
    }
    doLast {
        outputDirectory.walkTopDown().filter {
            it.isFile
        }.forEach {
            it.writeText(it.readText().replace("\r\n", "\n"))
        }
    }
}

npmAssemble.configure {
    inputs.files(phases).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(phasesModel).withPathSensitivity(PathSensitivity.RELATIVE)
}

// the instance tree's test reads the model
npmCheck.configure {
    inputs.file(phasesModel).withPropertyName("phasesModel").withPathSensitivity(PathSensitivity.RELATIVE)
}

val projectDirectories = rootProject.allprojects.associate {
    it.path to it.projectDir
}

val launcherSourceDirectories = launcherModulesGraph.flatMap {
    it.incoming.resolutionResult.rootComponent
}.map { root ->
    val reached = mutableSetOf<ResolvedComponentResult>()
    val pending = ArrayDeque(listOf(root))
    while (pending.isNotEmpty()) {
        pending.removeFirst().dependencies.filterIsInstance<ResolvedDependencyResult>().forEach {
            if (reached.add(it.selected)) {
                pending.add(it.selected)
            }
        }
    }
    reached.mapNotNull {
        (it.id as? ProjectComponentIdentifier)?.projectPath
    }.distinct().sorted().flatMap { path ->
        listOf("kotlin", "java").map {
            projectDirectories.getValue(path).resolve("src/main/$it")
        }
    }.filter {
        it.isDirectory
    }
}

class RelativePathsArgument(
    private val name: String,
    private val module: File,
    private val paths: Provider<List<File>>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        val relative = paths.get().map {
            it.relativeTo(module).invariantSeparatorsPath
        }
        return listOf("-D$name=${relative.joinToString(",")}")
    }
}

tasks {
    test {
        // the terms test reads the terms, every text a launcher can print and the details of the SysML v2 constructs
        inputs.file(rootProject.layout.projectDirectory.file("subprojects/semantifyr-editor-common/src/terms/terms.json"))
            .withPropertyName("terms")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(launcherSourceDirectories)
            .withPropertyName("launcherSources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(cloneSysmlConstructs)
            .withPropertyName("sysmlConstructs")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        jvmArgumentProviders.add(RelativePathsArgument("semantifyr.launcherSources", projectDir, launcherSourceDirectories))
        inputs.dir(project.layout.projectDirectory.dir("src/docs"))
            .withPropertyName("documentation")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        // the completeness test reads the models the pages import through the source aliases
        inputs.file(sourceAliasesFile)
            .withPropertyName("sourceAliases")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(sourceAliasDirectories)
            .withPropertyName("importedModels")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        // and the served examples the pages embed
        readsServedExamples()
        inputs.files(servedExampleSources())
            .withPropertyName("servedExamples")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }

    assemble {
        dependsOn(npmAssemble)
    }

    check {
        dependsOn(npmCheck)
    }

    clean {
        delete(".docusaurus")
        delete("build")
        delete(importedBrandingDir)
    }
}
