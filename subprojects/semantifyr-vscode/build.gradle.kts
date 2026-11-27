/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.readsPackage
import hu.bme.mit.semantifyr.gradle.registerNpmCheck
import hu.bme.mit.semantifyr.gradle.registerRenderPictures
import hu.bme.mit.semantifyr.gradle.runsWithTheta
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.nodejs")
}

val editorCommon = artifactsOf("npm-package", ":semantifyr-editor-common")
val languageServers = artifactsOf("application-distribution", ":oxsts.lang.ide", ":gamma.lang.ide")
val grammars = artifactsOf("textmate-grammar", ":oxsts.lang", ":gamma.lang")

val extensionVersion = version.toString()

val renderIcons = registerRenderPictures(
    "renderIcons",
    provider {
        layout.projectDirectory.dir("icons")
    },
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

val cloneLicense = tasks.register("cloneLicense") {
    val source = isolated.rootProject.projectDirectory.file("LICENSE")
    val target = layout.projectDirectory.file("LICENSE")
    inputs.file(source).withPathSensitivity(PathSensitivity.NONE)
    outputs.file(target)
    outputs.cacheIf {
        true
    }
    doLast {
        source.asFile.copyTo(target.asFile, overwrite = true)
    }
}

fun NpmTask.configureSharedInputs() {
    inputs.file(layout.projectDirectory.file("esbuild.mjs")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(editorCommon).withPathSensitivity(PathSensitivity.RELATIVE)
}

registerNpmCheck {
    configureSharedInputs()
}

val vsixFile = layout.buildDirectory.file("semantifyr-$extensionVersion.vsix")

val bundleExtension = tasks.register<NpmTask>("bundleExtension") {
    group = LifecycleBasePlugin.BUILD_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.files(cloneDistribution).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneGrammars).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("language-configuration.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(renderIcons).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("README.md")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("CHANGELOG.md")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(cloneLicense).withPathSensitivity(PathSensitivity.RELATIVE)

    npmCommand = listOf("run", "bundle", "--", extensionVersion, "--no-git-tag-version", "--no-update-package-json")

    outputs.file(vsixFile)
    outputs.cacheIf {
        true
    }
}

tasks.register<NpmTask>("endToEndTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    readsPackage()
    configureSharedInputs()
    inputs.files(bundleExtension).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(isolated.rootProject.projectDirectory.file("oxsts-test-models/smoke/smoke.semantifyr")).withPathSensitivity(PathSensitivity.RELATIVE)
    runsWithTheta()

    npmCommand = listOf("run", "test:end-to-end")
    environment.put(
        "SEMANTIFYR_VSIX",
        vsixFile.map {
            it.asFile.absolutePath
        },
    )
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(bundleExtension)
}

tasks.register("continuousIntegration") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.named("endToEndTest"))
}

tasks.named<Delete>("clean") {
    delete(
        "dist",
        "icons",
        "bin",
        "syntaxes",
        "LICENSE",
    )
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
