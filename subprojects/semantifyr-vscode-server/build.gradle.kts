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
    distributionClasspath(project(":sysmlv2-frontend", configuration = "distributionOutput"))

    thetaClasspath(project(":theta-wrapper", configuration = "thetaOutput"))
}

val cloneDistribution by tasks.registering(Sync::class) {
    inputs.files(distributionClasspath)

    from(distributionClasspath)

    into("extensions")
}

val cloneTheta by tasks.registering(Sync::class) {
    inputs.files(thetaClasspath)

    from(thetaClasspath)

    into("theta-xsts-cli")
}

val cloneGammaLibrary by tasks.registering(Sync::class) {
    from(project(":gamma-semantics").layout.projectDirectory.dir("Library"))
    into("examples/gamma/Library")
}

val cloneSysMLLibrary by tasks.registering(Sync::class) {
    from(project(":sysmlv2-semantics").layout.projectDirectory.dir("Library"))
    into("examples/sysml/Library")
}

val cloneLibraries by tasks.registering {
    dependsOn(cloneGammaLibrary)
    dependsOn(cloneSysMLLibrary)
}

val cloneGammaTestModels by tasks.registering(Sync::class) {
    from(project(":gamma-semantics").layout.projectDirectory.dir("TestModels")) {
        include("*.gamma")
    }
    into("examples/gamma/TestModels")
}

val cloneSysMLTestModels by tasks.registering(Sync::class) {
    from(project(":sysmlv2-semantics").layout.projectDirectory.dir("TestModels")) {
        include("*.sysml")
    }
    into("examples/sysml/TestModels")
}

val cloneTutorialModels by tasks.registering(Sync::class) {
    from(project(":xsts-verifier").layout.projectDirectory.dir("test-models/Tutorial")) {
        include("*.oxsts")
    }
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

val dockerBuildImage by tasks.registering(DockerBuildImage::class) {
    dependsOn(prepareDockerBuild)
    inputDir.set(projectDir)
    images.add("ftsrgbot/semantifyr-vscode-server:${project.version}")
}

val dockerPushImage by tasks.registering(DockerPushImage::class) {
    dependsOn(dockerBuildImage)
    images.add("ftsrgbot/semantifyr-vscode-server:${project.version}")
}
