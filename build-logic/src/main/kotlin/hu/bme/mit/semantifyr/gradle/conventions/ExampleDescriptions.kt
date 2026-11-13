/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

// a model describes itself in a comment at its top: `//` lines in OXSTS and Gamma, a `doc` comment in SysML v2
fun descriptionOf(text: String): String? {
    val lines = text.lines().dropWhile {
        it.isBlank()
    }
    val first = lines.firstOrNull()?.trim() ?: return null
    if (first.startsWith("//")) {
        return first.removePrefix("//").trim().ifEmpty {
            null
        }
    }
    if (!first.startsWith("doc")) {
        return null
    }
    val comment = lines.joinToString("\n").substringAfter("/*", "").substringBefore("*/")
    return comment.lines().map {
        it.trim().removePrefix("*").trim()
    }.firstOrNull {
        it.isNotEmpty()
    }
}

@CacheableTask
abstract class ExampleDescriptions : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val models: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        val directory = outputDirectory.get().asFile
        directory.deleteRecursively()
        directory.mkdirs()
        models.files.forEach { model ->
            descriptionOf(model.readText())?.let {
                directory.resolve("${model.name}.description").writeText(it)
            }
        }
    }
}
