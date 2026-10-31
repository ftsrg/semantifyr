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
    cliDistribution(project(":semantifyr-cli"))
    if (providers.gradleProperty("website.apiDocs").getOrElse("false").toBoolean()) {
        apiDocs(project(":"))
    }
}

val cliReferenceDir = layout.buildDirectory.dir("cli-reference")

fun cliReferenceTask(name: String, vararg arguments: String) = tasks.register<JavaExec>("cliReference_$name") {
    val outputFile = cliReferenceDir.map { it.file("$name.txt") }
    classpath = cliDistributionFiles.get().asFileTree.matching { include("*/lib/*.jar") }
    mainClass = "hu.bme.mit.semantifyr.cli.SemantifyrCliKt"
    args(*arguments)
    outputs.file(outputFile)
    outputs.cacheIf { true }
    doFirst {
        standardOutput = outputFile.get().asFile.outputStream()
    }
    doLast {
        standardOutput.close()
    }
}

val cliReference = listOf(
    cliReferenceTask("help", "--help"),
    cliReferenceTask("verify", "verify", "--help"),
    cliReferenceTask("compile", "compile", "--help"),
    cliReferenceTask("list", "list", "--help"),
    cliReferenceTask("portfolios-help", "portfolios", "--help"),
    cliReferenceTask("portfolios", "portfolios"),
)

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
    inputs.files(tasks.npmInstall)
    inputs.files(cloneBrandingAssets)
    inputs.files(editorCommonFiles)
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    configureSharedInputs()
    inputs.property("liveBackendUrl", providers.environmentVariable("SEMANTIFYR_LIVE_URL").orElse(""))
    inputs.files(cliReference)

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
