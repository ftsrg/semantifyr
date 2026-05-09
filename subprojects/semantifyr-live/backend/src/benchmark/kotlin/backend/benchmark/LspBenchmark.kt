/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.benchmark

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.withRealServer
import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.websocket.webSocket as clientWebSocket

class LspBenchmark {

    private val logger by loggerFactory()

    private val lspBinariesDirectory = System.getProperty("semantifyr.live.lsp")?.let {
        Path.of(it)
    }

    private val semanticLibrariesDirectory = System.getProperty("semantifyr.live.semanticLibraries")?.let {
        Path.of(it)
    }

    private val oxstsTestModelsDirectory = System.getProperty("semantifyr.live.oxstsTestModels")?.let {
        Path.of(it)
    }

    private val sessionCountSweep = (System.getProperty("semantifyr.benchmark.sweep") ?: "1,2,4,8,16,32,64")
        .split(",").map {
            it.trim().toInt()
        }

    private val idleHoldMillis = (System.getProperty("semantifyr.benchmark.idleHoldMillis") ?: "2000").toLong()
    private val editsPerSession = (System.getProperty("semantifyr.benchmark.edits") ?: "30").toInt()
    private val editIntervalMillis = (System.getProperty("semantifyr.benchmark.editIntervalMillis") ?: "200").toLong()
    private val stormDrainMillis = (System.getProperty("semantifyr.benchmark.stormDrainMillis") ?: "2000").toLong()
    private val scenarioTimeoutSeconds = (System.getProperty("semantifyr.benchmark.scenarioTimeoutSeconds") ?: "180").toLong()
    private val sessionPhaseTimeoutSeconds = (System.getProperty("semantifyr.benchmark.sessionPhaseTimeoutSeconds") ?: "60").toLong()
    private val sessionStartIntervalMillis = (System.getProperty("semantifyr.benchmark.sessionStartIntervalMillis") ?: "0").toLong()
    private val lspStartCooldownMillis = (System.getProperty("semantifyr.benchmark.lspStartCooldownMillis") ?: "0").toLong()
    private val lspProfileSessionCount = (System.getProperty("semantifyr.benchmark.profileAt") ?: "1").toInt()

    private val lspProfileJcmdCommands = listOf(
        listOf("VM.flags"),
        listOf("VM.native_memory", "summary"),
        listOf("VM.metaspace"),
        listOf("GC.class_histogram"),
    )

    @Test
    suspend fun `idle session steady-state RSS across N sessions`(@TempDir temporaryDirectory: Path) {
        assumeProcfsAvailable()
        assumeFixturesStaged()

        val modelSource = Files.readString(checkNotNull(oxstsTestModelsDirectory).resolve("simple.oxsts"))
        printScenarioHeader("idle steady-state", snapshotRss())

        for (sessionCount in sessionCountSweep) {
            val workDirectory = temporaryDirectory.resolve("idle-$sessionCount").also {
                it.createDirectories()
            }
            runScenario(
                scenarioName = "idle",
                sessionCount = sessionCount,
                workDirectory = workDirectory,
                measureSession = { httpClient, serverPort, sessionIndex, barrier ->
                    measureIdleSession(httpClient, serverPort, sessionIndex, modelSource, barrier)
                },
                report = this::reportIdle,
            )
        }
    }

    @Test
    suspend fun `edit storm publishDiagnostics throughput across N sessions`(@TempDir temporaryDirectory: Path) {
        assumeProcfsAvailable()
        assumeFixturesStaged()

        val modelSource = Files.readString(checkNotNull(oxstsTestModelsDirectory).resolve("simple.oxsts"))
        printScenarioHeader("edit storm", snapshotRss())
        logger.info { "editsPerSession=$editsPerSession intervalMillis=$editIntervalMillis drainMillis=$stormDrainMillis" }

        for (sessionCount in sessionCountSweep) {
            val workDirectory = temporaryDirectory.resolve("storm-$sessionCount").also {
                it.createDirectories()
            }
            runScenario(
                scenarioName = "storm",
                sessionCount = sessionCount,
                workDirectory = workDirectory,
                measureSession = { httpClient, serverPort, sessionIndex, barrier ->
                    measureStormSession(httpClient, serverPort, sessionIndex, modelSource, barrier)
                },
                report = this::reportStorm,
            )
        }
    }

    private suspend fun <T> runScenario(
        scenarioName: String,
        sessionCount: Int,
        workDirectory: Path,
        measureSession: suspend (HttpClient, Int, Int, SteadyStateBarrier) -> T,
        report: (Int, List<T>, RssSnapshot, List<RssSnapshot>) -> Unit,
    ) {
        logger.info {
            "Scenario start scenario=$scenarioName sessionCount=$sessionCount " +
                "sessionStartIntervalMillis=$sessionStartIntervalMillis"
        }
        val scenarioStartNanos = System.nanoTime()
        val completed = withTimeoutOrNull(scenarioTimeoutSeconds.seconds) {
            withRealServer(benchmarkConfig(workDirectory)) { httpClient, serverPort ->
                coroutineScope {
                    val sampler = RssSampler()
                    val samplerJob = launch {
                        sampler.runUntilCancelled()
                    }
                    val barrier = SteadyStateBarrier(sessionCount)
                    val steadySnapshot = CompletableDeferred<RssSnapshot>()
                    val coordinator = launch {
                        barrier.awaitAllReady()
                        val snapshot = snapshotRss()
                        logger.info {
                            "All sessions accounted for scenario=$scenarioName sessionCount=$sessionCount " +
                                "elapsedMillis=${elapsedMillis(scenarioStartNanos)} " +
                                "backendMiB=${snapshot.backendKilobytes / 1024} " +
                                "childrenMiB=${snapshot.childrenKilobytes / 1024} " +
                                "childProcessCount=${snapshot.childProcessCount}"
                        }
                        steadySnapshot.complete(snapshot)
                        if (sessionCount == lspProfileSessionCount) {
                            captureLspProfile(scenarioName)
                        }
                        barrier.release()
                    }
                    val sessionDeferreds = mutableListOf<Deferred<T>>()
                    for (sessionIndex in 0 until sessionCount) {
                        if (sessionIndex > 0 && sessionStartIntervalMillis > 0) {
                            delay(sessionStartIntervalMillis)
                        }
                        sessionDeferreds += async { measureSession(httpClient, serverPort, sessionIndex, barrier) }
                    }
                    val sessionTimings = sessionDeferreds.awaitAll()
                    coordinator.join()
                    samplerJob.cancelAndJoin()
                    val captured = steadySnapshot.await()
                    logger.info {
                        "Scenario complete scenario=$scenarioName sessionCount=$sessionCount " +
                            "elapsedMillis=${elapsedMillis(scenarioStartNanos)}"
                    }
                    report(sessionCount, sessionTimings, captured, sampler.snapshot())
                }
            }
            true
        }
        if (completed == null) {
            logger.error {
                "Scenario timed out scenario=$scenarioName sessionCount=$sessionCount " +
                    "timeoutSeconds=$scenarioTimeoutSeconds elapsedMillis=${elapsedMillis(scenarioStartNanos)}"
            }
        }
    }

    private suspend fun measureIdleSession(
        httpClient: HttpClient,
        serverPort: Int,
        sessionIndex: Int,
        modelSource: String,
        barrier: SteadyStateBarrier,
    ): IdleTimings {
        val sessionLabel = "idle/$sessionIndex"
        val arrivalSignaled = AtomicBoolean(false)
        fun ensureArrival(reason: String) {
            if (arrivalSignaled.compareAndSet(false, true)) {
                barrier.signalArrival()
                logger.info { "Barrier arrival session=$sessionLabel reason=$reason" }
            }
        }

        val documentUri = "file:///workspace/snippet.oxsts"
        var sessionReady = SessionReady(0L, 0L)
        var failed = false
        try {
            httpClient.clientWebSocket("ws://localhost:$serverPort/ws/lsp/oxsts") {
                logger.info { "WS opened session=$sessionLabel" }
                val connectStartNanos = System.nanoTime()
                sessionReady = withTimeout(sessionPhaseTimeoutSeconds.seconds) {
                    completeOpenHandshake(modelSource, documentUri, connectStartNanos)
                }
                logger.info {
                    "Session ready session=$sessionLabel " +
                        "initializeMillis=${sessionReady.initializeMillis} " +
                        "firstDiagnosticMillis=${sessionReady.firstDiagnosticMillis}"
                }
                coroutineScope {
                    val drainer = launch {
                        drainIncoming()
                    }
                    ensureArrival("ready")
                    barrier.awaitRelease()
                    delay(idleHoldMillis)
                    drainer.cancelAndJoin()
                }
                logger.info { "WS closing session=$sessionLabel" }
            }
        } catch (e: TimeoutCancellationException) {
            failed = true
            logger.error { "Session phase timed out session=$sessionLabel timeoutSeconds=$sessionPhaseTimeoutSeconds" }
        } catch (e: CancellationException) {
            ensureArrival("cancelled")
            throw e
        } catch (e: Exception) {
            failed = true
            logger.error { "Session failed session=$sessionLabel ${e::class.simpleName}: ${e.message}" }
        } finally {
            ensureArrival("finally")
        }
        return IdleTimings(
            initializeMillis = sessionReady.initializeMillis,
            firstDiagnosticMillis = sessionReady.firstDiagnosticMillis,
            failed = failed,
        )
    }

    private suspend fun measureStormSession(
        httpClient: HttpClient,
        serverPort: Int,
        sessionIndex: Int,
        modelSource: String,
        barrier: SteadyStateBarrier,
    ): StormTimings {
        val sessionLabel = "storm/$sessionIndex"
        val arrivalSignaled = AtomicBoolean(false)
        fun ensureArrival(reason: String) {
            if (arrivalSignaled.compareAndSet(false, true)) {
                barrier.signalArrival()
                logger.info { "Barrier arrival session=$sessionLabel reason=$reason" }
            }
        }

        val documentUri = "file:///workspace/snippet.oxsts"
        var sessionReady = SessionReady(0L, 0L)
        var editsSentMillis = 0L
        var stormTotalMillis = 0L
        val diagnosticCount = AtomicInteger(0)
        var failed = false
        try {
            httpClient.clientWebSocket("ws://localhost:$serverPort/ws/lsp/oxsts") {
                logger.info { "WS opened session=$sessionLabel" }
                val connectStartNanos = System.nanoTime()
                sessionReady = withTimeout(sessionPhaseTimeoutSeconds.seconds) {
                    completeOpenHandshake(modelSource, documentUri, connectStartNanos)
                }
                logger.info {
                    "Session ready session=$sessionLabel " +
                        "initializeMillis=${sessionReady.initializeMillis} " +
                        "firstDiagnosticMillis=${sessionReady.firstDiagnosticMillis}"
                }
                coroutineScope {
                    val diagnosticReader = launch {
                        while (isActive) {
                            val frame = incoming.receive() as? Frame.Text ?: continue
                            val parsed = parseFrame(frame)
                            if (parsed.publishDiagnosticsUri() == documentUri) {
                                diagnosticCount.incrementAndGet()
                            } else {
                                ackIfRequest(parsed)
                            }
                        }
                    }
                    ensureArrival("ready")
                    barrier.awaitRelease()
                    val stormStartNanos = System.nanoTime()
                    repeat(editsPerSession) {
                        val editedText = "$modelSource\n// edit $it\n"
                        send(
                            Frame.Text(
                                LspWire.didChangeNotification(
                                    uri = documentUri,
                                    version = it + 2,
                                    text = editedText,
                                ),
                            ),
                        )
                        delay(editIntervalMillis)
                    }
                    editsSentMillis = elapsedMillis(stormStartNanos)
                    delay(stormDrainMillis)
                    stormTotalMillis = elapsedMillis(stormStartNanos)
                    diagnosticReader.cancelAndJoin()
                }
                logger.info { "WS closing session=$sessionLabel" }
            }
        } catch (e: TimeoutCancellationException) {
            failed = true
            logger.error { "Session phase timed out session=$sessionLabel timeoutSeconds=$sessionPhaseTimeoutSeconds" }
        } catch (e: CancellationException) {
            ensureArrival("cancelled")
            throw e
        } catch (e: Exception) {
            failed = true
            logger.error { "Session failed session=$sessionLabel ${e::class.simpleName}: ${e.message}" }
        } finally {
            ensureArrival("finally")
        }
        return StormTimings(
            initializeMillis = sessionReady.initializeMillis,
            firstDiagnosticMillis = sessionReady.firstDiagnosticMillis,
            editsSentMillis = editsSentMillis,
            stormTotalMillis = stormTotalMillis,
            diagnosticCount = diagnosticCount.get(),
            failed = failed,
        )
    }

    private suspend fun DefaultClientWebSocketSession.completeOpenHandshake(
        modelSource: String,
        documentUri: String,
        connectStartNanos: Long,
    ): SessionReady {
        send(Frame.Text(LspWire.initializeRequest()))
        awaitResponseFor(id = 1, timeout = sessionPhaseTimeoutSeconds.seconds)
        val initializeMillis = elapsedMillis(connectStartNanos)
        send(Frame.Text(LspWire.initializedNotification()))
        send(
            Frame.Text(
                LspWire.didOpenNotification(
                    uri = documentUri,
                    languageId = "oxsts",
                    text = modelSource,
                ),
            ),
        )
        waitForPublishDiagnostics(documentUri, sessionPhaseTimeoutSeconds.seconds)
        val firstDiagnosticMillis = elapsedMillis(connectStartNanos)
        return SessionReady(initializeMillis, firstDiagnosticMillis)
    }

    private fun captureLspProfile(scenarioName: String) {
        val targetPid = ProcessHandle.current().descendants()
            .filter {
                residentSetSizeKilobytes(it.pid()) != null
            }
            .findFirst()
            .orElse(null)
            ?.pid()
        if (targetPid == null) {
            logger.error { "No descendant LSP process found for profiling scenario=$scenarioName" }
            return
        }
        val jcmdPath = locateJcmd()
        if (jcmdPath == null) {
            logger.error { "jcmd not found, cannot profile LSP scenario=$scenarioName" }
            return
        }
        logger.info { "Capturing LSP profile scenario=$scenarioName lspPid=$targetPid" }

        for (command in lspProfileJcmdCommands) {
            val output = runJcmd(jcmdPath, targetPid, command)
            val trimmed = trimProfileOutput(command.first(), output)
            logger.info { "--- jcmd ${command.joinToString(" ")} (pid=$targetPid) ---\n$trimmed" }
        }

        val threadDump = runJcmd(jcmdPath, targetPid, listOf("Thread.print"))
        val threadCount = threadDump.lineSequence().count {
            it.startsWith("\"")
        }
        logger.info { "Thread count scenario=$scenarioName lspPid=$targetPid threadCount=$threadCount" }
    }

    private fun runJcmd(jcmdPath: Path, targetPid: Long, command: List<String>): String {
        val process = ProcessBuilder(listOf(jcmdPath.toString(), targetPid.toString()) + command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        return output
    }

    private fun trimProfileOutput(commandHead: String, output: String): String {
        return when (commandHead) {
            "GC.class_histogram" -> output.lineSequence().take(33).joinToString("\n")
            else -> output
        }
    }

    private fun locateJcmd(): Path? {
        val javaHome = System.getProperty("java.home") ?: return null
        val jcmd = Path.of(javaHome).resolve("bin/jcmd")
        return jcmd.takeIf {
            Files.isExecutable(it)
        }
    }

    private fun benchmarkConfig(workDirectory: Path): BackendConfig {
        val lspBinariesPath = checkNotNull(lspBinariesDirectory)
        val semanticLibrariesPath = checkNotNull(semanticLibrariesDirectory)
        val productionDefaultLspJvmOpts = SessionManagerConfig().lspJvmOpts
        return BackendConfig(
            server = ServerConfig(
                adminPassword = "benchmark",
                wsHandshakesPerPeriod = 100_000,
            ),
            sessionManager = SessionManagerConfig(
                rootWorkDirectory = workDirectory.toString(),
                lspBinariesDirectory = lspBinariesPath.toString(),
                semanticLibrariesDirectory = semanticLibrariesPath.toString(),
                maxSessionsGlobal = 256,
                maxSessionsPerIp = 256,
                lspStartCooldownMillis = lspStartCooldownMillis,
                lspJvmOpts = "$productionDefaultLspJvmOpts -XX:NativeMemoryTracking=summary",
            ),
        )
    }

    private fun assumeFixturesStaged() {
        assumeTrue(lspBinariesDirectory != null, "semantifyr.live.lsp system property not set")
        assumeTrue(semanticLibrariesDirectory != null, "semantifyr.live.semanticLibraries system property not set")
        assumeTrue(oxstsTestModelsDirectory != null, "semantifyr.live.oxstsTestModels system property not set")
    }

    private fun assumeProcfsAvailable() {
        assumeTrue(Files.exists(Path.of("/proc/self/status")), "RSS sampling requires Linux /proc")
    }

    private fun reportIdle(
        sessionCount: Int,
        sessionTimings: List<IdleTimings>,
        steadyState: RssSnapshot,
        memorySamples: List<RssSnapshot>,
    ) {
        val succeeded = sessionTimings.filterNot {
            it.failed
        }
        val failedCount = sessionTimings.size - succeeded.size
        val initializeMillis = succeeded.map { it.initializeMillis }
        val firstDiagnosticMillis = succeeded.map { it.firstDiagnosticMillis }
        val peakBackendKilobytes = memorySamples.maxOfOrNull { it.backendKilobytes } ?: 0L
        val peakChildrenKilobytes = memorySamples.maxOfOrNull { it.childrenKilobytes } ?: 0L
        logger.info {
            "  N=%2d | succeeded=%2d failed=%2d | initialize p50=%5d ms p99=%5d ms | firstDiagnostic p50=%5d ms p99=%5d ms | RSS steady backend=%6d MiB children=%6d MiB (childProcessCount=%d) | peak backend=%6d MiB children=%6d MiB"
                .format(
                    sessionCount,
                    succeeded.size,
                    failedCount,
                    initializeMillis.percentile(50),
                    initializeMillis.percentile(99),
                    firstDiagnosticMillis.percentile(50),
                    firstDiagnosticMillis.percentile(99),
                    steadyState.backendKilobytes / 1024,
                    steadyState.childrenKilobytes / 1024,
                    steadyState.childProcessCount,
                    peakBackendKilobytes / 1024,
                    peakChildrenKilobytes / 1024,
                )
        }
    }

    private fun reportStorm(
        sessionCount: Int,
        sessionTimings: List<StormTimings>,
        steadyState: RssSnapshot,
        memorySamples: List<RssSnapshot>,
    ) {
        val succeeded = sessionTimings.filterNot {
            it.failed
        }
        val failedCount = sessionTimings.size - succeeded.size
        val initializeMillis = succeeded.map { it.initializeMillis }
        val firstDiagnosticMillis = succeeded.map { it.firstDiagnosticMillis }
        val stormTotalMillis = succeeded.map { it.stormTotalMillis }
        val perSessionDiagnostics = succeeded.map { it.diagnosticCount }
        val totalEdits = succeeded.size * editsPerSession
        val totalDiagnostics = perSessionDiagnostics.sum()
        val peakBackendKilobytes = memorySamples.maxOfOrNull { it.backendKilobytes } ?: 0L
        val peakChildrenKilobytes = memorySamples.maxOfOrNull { it.childrenKilobytes } ?: 0L
        logger.info {
            "  N=%2d | succeeded=%2d failed=%2d | initialize p50=%5d ms | firstDiagnostic p50=%5d ms | stormTotal p50=%5d ms | totalEdits=%4d totalDiagnostics=%4d (perSession p50=%3d) | RSS steady backend=%6d MiB children=%6d MiB | peak backend=%6d MiB children=%6d MiB"
                .format(
                    sessionCount,
                    succeeded.size,
                    failedCount,
                    initializeMillis.percentile(50),
                    firstDiagnosticMillis.percentile(50),
                    stormTotalMillis.percentile(50),
                    totalEdits,
                    totalDiagnostics,
                    perSessionDiagnostics.percentile(50),
                    steadyState.backendKilobytes / 1024,
                    steadyState.childrenKilobytes / 1024,
                    peakBackendKilobytes / 1024,
                    peakChildrenKilobytes / 1024,
                )
        }
    }

    private fun printScenarioHeader(scenarioName: String, baselineSnapshot: RssSnapshot) {
        logger.info { "=== $scenarioName ===" }
        logger.info {
            "baseline RSS: backend=${baselineSnapshot.backendKilobytes / 1024} MiB " +
                "childProcessCount=${baselineSnapshot.childProcessCount}"
        }
        logger.info { "sessionCountSweep=$sessionCountSweep" }
    }

    private data class SessionReady(
        val initializeMillis: Long,
        val firstDiagnosticMillis: Long,
    )

    private data class IdleTimings(
        val initializeMillis: Long,
        val firstDiagnosticMillis: Long,
        val failed: Boolean,
    )

    private data class StormTimings(
        val initializeMillis: Long,
        val firstDiagnosticMillis: Long,
        val editsSentMillis: Long,
        val stormTotalMillis: Long,
        val diagnosticCount: Int,
        val failed: Boolean,
    )
}

private class SteadyStateBarrier(private val sessionCount: Int) {

    private val arrivedCount = AtomicInteger(0)
    private val allArrived = CompletableDeferred<Unit>()
    private val released = CompletableDeferred<Unit>()

    fun signalArrival() {
        if (arrivedCount.incrementAndGet() == sessionCount) {
            allArrived.complete(Unit)
        }
    }

    suspend fun awaitAllReady() {
        allArrived.await()
    }

    suspend fun awaitRelease() {
        released.await()
    }

    fun release() {
        released.complete(Unit)
    }
}

private suspend fun DefaultClientWebSocketSession.drainIncoming() = coroutineScope {
    while (isActive) {
        val frame = incoming.receive() as? Frame.Text ?: continue
        ackIfRequest(parseFrame(frame))
    }
}

private fun elapsedMillis(startNanos: Long): Long {
    return (System.nanoTime() - startNanos) / 1_000_000
}

private fun parseFrame(frame: Frame.Text): JsonObject {
    return Json.parseToJsonElement(frame.readText()).jsonObject
}

private fun JsonObject.publishDiagnosticsUri(): String? {
    if (this["method"]?.jsonPrimitive?.contentOrNull != "textDocument/publishDiagnostics") {
        return null
    }
    return this["params"]?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
}

private suspend fun DefaultClientWebSocketSession.ackIfRequest(message: JsonObject) {
    val method = message["method"]?.jsonPrimitive?.contentOrNull
    val identifier = message["id"] ?: return
    if (method == null) {
        return
    }
    val acknowledgement = """{"jsonrpc":"2.0","id":$identifier,"result":null}"""
    send(Frame.Text(acknowledgement))
}

private suspend fun DefaultClientWebSocketSession.waitForPublishDiagnostics(
    documentUri: String,
    timeout: kotlin.time.Duration,
) {
    withTimeout(timeout) {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val parsed = parseFrame(frame)
            if (parsed.publishDiagnosticsUri() == documentUri) {
                return@withTimeout
            }
            ackIfRequest(parsed)
        }
    }
}

private fun List<Long>.percentile(percent: Int): Long {
    if (isEmpty()) {
        return 0L
    }
    val sorted = sorted()
    val index = ((percent / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
    return sorted[index]
}

@JvmName("percentileInt")
private fun List<Int>.percentile(percent: Int): Int {
    if (isEmpty()) {
        return 0
    }
    val sorted = sorted()
    val index = ((percent / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
    return sorted[index]
}

private data class RssSnapshot(
    val timestampNanos: Long,
    val backendKilobytes: Long,
    val childrenKilobytes: Long,
    val childProcessCount: Int,
)

private fun snapshotRss(): RssSnapshot {
    val currentProcess = ProcessHandle.current()
    val backendKilobytes = residentSetSizeKilobytes(currentProcess.pid()) ?: 0L
    var childrenKilobytes = 0L
    var childProcessCount = 0
    currentProcess.descendants().forEach {
        val kilobytes = residentSetSizeKilobytes(it.pid())
        if (kilobytes != null) {
            childrenKilobytes += kilobytes
            childProcessCount++
        }
    }
    return RssSnapshot(
        timestampNanos = System.nanoTime(),
        backendKilobytes = backendKilobytes,
        childrenKilobytes = childrenKilobytes,
        childProcessCount = childProcessCount,
    )
}

private fun residentSetSizeKilobytes(processId: Long): Long? {
    val statusPath = Path.of("/proc/$processId/status")
    if (!Files.exists(statusPath)) {
        return null
    }
    return runCatching {
        statusPath.readText()
            .lineSequence()
            .firstOrNull {
                it.startsWith("VmRSS:")
            }
            ?.split(Regex("\\s+"))
            ?.getOrNull(1)
            ?.toLong()
    }.getOrNull()
}

private class RssSampler(private val intervalMillis: Long = 100) {

    private val samples = mutableListOf<RssSnapshot>()

    suspend fun runUntilCancelled() = coroutineScope {
        while (isActive) {
            samples.add(snapshotRss())
            delay(intervalMillis)
        }
    }

    fun snapshot(): List<RssSnapshot> {
        return samples.toList()
    }
}
