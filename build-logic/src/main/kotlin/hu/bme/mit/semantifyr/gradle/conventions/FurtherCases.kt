/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

private const val FURTHER_CASES_EXTENSION = "further-cases"

// sysml-2ls translates one root namespace per document, so a model's further cases cannot sit in a second file that imports it
@CacheableTask
abstract class AppendFurtherCases : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val corpusDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun append() {
        val corpus = corpusDirectory.get().asFile
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        corpus.walkTopDown().filter {
            it.isFile && it.extension != FURTHER_CASES_EXTENSION
        }.forEach { model ->
            val target = output.resolve(model.relativeTo(corpus))
            target.parentFile.mkdirs()
            val fragment = model.resolveSibling("${model.nameWithoutExtension}.$FURTHER_CASES_EXTENSION")
            if (fragment.isFile) {
                target.writeText("${model.readText().trimEnd()}\n\n${fragment.readText()}")
            } else {
                model.copyTo(target)
            }
        }
    }
}
