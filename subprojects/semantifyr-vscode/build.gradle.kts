/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    base
    alias(libs.plugins.gradle.node)
}

val distributionClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    distributionClasspath(project(":oxsts.lang.ide", configuration = "distributionOutput"))
    distributionClasspath(project(":xsts.lang.ide", configuration = "distributionOutput"))
    distributionClasspath(project(":semantifyr", configuration = "distributionOutput"))
}

val cloneDistribution by tasks.registering(Sync::class) {
    inputs.files(distributionClasspath)

    from (distributionClasspath.map {
        fileTree(it)
    })

    into("bin")
}

tasks {
    val compile by registering(NpmTask::class) {
        inputs.dir(project.layout.projectDirectory.dir("src"))
        inputs.files(npmInstall.get().outputs)

        npmCommand.set(
            listOf(
                "run",
                "package",
            )
        )

        outputs.dir("dist")
    }

    val packageExtension by registering(Exec::class) {
        inputs.files(cloneDistribution.get().outputs)
        inputs.files(compile.get().outputs)
        inputs.dir("node_modules")

        outputs.dir(project.layout.buildDirectory.dir("vscode"))

        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            commandLine(
                "cmd",
                "/c",
                "node_modules\\.bin\\vsce.cmd",
                "package",
                "--out", project.layout.buildDirectory.dir("vscode").get().asFile.absolutePath,
            )
        } else {
            commandLine(
                "sh",
                "-c",
                "node_modules/.bin/vsce package --out " + project.layout.buildDirectory.dir("vscode").get().asFile.absolutePath,
            )
        }
    }

    assemble {
        inputs.files(packageExtension.get().outputs)
    }

    clean {
        delete("dist")
    }
}
