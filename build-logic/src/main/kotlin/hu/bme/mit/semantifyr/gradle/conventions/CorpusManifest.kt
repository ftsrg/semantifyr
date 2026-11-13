/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.file.Directory

class CorpusModel(
    val file: String,
    val conformance: Boolean,
) {
    val name: String
        get() = file.substringBeforeLast('.')
}

// the models a corpus manifest lists, the shape the corpora share
fun Project.corpusModels(corpusDirectory: Directory): List<CorpusModel> {
    val manifest = providers.fileContents(corpusDirectory.file("manifest.json")).asText.get()
    val models = (JsonSlurper().parseText(manifest) as Map<*, *>)["models"] as List<*>
    return models.map {
        val model = it as Map<*, *>
        CorpusModel(model["file"] as String, model["conformance"] == true)
    }
}
