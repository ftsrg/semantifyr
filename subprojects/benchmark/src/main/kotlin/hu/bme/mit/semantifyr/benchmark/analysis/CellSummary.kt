/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.benchmark.BenchmarkRecord
import hu.bme.mit.semantifyr.benchmark.Measurement
import hu.bme.mit.semantifyr.benchmark.Outcome
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration

data class Statistic(
    val median: Duration,
    val minimum: Duration,
    val maximum: Duration,
) {
    val spread: Duration
        get() = maximum - minimum

    companion object {
        fun of(values: List<Duration>): Statistic? {
            if (values.isEmpty()) {
                return null
            }
            val sorted = values.sorted()
            val middle = sorted.size / 2
            val median = if (sorted.size % 2 == 1) {
                sorted[middle]
            } else {
                (sorted[middle - 1] + sorted[middle]) / 2
            }
            return Statistic(median, sorted.first(), sorted.last())
        }
    }
}

data class CellSummary(
    val cell: Map<String, String>,
    val outcome: Outcome,
    val verdicts: List<VerificationVerdict>,
    val expected: VerificationVerdict?,
    val asExpected: Boolean?,
    val falsification: Boolean,
    val decidedBy: Set<String>,
    val repetitions: Int,
    val wallTime: Statistic?,
    val cpuTime: Statistic?,
    val backendCpuTime: Statistic?,
    val runexecCpuTime: Statistic?,
    val peakMemory: Long?,
    val compilationTime: Statistic?,
    val optimizationTime: Statistic?,
    val backendTime: Statistic?,
    val inputSize: ModelSize?,
    val inlinedSize: ModelSize?,
    val flattenedSize: ModelSize?,
    val extensions: Map<String, String>,
) {
    val verdict: VerificationVerdict?
        get() = verdicts.distinct().singleOrNull()
}

fun summarize(records: List<BenchmarkRecord>): List<CellSummary> {
    return records.groupBy {
        it.cellKey
    }.values.map { cellRecords ->
        val last = cellRecords.maxBy { it.repetition }.measurement
        val measured = cellRecords.filter {
            !it.warmup
        }.map {
            it.measurement
        }
        // sizes do not depend on the repetition, so a cell that stopped in its warmup still has them
        val sized = cellRecords.firstNotNullOfOrNull {
            it.measurement.metrics
        }?.verifier?.compilation
        CellSummary(
            cell = cellRecords.first().cell,
            outcome = last.outcome,
            verdicts = measured.mapNotNull { it.verdict },
            expected = last.expected,
            asExpected = judgeAll(measured),
            falsification = last.falsification,
            decidedBy = measured.mapNotNull { it.decidedBy }.toSortedSet(),
            repetitions = measured.count { it.outcome == Outcome.Completed },
            wallTime = Statistic.of(measured.mapNotNull { it.wallTime }),
            cpuTime = Statistic.of(measured.mapNotNull { it.cpuTime }),
            backendCpuTime = Statistic.of(measured.mapNotNull { it.backendCpuTime }),
            runexecCpuTime = Statistic.of(measured.mapNotNull { it.runexec?.cpuTime }),
            peakMemory = measured.mapNotNull { it.runexec?.peakMemory }.maxOrNull(),
            compilationTime = Statistic.of(measured.mapNotNull { it.metrics?.verifier?.compilationDuration }),
            optimizationTime = Statistic.of(
                measured.mapNotNull {
                    it.metrics?.verifier?.compilation?.let { compilation ->
                        compilation.inliningOptimization.duration + compilation.flatteningOptimization.duration
                    }
                },
            ),
            backendTime = Statistic.of(measured.mapNotNull { it.metrics?.backend?.verificationDuration }),
            inputSize = sized?.inputSize,
            inlinedSize = sized?.inlinedSize,
            flattenedSize = sized?.flattenedSize,
            extensions = flattenExtensions((measured.firstOrNull() ?: last).extensions),
        )
    }
}

private fun judgeAll(measurements: List<Measurement>): Boolean? {
    val judgements = measurements.mapNotNull { it.asExpected }
    return when {
        judgements.isEmpty() -> null
        false in judgements -> false
        else -> true
    }
}

private fun flattenExtensions(extensions: Map<String, JsonElement>): Map<String, String> {
    return extensions.flatMap { (key, value) ->
        flatten(key, value)
    }.toMap()
}

private fun flatten(prefix: String, element: JsonElement): List<Pair<String, String>> {
    return when (element) {
        is JsonObject -> element.flatMap { (key, value) ->
            flatten("$prefix.$key", value)
        }
        is JsonPrimitive -> listOf(prefix to element.content)
        else -> listOf(prefix to element.toString())
    }
}
