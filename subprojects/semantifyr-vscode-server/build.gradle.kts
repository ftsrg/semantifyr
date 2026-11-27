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
val oxstsTutorial = artifactsOf("oxsts-tutorial", ":website")

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

val cloneSysmlLibrary = tasks.register<Sync>("cloneSysmlLibrary") {
    from(sysmlLibrary)
    into("examples/sysml/Library")
}

val cloneGammaExamples = tasks.register<Sync>("cloneGammaExamples") {
    from(gammaExamples) {
        include("*.gamma")
    }
    into("examples/gamma/TestModels")
}

val cloneSysmlExamples = tasks.register<Sync>("cloneSysmlExamples") {
    val examples = sysmlExamples
    from(examples) {
        // the corpus models share its isStateActive and eventually
        include { element ->
            val modelFiles = corpusModels(examples.singleFile.resolve("manifest.json").readText()).map {
                it.file
            }
            element.isDirectory || element.name == "Verification.sysml" || element.relativePath.pathString in modelFiles
        }
    }
    into("examples/sysml/TestModels")
}

val cloneTutorialModels = tasks.register<Sync>("cloneTutorialModels") {
    from(oxstsTutorial)
    into("examples/tutorial/")
}

val vscodeServerImageContext = tasks.register<Tar>("vscodeServerImageContext") {
    archiveFileName = "vscode-server-image-context.tar.gz"
    compression = Compression.GZIP
    useFileSystemPermissions()
    dependsOn(
        cloneDistribution,
        cloneTheta,
        cloneGammaLibrary,
        cloneSysmlLibrary,
        cloneGammaExamples,
        cloneSysmlExamples,
        cloneTutorialModels,
    )
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
