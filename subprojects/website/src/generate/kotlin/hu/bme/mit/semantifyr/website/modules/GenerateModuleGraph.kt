/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website.modules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BuildModule(
    val path: String,
    val directory: String,
    val dependencies: List<String>,
)

@Serializable
data class ModuleGraph(
    val modules: List<BuildModule>,
)

private val json = Json {
    prettyPrint = true
}

// each argument is a module as the build passes it: its path, its directory and the paths it depends on, separated by spaces
fun main(arguments: Array<String>) {
    val modules = arguments.map {
        val parts = it.split(" ")
        BuildModule(
            path = parts[0],
            directory = parts[1],
            dependencies = parts.drop(2),
        )
    }
    println(json.encodeToString(ModuleGraph(modules)))
}
