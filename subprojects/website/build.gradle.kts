/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.conventions.stampOnSuccess

plugins {
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

val apiDocs = configurations.dependencyScope("apiDocs")
val apiDocsFiles = configurations.resolvable("apiDocsFiles") {
    extendsFrom(apiDocs.get())
    attributes(semantifyrArtifact("api-docs"))
}

dependencies {
    editorCommon(project(":semantifyr-editor-common"))
    cliDistribution(project(":cli"))
    cliDistribution(project(":sysml-cli"))
    cliDistribution(project(":gamma-cli"))
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
    classpath = cliDistributionFiles.get().asFileTree.matching { include("${launcher.name}/lib/*.jar") }
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

val backendLimitations = tasks.register<JavaExec>("backendLimitations") {
    val outputFile = layout.buildDirectory.file("backend-reference/limitations.tsv")
    classpath = cliDistributionFiles.get().asFileTree.matching { include("*/lib/*.jar") }
    mainClass = "hu.bme.mit.semantifyr.portfolios.BackendLimitationsKt"
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dguice_bytecode_gen_option=DISABLED")
    args(outputFile.get().asFile.absolutePath)
    outputs.file(outputFile)
    outputs.cacheIf { true }
}

tasks.npmInstall {
    workingDir = rootProject.projectDir
}

val brandingDir = rootProject.layout.projectDirectory.dir("branding")
val importedBrandingDir = project.layout.projectDirectory.dir("static-imported-branding")

val cloneBrandingAssets = tasks.register<Sync>("cloneBrandingAssets") {
    from(brandingDir) {
        include("favicon.ico")
    }
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
    from(brandingDir) {
        include("semantifyr-logo-192.png")
        rename { "logo-192.png" }
        into("img")
    }
    from(brandingDir) {
        include("semantifyr-logo-512.png")
        rename { "logo-512.png" }
        into("img")
    }
    from(brandingDir) {
        include("semantifyr-logo-180.png")
        rename { "apple-touch-icon.png" }
        into("img")
    }
    into(importedBrandingDir)
}

val cloneApiDocs = tasks.register<Sync>("cloneApiDocs") {
    from(apiDocsFiles)
    into(layout.buildDirectory.dir("api-static/api"))
}

fun NpmTask.configureSharedInputs() {
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.dir(project.layout.projectDirectory.dir("static"))
    inputs.file(project.layout.projectDirectory.file("docusaurus.config.ts"))
    inputs.file(project.layout.projectDirectory.file("sidebars.ts"))
    inputs.file(project.layout.projectDirectory.file("babel.config.cts"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("eslint.config.mjs"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tsconfig.base.json"))
    inputs.file(rootProject.layout.projectDirectory.file("eslint.config.base.js"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.dir(rootProject.layout.projectDirectory.dir("oxsts-test-models/tutorial"))
    inputs.file(rootProject.layout.projectDirectory.file("oxsts-test-models/constructs/constructs.tsv"))
    inputs.dir(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/tutorial"))
    inputs.files(tasks.npmInstall)
    inputs.files(cloneBrandingAssets)
    inputs.files(editorCommonFiles)
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    configureSharedInputs()
    inputs.property("liveBackendUrl", providers.environmentVariable("SEMANTIFYR_LIVE_URL").orElse(""))
    inputs.files(cliReference)
    inputs.files(backendLimitations)

    npmCommand.set(listOf("run", "assemble"))
    inputs.files(cloneApiDocs)
    outputs.dir(project.layout.buildDirectory.dir("docusaurus"))
}

val npmCheck = tasks.register<NpmTask>("npmCheck") {
    configureSharedInputs()

    npmCommand.set(listOf("run", "check"))

    stampOnSuccess()
}

tasks {
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
