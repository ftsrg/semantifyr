/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExitCodeEntry(
    val code: Int,
    val meaning: String,
)

private val json = Json {
    prettyPrint = true
}

private fun exitCodes(): String {
    val entries = ExitCode.entries.map {
        ExitCodeEntry(it.code, it.meaning)
    }
    return json.encodeToString(entries)
}

private fun verdicts(): String {
    val tags = VerificationVerdict.entries.map {
        verdictTag(it)
    }
    return json.encodeToString(tags)
}

fun main(arguments: Array<String>) {
    val output = when (val reference = arguments.single()) {
        "exit-codes" -> {
            exitCodes()
        }
        "verdicts" -> {
            verdicts()
        }
        else -> {
            error("No reference $reference")
        }
    }
    println(output)
}
