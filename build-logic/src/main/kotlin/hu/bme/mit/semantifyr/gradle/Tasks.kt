/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.getByType
import org.gradle.process.ProcessForkOptions
import java.io.File

fun <T> T.runsWithTheta() where T : Task, T : ProcessForkOptions {
    val thetaDirectory = project.artifactsOf("theta-xsts-cli", ":theta-executor")
    inputs.files(thetaDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
    doFirst {
        environment("PATH", "${thetaDirectory.singleFile.absolutePath}${File.pathSeparator}${System.getenv("PATH")}")
    }
}

fun Project.commitHash(): Provider<String> {
    return providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map {
        it.trim()
    }
}

fun JavaExec.writesStandardOutputTo(file: Provider<RegularFile>) {
    outputs.file(file)
    doFirst {
        standardOutput = file.get().asFile.outputStream()
    }
    doLast {
        standardOutput.close()
    }
}

fun Project.registerCliReference(references: Map<String, List<String>>) {
    val application = extensions.getByType<JavaApplication>()
    val mainSourceSet = extensions.getByType<SourceSetContainer>().named(SourceSet.MAIN_SOURCE_SET_NAME)
    val referenceDirectory = layout.buildDirectory.dir("cli-reference")
    val referenceTasks = references.map { (name, arguments) ->
        tasks.register("cliReference_$name", JavaExec::class.java) {
            val outputFile = referenceDirectory.map {
                it.file("$name.txt")
            }
            classpath = mainSourceSet.get().runtimeClasspath
            mainClass = application.mainClass
            args(arguments)
            writesStandardOutputTo(outputFile)
            outputs.cacheIf {
                true
            }
            doLast {
                val file = outputFile.get().asFile
                file.writeText(file.readText().replace("\r\n", "\n"))
            }
        }
    }

    configurations.consumable("cliReferenceElements") {
        attributes(semantifyrArtifact("cli-reference"))
        outgoing.artifact(referenceDirectory) {
            builtBy(referenceTasks)
        }
    }
}
