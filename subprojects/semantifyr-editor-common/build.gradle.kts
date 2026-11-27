/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.gradle.registerNpmCheck
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.nodejs")
}

registerNpmCheck {
    listOf(
        "subprojects/website/models",
        "subprojects/frontends/gamma/models",
        "subprojects/frontends/sysml/models",
    ).forEach {
        inputs.dir(isolated.rootProject.projectDirectory.dir(it)).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

configurations.consumable("npmPackageElements") {
    attributes(semantifyrArtifact("npm-package"))
    outgoing.artifact(layout.projectDirectory.dir("src"))
    outgoing.artifact(layout.projectDirectory.file("package.json"))
}
