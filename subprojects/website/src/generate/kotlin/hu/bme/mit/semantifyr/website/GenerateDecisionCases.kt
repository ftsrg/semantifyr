/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website

import hu.bme.mit.semantifyr.verifier.CaseManifest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.readText

@Serializable
data class DecidingCase(
    val model: String,
    val name: String,
)

@Serializable
data class DecisionCases(
    val id: String,
    val title: String,
    val cases: List<DecidingCase>,
)

private val decisionHeading = Regex("""^### (.+) \{#([a-z]+(?:-[a-z]+)*)}$""", RegexOption.MULTILINE)

private val json = Json {
    prettyPrint = true
}

fun main(arguments: Array<String>) {
    val (manifestDirectory, page) = arguments
    val manifest = CaseManifest.load(Path(manifestDirectory))
    val decisions = decisionHeading.findAll(Path(page).readText()).map { heading ->
        val id = heading.groupValues[2]
        val cases = manifest.models.flatMap { model ->
            model.cases.filter {
                id in it.decisions
            }.map {
                DecidingCase(model.file, it.name)
            }
        }
        DecisionCases(id, heading.groupValues[1], cases)
    }.toList()
    println(json.encodeToString(decisions))
}
