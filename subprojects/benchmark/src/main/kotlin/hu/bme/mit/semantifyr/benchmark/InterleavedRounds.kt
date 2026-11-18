/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.logging.info
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.time.Duration

private val logger = LoggerFactory.getLogger("hu.bme.mit.semantifyr.benchmark.InterleavedRounds")

class RepetitionScope internal constructor(
    internal val runexec: Runexec,
    internal val directory: Path,
)

class Measured<R> internal constructor(
    internal val jvm: ProcessObservation?,
    internal val calls: List<Call<R>>,
    internal val failure: Failure?,
    internal val timeLimit: Duration?,
)

inline fun <reified C, reified R> Benchmark<R>.interleavedRounds(
    cases: Map<String, C>,
    repetitions: Int = 5,
    context: List<ContextSource<*>> = emptyList(),
    noinline repetition: RepetitionScope.(C) -> Measured<R>,
): BenchmarkRun<R> {
    return runInterleavedRounds(
        name = javaClass.simpleName,
        cases = cases,
        repetitions = repetitions,
        context = context,
        configurationSerializer = serializer<C>(),
        resultSerializer = serializer<R>(),
        runexecExecutable = System.getProperty("benchmark.runexec") ?: "runexec",
        outputDirectory = Path(System.getProperty("benchmark.output") ?: "build/benchmark"),
        repetition = repetition,
    )
}

internal data class ScheduledRepetition(
    val round: Int,
    val configuration: String,
)

internal fun roundOrder(configurations: List<String>, repetitions: Int): List<ScheduledRepetition> {
    return (0..<repetitions).flatMap { round ->
        configurations.map {
            ScheduledRepetition(round, it)
        }
    }
}

@PublishedApi
internal fun <C, R> runInterleavedRounds(
    name: String,
    cases: Map<String, C>,
    repetitions: Int,
    context: List<ContextSource<*>>,
    configurationSerializer: KSerializer<C>,
    resultSerializer: KSerializer<R>,
    runexecExecutable: String,
    outputDirectory: Path,
    repetition: RepetitionScope.(C) -> Measured<R>,
): BenchmarkRun<R> {
    require(repetitions >= 5) {
        "a benchmark repeats at least five times"
    }
    val runexec = Runexec.probe(runexecExecutable)
    val runexecSource = object : ContextSource<String>("runexec", String.serializer()) {
        override fun record(): String {
            return runexec.version
        }
    }
    val startedAt = Instant.now().toString()
    val runDirectory = outputDirectory.resolve(name).resolve(startedAt.replace(':', '-'))
    val file = runDirectory.resolveSibling("${runDirectory.fileName}.json").createParentDirectories()
    val statement = Statement(
        benchmark = name,
        configurations = cases.mapValues {
            benchmarkJson.encodeToJsonElement(configurationSerializer, it.value)
        },
        repetitions = repetitions,
    )
    val recordedContext = (listOf(CodeRevision, Host, Jvm, runexecSource) + context).associate {
        it.name to it.recordAsJson()
    }
    val runSerializer = BenchmarkRun.serializer(resultSerializer)
    val scheduled = roundOrder(cases.keys.toList(), repetitions)
    val recorded = mutableListOf<Repetition<R>>()
    var run = BenchmarkRun(statement, startedAt, recordedContext, recorded.toList())
    for ((index, scheduledRepetition) in scheduled.withIndex()) {
        logger.info { "Repetition ${index + 1} of ${scheduled.size}: round ${scheduledRepetition.round + 1}, ${scheduledRepetition.configuration}" }
        val directory = runDirectory.resolve("round-${scheduledRepetition.round + 1}").resolve(scheduledRepetition.configuration).createDirectories()
        val loadBefore = LoadAverage.now()
        val measured = try {
            RepetitionScope(runexec, directory).repetition(cases.getValue(scheduledRepetition.configuration))
        } catch (throwable: Throwable) {
            Measured(null, emptyList(), Failure(Stage.Setup, throwable.stackTraceToString()), null)
        }
        recorded += Repetition(
            configuration = scheduledRepetition.configuration,
            round = scheduledRepetition.round,
            hostState = HostState(loadBefore, LoadAverage.now()),
            timeLimit = measured.timeLimit,
            jvm = measured.jvm,
            calls = measured.calls,
            failure = measured.failure,
        )
        run = BenchmarkRun(statement, startedAt, recordedContext, recorded.toList())
        writeAtomically(file, benchmarkJson.encodeToString(runSerializer, run))
    }
    val loads = recorded.map {
        it.hostState.loadBefore.oneMinute
    }
    logger.info { "Wrote $file, load average ${loads.min()} to ${loads.max()} before the repetitions" }
    return run
}
