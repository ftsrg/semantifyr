/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.artifactsOf
import hu.bme.mit.semantifyr.gradle.corpusModels

plugins {
    base
    id("hu.bme.mit.semantifyr.formatting")
}

val vscodeExtensions = artifactsOf("vscode-extension", ":semantifyr-vscode", ":sysml-wrapper")
val theta = artifactsOf("theta-xsts-cli", ":theta-executor")
val gammaLibrary = artifactsOf("gamma-library", ":gamma-frontend")
val sysmlLibrary = artifactsOf("sysml-library", ":sysml-frontend")
val gammaExamples = artifactsOf("gamma-examples", ":gamma-frontend")
val sysmlExamples = artifactsOf("sysml-examples", ":sysml-frontend")

val cloneDistribution = tasks.register<Sync>("cloneDistribution") {
    from(vscodeExtensions)

    into("extensions")
}

val cloneTheta = tasks.register<Sync>("cloneTheta") {
    from(theta)

    into("theta-xsts-cli")
}

val cloneGammaLibrary = tasks.register<Sync>("cloneGammaLibrary") {
    from(gammaLibrary)
    into("examples/gamma/Library")
}

val cloneSysMLLibrary = tasks.register<Sync>("cloneSysMLLibrary") {
    from(sysmlLibrary)
    into("examples/sysml/Library")
}

val cloneLibraries = tasks.register("cloneLibraries") {
    dependsOn(cloneGammaLibrary)
    dependsOn(cloneSysMLLibrary)
}

val cloneGammaTestModels = tasks.register<Sync>("cloneGammaTestModels") {
    from(gammaExamples) {
        include("*.gamma")
    }
    into("examples/gamma/TestModels")
}

val cloneSysMLTestModels = tasks.register<Sync>("cloneSysMLTestModels") {
    from(sysmlExamples) {
        // the corpus models share its isStateActive and eventually
        include("Verification.sysml")
        include(
            corpusModels(layout.projectDirectory.dir(sysmlExamples.singleFile.path)).map {
                it.file
            },
        )
    }
    into("examples/sysml/TestModels")
}

val cloneTutorialModels = tasks.register<Sync>("cloneTutorialModels") {
    from(isolated.rootProject.projectDirectory.dir("oxsts-test-models/tutorial"))
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

val vscodeServerImageContext = tasks.register<Tar>("vscodeServerImageContext") {
    archiveFileName = "vscode-server-image-context.tar.gz"
    compression = Compression.GZIP
    useFileSystemPermissions()
    dependsOn(prepareDockerBuild)
    from(layout.projectDirectory) {
        include("Dockerfile")
        include("jupyter_server_config.py")
        include("examples/**")
        include("extensions/**")
        include("theta-xsts-cli/**")
    }
}

tasks.register("pack") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn(vscodeServerImageContext)
}
