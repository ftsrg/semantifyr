/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.benchmark.coordinateKey
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readLines
import kotlin.io.path.writeText

@Serializable
data class ReferenceCell(
    val cell: Map<String, String>,
    val verdict: VerificationVerdict? = null,
    val inputSize: ModelSize? = null,
    val inlinedSize: ModelSize? = null,
    val flattenedSize: ModelSize? = null,
) {
    val key: String
        get() = coordinateKey(cell)
}

object Reference {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    // times stay out: they differ between hosts, verdicts and sizes must not
    fun of(summaries: List<CellSummary>): List<ReferenceCell> {
        return summaries.map {
            ReferenceCell(
                cell = it.cell,
                verdict = it.verdict?.takeIf { verdict -> verdict.isDecisive },
                inputSize = it.inputSize,
                inlinedSize = it.inlinedSize,
                flattenedSize = it.flattenedSize,
            )
        }.sortedBy {
            it.key
        }
    }

    fun write(file: Path, cells: List<ReferenceCell>) {
        file.parent.createDirectories()
        file.writeText(
            cells.joinToString("") {
                "${json.encodeToString(it)}\n"
            },
        )
    }

    fun read(file: Path): List<ReferenceCell> {
        return file.readLines().filter {
            it.isNotBlank()
        }.map {
            json.decodeFromString<ReferenceCell>(it)
        }
    }

    fun compare(reference: List<ReferenceCell>, current: List<ReferenceCell>): List<String> {
        val currentByKey = current.associateBy { it.key }
        val referenceKeys = reference.map { it.key }.toSet()
        val differences = reference.flatMap { expected ->
            val actual = currentByKey[expected.key] ?: return@flatMap listOf("${expected.key}: missing from the results")
            listOfNotNull(
                difference(expected.key, "verdict", expected.verdict, actual.verdict),
                difference(expected.key, "input size", expected.inputSize, actual.inputSize),
                difference(expected.key, "inlined size", expected.inlinedSize, actual.inlinedSize),
                difference(expected.key, "flattened size", expected.flattenedSize, actual.flattenedSize),
            )
        }
        val unexpected = current.filter {
            it.key !in referenceKeys
        }.map {
            "${it.key}: not in the reference"
        }
        return differences + unexpected
    }

    fun compareTimes(
        baseline: List<CellSummary>,
        current: List<CellSummary>,
        tolerance: Double,
    ): List<String> {
        val baselineByKey = baseline.associateBy {
            coordinateKey(it.cell)
        }
        return current.mapNotNull { summary ->
            val key = coordinateKey(summary.cell)
            val now = summary.wallTime?.median ?: return@mapNotNull null
            val before = baselineByKey[key]?.wallTime?.median ?: return@mapNotNull null
            if (now > before * (1 + tolerance)) {
                "$key: wall time median $now exceeds the baseline $before by more than ${tolerance * 100}%"
            } else {
                null
            }
        }
    }

    private fun difference(
        key: String,
        field: String,
        expected: Any?,
        actual: Any?,
    ): String? {
        if (expected == actual) {
            return null
        }
        return "$key: $field is $actual, the reference has $expected"
    }
}
