/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test

val Project.oxstsFileExtension: String
    get() {
        val workflow = providers.fileContents(isolated.rootProject.projectDirectory.file("subprojects/oxsts.lang/src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2")).asText.get()
        val extensions = Regex("""fileExtensions = "([^"]+)"""").find(workflow) ?: error("The grammar workflow lists no file extension")
        return extensions.groupValues[1].substringBefore(',')
    }

class CorpusModel(
    val file: String,
    val conformance: Boolean,
) {
    val name: String
        get() = file.substringBeforeLast('.')
}

fun Project.corpusModels(corpusDirectory: Directory): List<CorpusModel> {
    return corpusModels(providers.fileContents(corpusDirectory.file("manifest.json")).asText.get())
}

fun corpusModels(manifest: String): List<CorpusModel> {
    val models = (JsonSlurper().parseText(manifest) as Map<*, *>)["models"] as List<*>
    return models.map {
        val model = it as Map<*, *>
        CorpusModel(
            model["file"] as String,
            model["conformance"] == true,
        )
    }
}

const val FURTHER_CASES_EXTENSION = "further-cases"

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
        }.forEach {
            val target = output.resolve(it.relativeTo(corpus))
            target.parentFile.mkdirs()
            val fragment = it.resolveSibling("${it.nameWithoutExtension}.$FURTHER_CASES_EXTENSION")
            if (fragment.isFile) {
                target.writeText("${it.readText().trimEnd()}\n\n${fragment.readText()}")
            } else {
                it.copyTo(target)
            }
        }
    }
}

class ServedExample(
    val language: String,
    val file: String,
) {
    val name: String
        get() = file.substringAfterLast('/').substringBeforeLast('.')
}

private val Project.servedExamplesFile: RegularFile
    get() = isolated.rootProject.projectDirectory.file("subprojects/semantifyr-editor-common/src/examples/examples.json")

private fun Project.servedExamples(): List<ServedExample> {
    val registry = providers.fileContents(servedExamplesFile).asText.get()
    val examples = (JsonSlurper().parseText(registry) as Map<*, *>)["examples"] as List<*>
    return examples.map {
        val example = it as Map<*, *>
        ServedExample(
            example["language"] as String,
            example["file"] as String,
        )
    }
}

fun Project.servedExamples(language: String): List<ServedExample> {
    return servedExamples().filter {
        it.language == language
    }
}

fun Project.servedExampleSources(): List<RegularFile> {
    val root = isolated.rootProject.projectDirectory
    return servedExamples().flatMap {
        listOf(
            it.file,
            "${it.file.substringBeforeLast('/')}/manifest.json",
        )
    }.distinct().map {
        root.file(it)
    }
}

fun Test.readsTestModels(vararg folders: String) {
    val testModels = project.isolated.rootProject.projectDirectory.dir("oxsts-test-models")
    if (folders.isEmpty()) {
        inputs.dir(testModels).withPathSensitivity(PathSensitivity.RELATIVE)
    }
    folders.forEach {
        inputs.dir(testModels.dir(it)).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

private fun descriptionOf(text: String): String? {
    val opening = text.trimStart()
    if (!opening.startsWith("/**") && !opening.startsWith("doc")) {
        return null
    }
    val comment = opening.substringAfter("/*", "").substringBefore("*/")
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
