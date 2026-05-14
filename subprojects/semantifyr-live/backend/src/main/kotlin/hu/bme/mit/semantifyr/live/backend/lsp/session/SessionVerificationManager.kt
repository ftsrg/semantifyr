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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.future
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.Location
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.TimeSource

@SessionScoped
class SessionVerificationManager @Inject constructor(
    private val backendConfig: BackendConfig,
    private val verificationManager: VerificationManager,
    private val sessionContext: SessionContext,
    private val sessionRunContext: SessionRunContext,
    private val sessionClient: SessionClient,
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

        return sessionRunContext.coroutineScope.future(launchContext) {
            val mdcContext = withVerificationIdMdc(verificationId)
            withContext(mdcContext) {
                val entry = Entry(
                    verificationId = verificationId,
                    kind = kind,
                    location = location,
                    portfolioId = portfolioId,
                    startedAt = startedAt,
                    job = coroutineContext.job,
                )
                verifications[verificationId] = entry
                notifyActiveChanged()
                try {
                    verificationManager.withPermit {
                        entry.state = VerificationState.Running
                        notifyActiveChanged()
                        withTimeout(backendConfig.verification.timeout) {
                            work()
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    val message = "Verification timed out after ${backendConfig.verification.timeout}"
                    logger.warn(e) { message }
                    erroredResult(portfolioId, message)
                } catch (e: CancellationException) {
                    logger.info(e) { "Verification cancelled" }
                    null
                } catch (e: Throwable) {
                    val message = "Verification failed: ${e.message ?: e::class.simpleName}"
                    logger.error(e) { message }
                    erroredResult(portfolioId, message)
                } finally {
                    verifications.remove(verificationId)
                    notifyActiveChanged()
                }
            }
        }
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
