/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.artifactsOf

plugins {
    base
    id("hu.bme.mit.semantifyr.formatting")
}

val vscodeExtensions = artifactsOf("vscode-extension", ":semantifyr-vscode", ":sysml-wrapper")
val theta = artifactsOf("theta-xsts-cli", ":theta-executor")
val gammaLibrary = isolated.rootProject.projectDirectory.dir("subprojects/frontends/gamma/models/libraries/default")
val sysmlLibrary = isolated.rootProject.projectDirectory.dir("subprojects/frontends/sysml/models/libraries/default")
val gammaExamples = isolated.rootProject.projectDirectory.dir("subprojects/frontends/gamma/models/examples")
val sysmlExamples = isolated.rootProject.projectDirectory.dir("subprojects/frontends/sysml/models/examples")
val oxstsTutorial = artifactsOf("oxsts-tutorial", ":website")
val oxstsExamples = isolated.rootProject.projectDirectory.dir("subprojects/website/models/examples")

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
    from(sysmlExamples) {
        include("*.sysml")
    }
    into("examples/sysml/TestModels")
}

val cloneTutorialModels = tasks.register<Sync>("cloneTutorialModels") {
    from(oxstsTutorial)
    from(oxstsExamples) {
        include("*.semantifyr")
    }
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
