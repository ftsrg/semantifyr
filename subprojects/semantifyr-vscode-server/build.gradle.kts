/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    base
}

val distributionClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    distributionClasspath(project(":semantifyr-vscode", configuration = "distributionOutput"))
}

val cloneDistribution by tasks.registering(Sync::class) {
    inputs.files(distributionClasspath)

    from (distributionClasspath.map {
        fileTree(it)
    })

    into("extensions")
}

val prepareDockerBuild by tasks.registering() {
    inputs.files(cloneDistribution.get().outputs)
}
