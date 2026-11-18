/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration

@Serializable
data class BenchmarkRun<C, R>(
    val statement: Statement<C>,
    val startedAt: String,
    val context: Map<String, JsonElement>,
    val repetitions: List<Repetition<R>>,
) {
    fun <V> context(source: ContextSource<V>): V? {
        return context[source.name]?.let {
            benchmarkJson.decodeFromJsonElement(source.serializer, it)
        }
    }

    fun repetitionsOf(configuration: String): List<Repetition<R>> {
        return repetitions.filter {
            it.configuration == configuration
        }
    }
}

@Serializable
data class Statement<C>(
    val benchmark: String,
    val configurations: Map<String, C>,
    val isolation: Isolation,
    val schedule: InterleavedRounds,
    val timeLimit: Duration?,
)

@Serializable
data class Repetition<R>(
    val configuration: String,
    val round: Int,
    val hostState: HostState,
    val jvm: ProcessObservation?,
    val calls: List<Call<R>>,
    val failure: Failure?,
) {
    val measuredCall: Call<R>?
        get() {
            return calls.singleOrNull {
                !it.warmUp
            }
        }
}

@Serializable
data class Call<R>(
    val warmUp: Boolean,
    val spans: List<SpanObservation>,
    val processes: List<ProcessObservation>,
    val result: R,
) {
    fun spanSeconds(name: String): Double {
        return spans.single {
            it.name == name
        }.wallTimeNanoseconds / 1e9
    }
}

@Serializable
data class SpanObservation(
    val name: String,
    val wallTimeNanoseconds: Long,
)

enum class Stage {
    Setup,
    Call,
    Measurement,
}

@Serializable
data class Failure(
    val stage: Stage,
    val stackTrace: String,
)

@Serializable
data class HostState(
    val loadBefore: LoadAverage,
    val loadAfter: LoadAverage,
)

@Serializable
data class LoadAverage(
    val oneMinute: Double,
    val fiveMinutes: Double,
    val fifteenMinutes: Double,
) {
    companion object {
        fun now(): LoadAverage {
            val fields = Path("/proc/loadavg").readText().split(" ")
            return LoadAverage(fields[0].toDouble(), fields[1].toDouble(), fields[2].toDouble())
        }
    }
}

internal val benchmarkJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

internal fun writeAtomically(file: Path, text: String) {
    val temporaryFile = file.resolveSibling("${file.fileName}.writing")
    temporaryFile.writeText(text)
    Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
