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
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/gamma/models/examples")) {
        include("*.gamma")
    }
    into("examples/gamma/TestModels")
}

val cloneSysMLTestModels by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("subprojects/frontends/sysml/models/examples")) {
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
