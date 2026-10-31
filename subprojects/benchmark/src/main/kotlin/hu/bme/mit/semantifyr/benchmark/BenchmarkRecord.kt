/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.RunexecRun
import hu.bme.mit.semantifyr.verifier.VerificationMetrics
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration

@Serializable
enum class Outcome {
    Completed,
    TimedOut,
    Errored,
    Unavailable,
    ;

    val isRetried: Boolean
        get() = this == Errored || this == Unavailable

    companion object {
        fun of(verdict: VerificationVerdict): Outcome {
            return when (verdict) {
                VerificationVerdict.Timeout -> TimedOut
                VerificationVerdict.Errored -> Errored
                else -> Completed
            }
        }
    }
}

@Serializable
data class Measurement(
    val outcome: Outcome,
    val verdict: VerificationVerdict? = null,
    val expected: VerificationVerdict? = null,
    val falsification: Boolean = false,
    val asExpected: Boolean? = judge(verdict, expected),
    val decidedBy: String? = null,
    val wallTime: Duration? = null,
    val cpuTime: Duration? = null,
    val backendCpuTime: Duration? = null,
    val runexec: RunexecTotals? = null,
    val message: String? = null,
    val metrics: VerificationMetrics? = null,
    val extensions: Map<String, JsonElement> = emptyMap(),
) {
    companion object {
        // an undecided verdict is not a wrong answer, so it is not judged
        fun judge(verdict: VerificationVerdict?, expected: VerificationVerdict?): Boolean? {
            if (verdict == null || expected == null || !verdict.isDecisive) {
                return null
            }
            return verdict == expected
        }
    }
}

@Serializable
data class RunexecTotals(
    val runs: Int,
    val cpuTime: Duration,
    val wallTime: Duration,
    val peakMemory: Long?,
) {
    companion object {
        fun of(runs: List<RunexecRun>): RunexecTotals {
            return RunexecTotals(
                runs = runs.size,
                cpuTime = runs.mapNotNull { it.cpuTime }.fold(Duration.ZERO) { total, time -> total + time },
                wallTime = runs.mapNotNull { it.wallTime }.fold(Duration.ZERO) { total, time -> total + time },
                peakMemory = runs.mapNotNull { it.memory }.maxOrNull(),
            )
        }
    }
}

@Serializable
data class BenchmarkRecord(
    val campaign: String,
    val cell: Map<String, String>,
    val repetition: Int,
    val warmup: Boolean,
    val measurement: Measurement,
) {
    val cellKey: String
        get() = coordinateKey(cell)
}

class RecordExtension<T>(
    val key: String,
    private val serializer: KSerializer<T>,
) {
    fun entry(value: T): Pair<String, JsonElement> {
        return key to Json.encodeToJsonElement(serializer, value)
    }

    fun read(measurement: Measurement): T? {
        return measurement.extensions[key]?.let {
            Json.decodeFromJsonElement(serializer, it)
        }
    }
}
