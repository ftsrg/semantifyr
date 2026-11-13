/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.file.Directory

fun Project.sysmlCorpusFiles(corpusDirectory: Directory): List<String> {
    val manifest = providers.fileContents(corpusDirectory.file("manifest.json")).asText.get()
    val models = (JsonSlurper().parseText(manifest) as Map<*, *>)["models"] as List<*>
    return models.map {
        (it as Map<*, *>)["file"] as String
    }
}
