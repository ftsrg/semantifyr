/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.logging.info
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration

private val logger = LoggerFactory.getLogger("hu.bme.mit.semantifyr.benchmark.BenchmarkRun")

@Serializable
class BenchmarkRun<R> {
    var benchmark = ""
        internal set

    var startedAt = ""
        internal set

    private val context = mutableMapOf<String, JsonElement>()

    @SerialName("configurations")
    private val configurationValues = mutableMapOf<String, JsonElement>()

    @SerialName("repetitions")
    private val recordedRepetitions = mutableListOf<Repetition<R>>()

    val configurations: Map<String, JsonElement>
        get() {
            return configurationValues
        }

    val repetitions: List<Repetition<R>>
        get() {
            return recordedRepetitions
        }

    fun record(source: ContextSource<*>) {
        context[source.name] = source.recordAsJson()
    }

    fun <V> context(source: ContextSource<V>): V? {
        return context[source.name]?.let {
            benchmarkJson.decodeFromJsonElement(source.serializer, it)
        }
    }

    inline fun <reified C> add(
        configuration: String,
        value: C,
        measured: Measured<R>,
    ) {
        addEncoded(configuration, benchmarkJson.encodeToJsonElement(serializer<C>(), value), measured)
    }

    @PublishedApi
    internal fun addEncoded(
        configuration: String,
        value: JsonElement,
        measured: Measured<R>,
    ) {
        configurationValues[configuration] = value
        recordedRepetitions += Repetition(configuration, measured.hostState, measured.timeLimit, measured.jvm, measured.calls, measured.failure)
        logger.info { "Repetition ${recordedRepetitions.size}: $configuration" }
        BenchmarkEnvironment.written(this)
    }

    internal fun describe(
        benchmark: String,
        startedAt: String,
        context: Map<String, JsonElement>,
    ) {
        this.benchmark = benchmark
        this.startedAt = startedAt
        this.context.putAll(context)
    }

    fun repetitionsOf(configuration: String): List<Repetition<R>> {
        return repetitions.filter {
            it.configuration == configuration
        }
    }
}

@Serializable
data class Repetition<R>(
    val configuration: String,
    val hostState: HostState,
    val timeLimit: Duration?,
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
    val result: R?,
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

@PublishedApi
internal val benchmarkJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

internal fun writeAtomically(file: Path, text: String) {
    val temporaryFile = file.resolveSibling("${file.fileName}.writing")
    temporaryFile.writeText(text)
    Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
