/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.file.Directory

class SysmlCorpusModel(
    val file: String,
    val isExample: Boolean,
) {
    val name: String
        get() = file.removeSuffix(".sysml")
}

fun Project.sysmlCorpusModels(corpusDirectory: Directory): List<SysmlCorpusModel> {
    val manifest = providers.fileContents(corpusDirectory.file("manifest.json")).asText.get()
    val models = (JsonSlurper().parseText(manifest) as Map<*, *>)["models"] as List<*>
    return models.map {
        val model = it as Map<*, *>
        SysmlCorpusModel(model["file"] as String, model["example"] != null)
    }
}
