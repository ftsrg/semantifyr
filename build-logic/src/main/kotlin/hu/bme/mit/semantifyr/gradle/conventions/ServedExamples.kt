/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

class ServedExample(
    val id: String,
    val language: String,
    val file: String,
    val hidden: Boolean,
) {
    val fileName: String
        get() = file.substringAfterLast('/')

    val name: String
        get() = fileName.substringBeforeLast('.')
}

val Project.servedExamplesFile: RegularFile
    get() = rootProject.layout.projectDirectory.file("subprojects/semantifyr-editor-common/src/examples/examples.json")

fun Project.servedExamples(): List<ServedExample> {
    val registry = providers.fileContents(servedExamplesFile).asText.get()
    val examples = (JsonSlurper().parseText(registry) as Map<*, *>)["examples"] as List<*>
    return examples.map {
        val example = it as Map<*, *>
        ServedExample(example["id"] as String, example["language"] as String, example["file"] as String, example["hidden"] == true)
    }
}

fun Project.servedExamples(language: String): List<ServedExample> {
    return servedExamples().filter {
        it.language == language
    }
}

// the served models and the manifests beside them, which hold their cases' expected verdicts
fun Project.servedExampleSources(): List<RegularFile> {
    val root = rootProject.layout.projectDirectory
    return servedExamples().flatMap {
        listOf(
            it.file,
            "${it.file.substringBeforeLast('/')}/manifest.json",
        )
    }.distinct().map {
        root.file(it)
    }
}

// paths relative to the module, so that the test task stays relocatable for the build cache
fun Test.readsServedExamples() {
    val module = project.projectDir.toPath()
    inputs.file(project.servedExamplesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("semantifyr.repository", module.relativize(project.rootDir.toPath()).joinToString("/"))
    systemProperty("semantifyr.examples", module.relativize(project.servedExamplesFile.asFile.toPath()).joinToString("/"))
}
