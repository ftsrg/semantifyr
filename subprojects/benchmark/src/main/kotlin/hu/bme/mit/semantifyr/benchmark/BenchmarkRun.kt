/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import com.sun.management.OperatingSystemMXBean
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration

@Serializable
data class BenchmarkRun<C, R>(
    val statement: Statement<C>,
    val context: RunContext,
    val repetitions: List<Repetition<R>>,
)

enum class BenchmarkKind {
    InJvm,
    Process,
}

@Serializable
data class Statement<C>(
    val benchmark: String,
    val kind: BenchmarkKind,
    val configurations: Map<String, C>,
    val repetitions: Int,
    val warmUpCalls: Int?,
    val timeLimit: Duration?,
)

@Serializable
data class Repetition<R>(
    val configuration: String,
    val round: Int,
    val hostState: HostState,
    val processes: List<ProcessObservation>,
    val calls: List<Call<R>>,
    val failure: Failure?,
)

@Serializable
data class Call<R>(
    val warmUp: Boolean,
    val spans: List<SpanObservation>,
    val result: R,
)

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

@Serializable
data class RunContext(
    val startedAt: String,
    val revision: String?,
    val uncommittedChanges: Boolean?,
    val backendVersions: Map<String, String?>,
    val runexec: String,
    val hostName: String,
    val operatingSystem: String,
    val processorModel: String?,
    val processors: Int,
    val physicalMemoryBytes: Long,
    val jvm: String,
    val jvmArguments: List<String>,
    val maximumHeapBytes: Long,
) {
    companion object {
        fun record(backendVersions: Map<String, String?>, runexec: Runexec): RunContext {
            val system = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
            val processorModel = Path("/proc/cpuinfo").readLines().firstOrNull {
                it.startsWith("model name")
            }?.substringAfter(':')?.trim()
            return RunContext(
                startedAt = Instant.now().toString(),
                revision = commandOutputOrNull("git", "rev-parse", "HEAD"),
                uncommittedChanges = commandOutputOrNull("git", "status", "--porcelain")?.isNotEmpty(),
                backendVersions = backendVersions,
                runexec = runexec.version,
                hostName = InetAddress.getLocalHost().hostName,
                operatingSystem = "${system.name} ${system.version} ${system.arch}",
                processorModel = processorModel,
                processors = system.availableProcessors,
                physicalMemoryBytes = system.totalMemorySize,
                jvm = "${System.getProperty("java.vm.name")} ${System.getProperty("java.runtime.version")}",
                jvmArguments = ManagementFactory.getRuntimeMXBean().inputArguments,
                maximumHeapBytes = Runtime.getRuntime().maxMemory(),
            )
        }
    }
}

internal val benchmarkJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

internal fun <C, R> writeBenchmarkRun(
    file: Path,
    run: BenchmarkRun<C, R>,
    configurationSerializer: KSerializer<C>,
    resultSerializer: KSerializer<R>,
) {
    val serializer = BenchmarkRun.serializer(configurationSerializer, resultSerializer)
    writeAtomically(file, benchmarkJson.encodeToString(serializer, run))
}

internal fun writeAtomically(file: Path, text: String) {
    val temporaryFile = file.resolveSibling("${file.fileName}.writing")
    temporaryFile.writeText(text)
    Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

fun <C, R> readBenchmarkRun(
    file: Path,
    configurationSerializer: KSerializer<C>,
    resultSerializer: KSerializer<R>,
): BenchmarkRun<C, R> {
    val serializer = BenchmarkRun.serializer(configurationSerializer, resultSerializer)
    return benchmarkJson.decodeFromString(serializer, file.readText())
}

inline fun <reified C, reified R> readBenchmarkRun(file: Path): BenchmarkRun<C, R> {
    return readBenchmarkRun(file, serializer<C>(), serializer<R>())
}
