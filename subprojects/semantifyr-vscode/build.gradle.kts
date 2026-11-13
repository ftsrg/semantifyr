/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.conventions.registerRenderPictures
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.conventions.stampOnSuccess

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.nodejs")
}

val languageServers = configurations.dependencyScope("languageServers")
val languageServersFiles = configurations.resolvable("languageServersFiles") {
    extendsFrom(languageServers.get())
    attributes(semantifyrArtifact("application-distribution"))
}

val editorCommon = configurations.dependencyScope("editorCommon")
val editorCommonFiles = configurations.resolvable("editorCommonFiles") {
    extendsFrom(editorCommon.get())
    attributes(semantifyrArtifact("npm-package"))
}

val theta = configurations.dependencyScope("theta")
val thetaFiles = configurations.resolvable("thetaFiles") {
    extendsFrom(theta.get())
    attributes(semantifyrArtifact("theta-xsts-cli"))
}

dependencies {
    languageServers(project(":oxsts.lang.ide"))
    languageServers(project(":gamma.lang.ide"))

    editorCommon(project(":semantifyr-editor-common"))

    theta(project(":theta-executor"))
}

val renderIcons = registerRenderPictures(
    "renderIcons",
    layout.projectDirectory.dir("icons"),
    listOf(
        "icon.png=semantifyr-logo@128",
        "semantifyr-file-icon.png=semantifyr-logo@128+104",
        "gamma-file-icon.png=gamma@128+104",
    ),
)

val cloneDistribution = tasks.register<Sync>("cloneDistribution") {
    from(languageServersFiles)

    into("bin")
}

tasks.npmInstall {
    workingDir = rootProject.projectDir
}

fun NpmTask.configureSharedInputs() {
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.file(project.layout.projectDirectory.file("esbuild.mjs"))
    inputs.file(project.layout.projectDirectory.file("vitest.config.ts"))
    inputs.file(project.layout.projectDirectory.file("eslint.config.js"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tsconfig.base.json"))
    inputs.file(rootProject.layout.projectDirectory.file("eslint.config.base.js"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall)
    inputs.files(editorCommonFiles)
}

val npmAssemble = tasks.register<NpmTask>("npmAssemble") {
    configureSharedInputs()

    npmCommand.set(listOf("run", "assemble"))

    outputs.dir("dist")
}

val npmCheck = tasks.register<NpmTask>("npmCheck") {
    configureSharedInputs()

    npmCommand.set(listOf("run", "check"))

    stampOnSuccess()
}

val thetaDirectory = layout.buildDirectory.dir("theta-xsts-cli")

val cloneTheta = tasks.register<Sync>("cloneTheta") {
    from(thetaFiles)
    into(thetaDirectory)
}

val npmIntegrationTest = tasks.register<NpmTask>("npmIntegrationTest") {
    group = "verification"

    configureSharedInputs()
    inputs.files(cloneDistribution)
    inputs.files(npmAssemble)
    inputs.files(cloneTheta)
    inputs.file(rootProject.layout.projectDirectory.file("oxsts-test-models/smoke/smoke.oxsts"))

    npmCommand.set(listOf("run", "test:integration"))
    environment.put("PATH", thetaDirectory.zip(providers.environmentVariable("PATH")) { directory, path ->
        "${directory.asFile.absolutePath}${File.pathSeparator}$path"
    })

    stampOnSuccess()
}

tasks.register("integrationTest") {
    group = "verification"
    dependsOn(npmIntegrationTest)
}

val vsixFile = project.layout.buildDirectory.file("semantifyr-${project.version}.vsix")

val bundleExtension = tasks.register<NpmTask>("bundleExtension") {
    val version = project.version.toString()
    inputs.property("version", version)

    inputs.files(cloneDistribution)
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.dir(project.layout.projectDirectory.dir("syntaxes"))
    inputs.file(project.layout.projectDirectory.file("esbuild.mjs"))
    inputs.file(project.layout.projectDirectory.file("eslint.config.js"))
    inputs.file(project.layout.projectDirectory.file("language-configuration.json"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.files(renderIcons)
    inputs.file(project.layout.projectDirectory.file("README.md"))
    inputs.file(project.layout.projectDirectory.file("CHANGELOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tsconfig.base.json"))
    inputs.file(rootProject.layout.projectDirectory.file("eslint.config.base.js"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall)
    inputs.files(editorCommonFiles)

    npmCommand.set(listOf("run", "bundle", "--", version, "--no-git-tag-version", "--no-update-package-json"))

    outputs.file(vsixFile)
}

tasks {
    assemble {
        inputs.files(cloneDistribution)
        inputs.files(npmAssemble)
    }

    check {
        dependsOn(npmCheck)
    }

    clean {
        delete("dist", "build", "icons")
    }
}

configurations.consumable("vscodeExtensionElements") {
    attributes(semantifyrArtifact("vscode-extension"))
    outgoing.artifact(vsixFile) {
        builtBy(bundleExtension)
    }
}
