/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

plugins {
    base
    alias(libs.plugins.bmuschko.docker)
}

val distributionClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val thetaClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    distributionClasspath(project(":semantifyr-vscode", configuration = "distributionOutput"))
    distributionClasspath(project(":sysml-wrapper", configuration = "distributionOutput"))

    thetaClasspath(project(":theta-executor", configuration = "thetaOutput"))
}

val cloneDistribution by tasks.registering(Sync::class) {
    from(distributionClasspath)

    into("extensions")
}

val cloneTheta by tasks.registering(Sync::class) {
    from(thetaClasspath)

    into("theta-xsts-cli")
}

val cloneGammaLibrary by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/libraries/default"))
    into("examples/gamma/Library")
}

val cloneSysMLLibrary by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/libraries/default"))
    into("examples/sysml/Library")
}

val cloneLibraries by tasks.registering {
    dependsOn(cloneGammaLibrary)
    dependsOn(cloneSysMLLibrary)
}

val cloneGammaTestModels by tasks.registering(Sync::class) {
    from(project(":gamma-frontend").layout.projectDirectory.dir("TestModels")) {
        include("*.gamma")
    }
    into("examples/gamma/TestModels")
}

val cloneSysMLTestModels by tasks.registering(Sync::class) {
    from(project(":sysml-frontend").layout.projectDirectory.dir("TestModels")) {
        include("*.sysml")
    }
    into("examples/sysml/TestModels")
}

val cloneTutorialModels by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("oxsts-test-models/tutorial"))
    into("examples/tutorial/")
}

val cloneTestModels by tasks.registering {
    dependsOn(cloneGammaTestModels)
    dependsOn(cloneSysMLTestModels)
    dependsOn(cloneTutorialModels)
}

val prepareDockerBuild by tasks.registering {
    dependsOn(cloneDistribution)
    dependsOn(cloneTheta)
    dependsOn(cloneLibraries)
    dependsOn(cloneTestModels)
}

tasks.assemble {
    dependsOn(prepareDockerBuild)
}

val dockerImageRepo = "ftsrgbot/semantifyr-vscode-server"
val dockerGitSha = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }

val dockerBuildImage by tasks.registering(DockerBuildImage::class) {
    val repo = dockerImageRepo
    val version = project.version.toString()
    dependsOn(prepareDockerBuild)
    inputDir.set(projectDir)
    images.add("$repo:$version")
    images.add("$repo:latest")
    images.add(dockerGitSha.map { "$repo:$it" })
}

val dockerPushImage by tasks.registering(DockerPushImage::class) {
    val repo = dockerImageRepo
    val version = project.version.toString()
    dependsOn(dockerBuildImage)
    images.add("$repo:$version")
    images.add("$repo:latest")
    images.add(dockerGitSha.map { "$repo:$it" })
}
