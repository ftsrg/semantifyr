/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Inject
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.data.VerificationState
import hu.bme.mit.semantifyr.live.backend.exceptions.SyntaxErrorException
import hu.bme.mit.semantifyr.live.backend.lsp.service.RunningVerification
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageClient
import hu.bme.mit.semantifyr.live.backend.lsp.service.VerificationsChangedParams
import hu.bme.mit.semantifyr.live.backend.utils.currentMdcContextBlocking
import hu.bme.mit.semantifyr.live.backend.utils.withVerificationIdMdc
import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.future
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.Location
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration

private val durationBuckets = arrayOf(
    Duration.ofMillis(100),
    Duration.ofMillis(500),
    Duration.ofSeconds(1),
    Duration.ofSeconds(2),
    Duration.ofSeconds(5),
    Duration.ofSeconds(10),
    Duration.ofSeconds(30),
    Duration.ofSeconds(60),
    Duration.ofSeconds(120),
    Duration.ofSeconds(300),
)

@SessionScoped
class SessionVerificationManager @Inject constructor(
    private val backendConfig: BackendConfig,
    private val verificationManager: VerificationManager,
    private val sessionContext: SessionContext,
    private val sessionRunContext: SessionRunContext,
    private val sessionClient: SessionClient,
    private val registry: MeterRegistry,
) {

    private val logger by loggerFactory()

    private class Entry(
        val verificationId: String,
        val kind: VerificationKind,
        val location: Location,
        val portfolioId: String,
        val startedAt: TimeSource.Monotonic.ValueTimeMark,
        val permitRequest: PermitRequest,
    ) {
        var job: Job? = null
        var cancelled = false

        @Volatile
        var cancellationMessage = "Verification cancelled"

        @Volatile
        var state = VerificationState.Queued

        @Volatile
        var runningSince: TimeSource.Monotonic.ValueTimeMark? = null
    }

    private data class Registration(
        val entry: Entry,
        val superseded: List<Entry>,
    )

    private val lock = Any()

    private val verifications = LinkedHashMap<String, Entry>()

    private val caseLocationsByWitnessUri = ConcurrentHashMap<String, Location>()

    fun launch(
        request: VerificationCaseRequest,
        kind: VerificationKind,
        work: suspend () -> Any?,
    ): CompletableFuture<Any?> {
        val portfolioId = request.portfolio()
        val entry = register(request.toLocation(), kind, portfolioId)
        val verificationId = entry.verificationId
        notifyActiveChanged()
        val launchContext = currentMdcContextBlocking() + currentSessionScopeElement()

        val future = sessionRunContext.coroutineScope.future(launchContext) {
            val mdcContext = withVerificationIdMdc(verificationId)
            withContext(mdcContext) {
                // A cancelled coroutine drops its return value, so the cancellable work runs under its own job
                val workJob = SupervisorJob(coroutineContext.job)
                val cancelledBeforeStart = synchronized(lock) {
                    entry.job = workJob
                    entry.cancelled
                }
                if (cancelledBeforeStart) {
                    workJob.cancel()
                }
                try {
                    val result = withContext(workJob) {
                        verificationManager.withPermit(entry.permitRequest) {
                            synchronized(lock) {
                                if (!entry.cancelled) {
                                    entry.state = VerificationState.Running
                                    entry.runningSince = TimeSource.Monotonic.markNow()
                                }
                            }
                            currentCoroutineContext().ensureActive()
                            recordQueueWait(entry)
                            notifyActiveChanged()
                            withTimeout(backendConfig.verification.timeout) {
                                work()
                            }
                        }
                    }
                    recordWitness(entry, result)
                    recordFinished(entry, result)
                    result
                } catch (e: TimeoutCancellationException) {
                    val message = "Verification timed out after ${backendConfig.verification.timeout}"
                    logger.warn(e) { message }
                    timedOutResult(kind, portfolioId, message).also {
                        recordFinished(entry, it)
                    }
                } catch (e: CancellationException) {
                    logger.info(e) { entry.cancellationMessage }
                    recordCancelled(entry)
                    cancelledResult(kind, portfolioId, entry.cancellationMessage)
                } catch (e: SyntaxErrorException) {
                    logger.info(e) { "Verification refused: ${e.message}" }
                    erroredResult(kind, portfolioId, e.message.orEmpty()).also {
                        recordFinished(entry, it)
                    }
                } catch (e: Throwable) {
                    val message = "Verification failed: ${e.message ?: e::class.simpleName}"
                    logger.error(e) { message }
                    erroredResult(kind, portfolioId, message).also {
                        recordFinished(entry, it)
                    }
                } finally {
                    workJob.complete()
                    synchronized(lock) {
                        verifications.remove(verificationId)
                    }
                    notifyActiveChanged()
                }
            }
        }
        // The body never runs when the session closed before dispatch
        future.whenComplete { _, _ ->
            verificationManager.abandon(entry.permitRequest)
            synchronized(lock) {
                verifications.remove(verificationId)
            }
        }
        return future
    }

    fun active(): List<RunningVerification> {
        val snapshot = synchronized(lock) {
            verifications.values.toList()
        }
        return snapshot.map {
            it.toRunningVerification()
        }
    }

    fun cancel(verificationId: String): Boolean {
        val entry = synchronized(lock) {
            val entry = verifications.remove(verificationId) ?: return false
            entry.cancelled = true
            entry
        }
        cancelEntry(entry)
        return true
    }

    fun cancelAll(): Int {
        val snapshot = synchronized(lock) {
            val snapshot = verifications.values.toList()
            verifications.clear()
            for (entry in snapshot) {
                entry.cancelled = true
            }
            snapshot
        }
        for (entry in snapshot) {
            cancelEntry(entry)
        }
        return snapshot.size
    }

    private fun register(
        location: Location,
        kind: VerificationKind,
        portfolioId: String,
    ): Entry {
        val (entry, superseded) = synchronized(lock) {
            val superseded = verifications.values.filter {
                it.state == VerificationState.Queued && supersedes(location, kind, it)
            }
            for (old in superseded) {
                verifications.remove(old.verificationId)
                old.cancellationMessage = supersededMessage(old.kind)
                old.cancelled = true
            }
            val entry = Entry(
                verificationId = UUID.randomUUID().toString(),
                kind = kind,
                location = location,
                portfolioId = portfolioId,
                startedAt = TimeSource.Monotonic.markNow(),
                permitRequest = verificationManager.enqueue(sessionContext.sessionId),
            )
            verifications[entry.verificationId] = entry
            Registration(entry, superseded)
        }
        for (old in superseded) {
            cancelEntry(old)
        }
        return entry
    }

    private fun supersedes(
        location: Location,
        kind: VerificationKind,
        older: Entry,
    ): Boolean {
        if (kind == older.kind) {
            return location == older.location
        }
        if (kind != VerificationKind.Verify) {
            return false
        }
        return older.location == location || caseLocationsByWitnessUri[older.location.uri] == location
    }

    private fun supersededMessage(kind: VerificationKind): String {
        return when (kind) {
            VerificationKind.Verify -> "Verification superseded by a newer verification of the case"
            VerificationKind.Validate -> "Validation superseded by a newer request for the case"
        }
    }

    private fun cancelEntry(entry: Entry) {
        verificationManager.abandon(entry.permitRequest)
        val job = synchronized(lock) {
            entry.job
        }
        job?.cancel()
    }

    private fun recordWitness(entry: Entry, result: Any?) {
        if (entry.kind != VerificationKind.Verify || result !is VerificationCaseResult) {
            return
        }
        val witnessUri = result.trace()?.witnessUri() ?: return
        caseLocationsByWitnessUri[witnessUri] = entry.location
    }

    private fun Entry.toRunningVerification(): RunningVerification {
        return RunningVerification(
            verificationId = verificationId,
            location = location,
            portfolioId = portfolioId,
            kind = kind,
            state = state,
            elapsed = startedAt.elapsedNow(),
        )
    }

    private fun recordQueueWait(entry: Entry) {
        Timer.builder("semantifyr.live.verification.queue")
            .tag("kind", entry.kind.name.lowercase())
            .serviceLevelObjectives(*durationBuckets)
            .register(registry)
            .record(entry.startedAt.elapsedNow().toJavaDuration())
    }

    private fun recordFinished(entry: Entry, result: Any?) {
        val (backendId, verdict) = when (result) {
            is VerificationCaseResult -> result.backendId() to result.status()
            is WitnessValidationResult -> result.backendId() to result.status()
            else -> null to null
        }
        Timer.builder("semantifyr.live.verification")
            .tag("kind", entry.kind.name.lowercase())
            .tag("backend", backendId ?: "none")
            .tag("verdict", verdict ?: "unknown")
            .serviceLevelObjectives(*durationBuckets)
            .register(registry)
            .record((entry.runningSince ?: entry.startedAt).elapsedNow().toJavaDuration())
    }

    private fun recordCancelled(entry: Entry) {
        Counter.builder("semantifyr.live.verification.cancellations")
            .tag("kind", entry.kind.name.lowercase())
            .tag("state", entry.state.name.lowercase())
            .register(registry)
            .increment()
    }

    private fun timedOutResult(
        kind: VerificationKind,
        portfolioId: String,
        message: String,
    ): Any {
        return when (kind) {
            VerificationKind.Verify -> VerificationCaseResult.timedOut(message, portfolioId)
            VerificationKind.Validate -> WitnessValidationResult.timedOut(message, portfolioId)
        }
    }

    private fun cancelledResult(
        kind: VerificationKind,
        portfolioId: String,
        message: String,
    ): Any {
        return when (kind) {
            VerificationKind.Verify -> VerificationCaseResult.cancelled(message, portfolioId)
            VerificationKind.Validate -> WitnessValidationResult.cancelled(message, portfolioId)
        }
    }

    private fun erroredResult(
        kind: VerificationKind,
        portfolioId: String,
        message: String,
    ): Any {
        return when (kind) {
            VerificationKind.Verify -> VerificationCaseResult.errored(message, portfolioId)
            VerificationKind.Validate -> WitnessValidationResult.errored(message, portfolioId)
        }
    }

    private fun notifyActiveChanged() {
        try {
            (sessionClient.get() as SessionLanguageClient).verificationsChanged(
                VerificationsChangedParams(active()),
            )
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to send active-changed for sessionId=${sessionContext.sessionId}" }
        }
    }
}
