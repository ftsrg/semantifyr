/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.conventions.registerRenderPictures
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.conventions.stampOnSuccess

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.nodejs")
}

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

val apiDocs = configurations.dependencyScope("apiDocs")
val apiDocsFiles = configurations.resolvable("apiDocsFiles") {
    extendsFrom(apiDocs.get())
    attributes(semantifyrArtifact("api-docs"))
}

dependencies {
    testImplementation(project(":oxsts.lang"))
    testImplementation(testFixtures(project(":oxsts.lang")))

    editorCommon(project(":semantifyr-editor-common"))
    cliDistribution(project(":semantifyr-cli"))
    cliDistribution(project(":sysml-cli"))
    cliDistribution(project(":gamma-cli"))
    sysmlConstructs(project(":sysml-wrapper"))
    if (providers.gradleProperty("website.apiDocs").getOrElse("false").toBoolean()) {
        apiDocs(project(":"))
    }
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

val cloneApiDocs = tasks.register<Sync>("cloneApiDocs") {
    from(apiDocsFiles)
    into(layout.buildDirectory.dir("api-static/api"))
}

fun NpmTask.configureSharedInputs() {
    inputs.dir(project.layout.projectDirectory.dir("src")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(project.layout.projectDirectory.dir("static")).withPathSensitivity(PathSensitivity.RELATIVE)
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
    inputs.dir(rootProject.layout.projectDirectory.dir("oxsts-test-models/tutorial")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("subprojects/oxsts.lang/src/main/resources/hu/bme/mit/semantifyr/oxsts/lang/library")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("oxsts-test-models/constructs/constructs.tsv")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/tutorial")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("subprojects/semantifyr-vscode/syntaxes/oxsts.tmLanguage.json")).withPathSensitivity(PathSensitivity.RELATIVE)
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
    inputs.files(cliReference).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneSysmlConstructs).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand.set(listOf("run", "assemble"))
    inputs.files(cloneApiDocs).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(project.layout.buildDirectory.dir("docusaurus"))
    outputs.cacheIf { true }
}

val npmCheck = tasks.register<NpmTask>("npmCheck") {
    configureSharedInputs()

    npmCommand.set(listOf("run", "check"))

    stampOnSuccess()
    outputs.cacheIf { true }
}

tasks {
    test {
        inputs.dir(project.layout.projectDirectory.dir("src/docs"))
            .withPropertyName("documentation")
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
