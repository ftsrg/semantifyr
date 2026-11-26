/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.readsWorkspace
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.nodejs")
}

val npmCheck = tasks.register<NpmTask>("npmCheck") {
    readsWorkspace()
    inputs.dir(layout.projectDirectory.dir("src")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("tsconfig.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("eslint.config.js")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("vitest.config.ts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    // the corpora whose manifests and models the tests read
    listOf(
        "oxsts-test-models",
        "subprojects/frontends/gamma/models",
        "subprojects/frontends/sysml/models",
    ).forEach {
        inputs.dir(isolated.rootProject.projectDirectory.dir(it)).withPathSensitivity(PathSensitivity.RELATIVE)
    }

    npmCommand = listOf("run", "check")

    outputs.file(layout.buildDirectory.file("tsconfig.tsbuildinfo"))
    outputs.file(layout.buildDirectory.file("eslint.json"))
    outputs.file(layout.buildDirectory.file("test-results/unit.xml"))
    outputs.cacheIf {
        true
    }
}

configurations.consumable("npmPackageElements") {
    attributes(semantifyrArtifact("npm-package"))
    outgoing.artifact(layout.projectDirectory.dir("src"))
}

tasks.named("check") {
    dependsOn(npmCheck)
}
