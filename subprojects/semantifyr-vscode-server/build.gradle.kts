/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.conventions.sysmlCorpusModels

plugins {
    base
    id("hu.bme.mit.semantifyr.gradle.conventions.formatting")
}

val vscodeExtensions = configurations.dependencyScope("vscodeExtensions")
val vscodeExtensionsFiles = configurations.resolvable("vscodeExtensionsFiles") {
    extendsFrom(vscodeExtensions.get())
    attributes(semantifyrArtifact("vscode-extension"))
}

val theta = configurations.dependencyScope("theta")
val thetaFiles = configurations.resolvable("thetaFiles") {
    extendsFrom(theta.get())
    attributes(semantifyrArtifact("theta-xsts-cli"))
}

dependencies {
    vscodeExtensions(project(":semantifyr-vscode"))
    vscodeExtensions(project(":sysml-wrapper"))

    theta(project(":theta-executor"))
}

val cloneDistribution = tasks.register<Sync>("cloneDistribution") {
    from(vscodeExtensionsFiles)

    into("extensions")
}

val cloneTheta = tasks.register<Sync>("cloneTheta") {
    from(thetaFiles)

    into("theta-xsts-cli")
}

val cloneGammaLibrary = tasks.register<Sync>("cloneGammaLibrary") {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/libraries/default"))
    into("examples/gamma/Library")
}

val cloneSysMLLibrary = tasks.register<Sync>("cloneSysMLLibrary") {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/libraries/default"))
    into("examples/sysml/Library")
}

val cloneLibraries = tasks.register("cloneLibraries") {
    dependsOn(cloneGammaLibrary)
    dependsOn(cloneSysMLLibrary)
}

val cloneGammaTestModels = tasks.register<Sync>("cloneGammaTestModels") {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/examples")) {
        include("*.gamma")
    }
    into("examples/gamma/TestModels")
}

val sysmlCorpusDir = rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/examples")

val cloneSysMLTestModels = tasks.register<Sync>("cloneSysMLTestModels") {
    from(sysmlCorpusDir) {
        // the corpus models share its isStateActive and eventually
        include("Verification.sysml")
        include(
            sysmlCorpusModels(sysmlCorpusDir).map {
                it.file
            },
        )
    }
    into("examples/sysml/TestModels")
}

val cloneTutorialModels = tasks.register<Sync>("cloneTutorialModels") {
    from(rootProject.layout.projectDirectory.dir("oxsts-test-models/tutorial"))
    into("examples/tutorial/")
}

val cloneTestModels = tasks.register("cloneTestModels") {
    dependsOn(cloneGammaTestModels)
    dependsOn(cloneSysMLTestModels)
    dependsOn(cloneTutorialModels)
}

val prepareDockerBuild = tasks.register("prepareDockerBuild") {
    dependsOn(cloneDistribution)
    dependsOn(cloneTheta)
    dependsOn(cloneLibraries)
    dependsOn(cloneTestModels)
}

tasks.assemble {
    dependsOn(prepareDockerBuild)
}
