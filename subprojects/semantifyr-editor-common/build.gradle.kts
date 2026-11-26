/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import hu.bme.mit.semantifyr.gradle.stampOnSuccess

plugins {
    id("hu.bme.mit.semantifyr.nodejs")
}

tasks.npmInstall {
    workingDir = rootProject.projectDir
}

val npmCheck = tasks.register<NpmTask>("npmCheck") {
    inputs.dir(layout.projectDirectory.dir("src"))
    inputs.file(layout.projectDirectory.file("tsconfig.json"))
    inputs.file(layout.projectDirectory.file("eslint.config.js"))
    inputs.file(layout.projectDirectory.file("vitest.config.ts"))
    inputs.file(layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tsconfig.base.json"))
    inputs.file(rootProject.layout.projectDirectory.file("eslint.config.base.js"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall)
    // the corpora whose manifests and models the tests read
    listOf(
        "oxsts-test-models",
        "subprojects/frontends/gamma/models",
        "subprojects/frontends/sysml/models",
    ).forEach {
        inputs.dir(rootProject.layout.projectDirectory.dir(it)).withPathSensitivity(PathSensitivity.RELATIVE)
    }

    npmCommand = listOf("run", "check")

    stampOnSuccess()
}

configurations.consumable("npmPackageElements") {
    attributes(semantifyrArtifact("npm-package"))
    outgoing.artifact(layout.projectDirectory.dir("src"))
}

tasks.check {
    dependsOn(npmCheck)
}
