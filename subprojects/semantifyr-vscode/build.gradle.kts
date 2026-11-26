/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.readsWorkspace
import hu.bme.mit.semantifyr.gradle.registerRenderPictures
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.nodejs")
}

val editorCommon = artifactsOf("npm-package", ":semantifyr-editor-common")
val languageServers = artifactsOf("application-distribution", ":oxsts.lang.ide", ":gamma.lang.ide")
val grammars = artifactsOf("textmate-grammar", ":oxsts.lang", ":gamma.lang")
val thetaDirectory = artifactsOf("theta-xsts-cli", ":theta-executor")

val renderIcons = registerRenderPictures(
    "renderIcons",
    layout.projectDirectory.dir("icons"),
    listOf(
        "icon.png=logo@128",
        "semantifyr-file-icon.png=logo@128+104",
        "gamma-file-icon.png=gamma@128+104",
    ),
)

val cloneDistribution = tasks.register<Sync>("cloneDistribution") {
    from(languageServers)

    into("bin")
}

val cloneGrammars = tasks.register<Sync>("cloneGrammars") {
    from(grammars)

    into("syntaxes")
}

fun NpmTask.configureSharedInputs() {
    readsWorkspace()
    inputs.dir(layout.projectDirectory.dir("src")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("esbuild.mjs")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("vitest.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("eslint.config.js")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("tsconfig.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(editorCommon).withPathSensitivity(PathSensitivity.RELATIVE)
}

val npmCheck = tasks.register<NpmTask>("npmCheck") {
    configureSharedInputs()

    npmCommand = listOf("run", "check")

    outputs.file(layout.buildDirectory.file("tsconfig.tsbuildinfo"))
    outputs.file(layout.buildDirectory.file("eslint.json"))
    outputs.file(layout.buildDirectory.file("test-results/unit.xml"))
    outputs.cacheIf {
        true
    }
}

val vsixFile = layout.buildDirectory.file("semantifyr-$version.vsix")

val bundleExtension = tasks.register<NpmTask>("bundleExtension") {
    val version = project.version.toString()
    inputs.property("version", version)

    configureSharedInputs()
    inputs.files(cloneDistribution).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGrammars).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("language-configuration.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(renderIcons).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("README.md")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("CHANGELOG.md")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("LICENSE")).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "bundle", "--", version, "--no-git-tag-version", "--no-update-package-json")

    outputs.file(vsixFile)
    outputs.cacheIf {
        true
    }
}

tasks.register<NpmTask>("endToEndTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    configureSharedInputs()
    inputs.files(bundleExtension)
    inputs.files(thetaDirectory)
    inputs.file(isolated.rootProject.projectDirectory.file("oxsts-test-models/smoke/smoke.semantifyr"))

    npmCommand = listOf("run", "test:end-to-end")
    environment.put("SEMANTIFYR_VSIX", vsixFile.map {
        it.asFile.absolutePath
    })
    environment.put("PATH", thetaDirectory.elements.zip(providers.environmentVariable("PATH")) { directories, path ->
        "${directories.single().asFile.absolutePath}${File.pathSeparator}$path"
    })
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(bundleExtension)
}

tasks.named("check") {
    dependsOn(npmCheck)
}

tasks.named<Delete>("clean") {
    delete("dist", "icons", "bin", "syntaxes")
}

configurations.consumable("vscodeExtensionElements") {
    attributes(semantifyrArtifact("vscode-extension"))
    outgoing.artifact(vsixFile) {
        builtBy(bundleExtension)
    }
}

configurations.consumable("languageConfigurationElements") {
    attributes(semantifyrArtifact("language-configuration"))
    outgoing.artifact(layout.projectDirectory.file("language-configuration.json"))
}

configurations.consumable("vscodeManifestElements") {
    attributes(semantifyrArtifact("vscode-manifest"))
    outgoing.artifact(layout.projectDirectory.file("package.json"))
}
