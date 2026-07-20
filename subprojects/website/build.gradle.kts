/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.nodejs")
}

tasks.npmInstall {
    workingDir = rootProject.projectDir
}

val brandingDir = rootProject.layout.projectDirectory.dir("branding")
val importedBrandingDir = project.layout.projectDirectory.dir("static-imported-branding")

val cloneBrandingAssets by tasks.registering(Sync::class) {
    from(brandingDir) {
        include("favicon.ico")
    }
    from(brandingDir) {
        include("semantifyr-logo.svg")
        rename { "logo.svg" }
        into("img")
    }
    from(brandingDir) {
        include("semantifyr-full-light.svg")
        rename { "logo-full-light.svg" }
        into("img")
    }
    from(brandingDir) {
        include("semantifyr-full-dark.svg")
        rename { "logo-full-dark.svg" }
        into("img")
    }
    from(brandingDir) {
        include("semantifyr-logo-192.png")
        rename { "logo-192.png" }
        into("img")
    }
    from(brandingDir) {
        include("semantifyr-logo-512.png")
        rename { "logo-512.png" }
        into("img")
    }
    from(brandingDir) {
        include("semantifyr-logo-180.png")
        rename { "apple-touch-icon.png" }
        into("img")
    }
    into(importedBrandingDir)
}

fun NpmTask.configureSharedInputs() {
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.dir(project.layout.projectDirectory.dir("static"))
    inputs.file(project.layout.projectDirectory.file("docusaurus.config.ts"))
    inputs.file(project.layout.projectDirectory.file("sidebars.ts"))
    inputs.file(project.layout.projectDirectory.file("babel.config.cts"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("eslint.config.mjs"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tsconfig.base.json"))
    inputs.file(rootProject.layout.projectDirectory.file("eslint.config.base.js"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall)
    inputs.files(cloneBrandingAssets)
}

val npmAssemble by tasks.registering(NpmTask::class) {
    configureSharedInputs()

    npmCommand.set(listOf("run", "assemble"))
    outputs.dir(project.layout.buildDirectory.dir("docusaurus"))
}

val npmCheck by tasks.registering(NpmTask::class) {
    configureSharedInputs()

    npmCommand.set(listOf("run", "check"))
}

tasks {
    assemble {
        dependsOn(npmAssemble)
    }

    check {
        dependsOn(npmCheck)
    }

    clean {
        delete(".docusaurus")
        delete("build")
        delete(importedBrandingDir)
    }
}
