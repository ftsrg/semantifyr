/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration

interface BenchmarkSource {
    val benchmark: Benchmark<*, *>
}

fun interface Trial<R> {
    fun call(measurement: Measurement): R
}

class Benchmark<C, R> @PublishedApi internal constructor(
    val configurations: Map<String, C>,
    val isolation: Isolation,
    val schedule: InterleavedRounds,
    val timeLimit: Duration?,
    val context: List<ContextSource<*>>,
    private val trial: (C) -> Trial<R>,
    private val analysis: (BenchmarkRun<C, R>, Logger) -> Unit,
    configurationSerializer: KSerializer<C>,
    resultSerializer: KSerializer<R>,
) {
    internal val runSerializer = BenchmarkRun.serializer(configurationSerializer, resultSerializer)

    internal val callsSerializer = ListSerializer(Call.serializer(resultSerializer))

    internal fun setUp(configuration: String): Trial<R> {
        return trial(configurations.getValue(configuration))
    }

    internal fun analyze(file: Path, logger: Logger) {
        analysis(benchmarkJson.decodeFromString(runSerializer, file.readText()), logger)
    }
}

inline fun <reified C, reified R> benchmark(
    configurations: Map<String, C>,
    isolation: Isolation,
    schedule: InterleavedRounds = InterleavedRounds(),
    timeLimit: Duration? = null,
    context: List<ContextSource<*>> = emptyList(),
    noinline analysis: (BenchmarkRun<C, R>, Logger) -> Unit = { _, _ ->
    },
    noinline trial: (C) -> Trial<R>,
): Benchmark<C, R> {
    return Benchmark(configurations, isolation, schedule, timeLimit, context, trial, analysis, serializer(), serializer())
}
