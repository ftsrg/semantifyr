/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Inject
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationStatus
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.data.VerificationState
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
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

    private data class Entry(
        val verificationId: String,
        val kind: VerificationKind,
        val location: Location,
        val portfolioId: String,
        val startedAt: TimeSource.Monotonic.ValueTimeMark,
        val job: Job,
        @Volatile
        var state: VerificationState = VerificationState.Queued,
        @Volatile
        var runningSince: TimeSource.Monotonic.ValueTimeMark? = null,
    )

    private val verifications = ConcurrentHashMap<String, Entry>()

    fun launch(
        request: VerificationCaseRequest,
        kind: VerificationKind,
        work: suspend () -> Any?,
    ): CompletableFuture<Any?> {
        val verificationId = UUID.randomUUID().toString()
        val location = request.toLocation()
        val portfolioId = request.portfolio()
        val startedAt = TimeSource.Monotonic.markNow()
        val launchContext = currentMdcContextBlocking() + currentSessionScopeElement()

        lateinit var entry: Entry
        val verification = sessionRunContext.coroutineScope.async(launchContext, start = CoroutineStart.LAZY) {
            val mdcContext = withVerificationIdMdc(verificationId)
            withContext(mdcContext) {
                notifyActiveChanged()
                try {
                    val result = verificationManager.withPermit {
                        entry.state = VerificationState.Running
                        entry.runningSince = TimeSource.Monotonic.markNow()
                        recordQueueWait(entry)
                        notifyActiveChanged()
                        withTimeout(backendConfig.verification.timeout) {
                            work()
                        }
                    }
                    recordFinished(entry, result)
                    result
                } catch (e: TimeoutCancellationException) {
                    val message = "Verification timed out after ${backendConfig.verification.timeout}"
                    logger.warn(e) { message }
                    VerificationCaseResult(VerificationStatus.TIMEOUT, message, null, portfolioId, null, null).also {
                        recordFinished(entry, it)
                    }
                } catch (e: CancellationException) {
                    logger.info(e) { "Verification cancelled" }
                    recordCancelled(entry)
                    null
                } catch (e: Throwable) {
                    val message = "Verification failed: ${e.message ?: e::class.simpleName}"
                    logger.error(e) { message }
                    erroredResult(portfolioId, message).also {
                        recordFinished(entry, it)
                    }
                } finally {
                    verifications.remove(verificationId)
                    notifyActiveChanged()
                }
            }
        }
        entry = Entry(
            verificationId = verificationId,
            kind = kind,
            location = location,
            portfolioId = portfolioId,
            startedAt = startedAt,
            job = verification,
        )
        val maxPerSession = backendConfig.verification.maxPerSession
        val admitted = synchronized(verifications) {
            if (verifications.size >= maxPerSession) {
                false
            } else {
                verifications[verificationId] = entry
                true
            }
        }
        if (!admitted) {
            verification.cancel()
            recordRejected(kind)
            logger.warn { "Verification rejected, the session has $maxPerSession pending verifications" }
            return CompletableFuture.failedFuture(
                ResponseErrorException(
                    ResponseError(ResponseErrorCode.RequestFailed, "The session has too many pending verifications (limit $maxPerSession)", null),
                ),
            )
        }
        verification.start()
        return verification.asCompletableFuture()
    }

    fun active(): List<RunningVerification> {
        return verifications.values.map {
            it.toRunningVerification()
        }
    }

    fun cancel(verificationId: String): Boolean {
        val entry = verifications.remove(verificationId) ?: return false
        entry.job.cancel()
        return true
    }

    fun cancelAll(): Int {
        val snapshot = verifications.values.toList()
        for (entry in snapshot) {
            verifications.remove(entry.verificationId)
            entry.job.cancel()
        }
        return snapshot.size
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

    private fun recordRejected(kind: VerificationKind) {
        Counter.builder("semantifyr.live.verifications.rejected")
            .tag("kind", kind.name.lowercase())
            .register(registry)
            .increment()
    }

    private fun recordCancelled(entry: Entry) {
        Counter.builder("semantifyr.live.verification.cancellations")
            .tag("kind", entry.kind.name.lowercase())
            .tag("state", entry.state.name.lowercase())
            .register(registry)
            .increment()
    }

    private fun erroredResult(portfolioId: String, message: String): VerificationCaseResult {
        return VerificationCaseResult(VerificationStatus.ERRORED, message, null, portfolioId, null, null)
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
