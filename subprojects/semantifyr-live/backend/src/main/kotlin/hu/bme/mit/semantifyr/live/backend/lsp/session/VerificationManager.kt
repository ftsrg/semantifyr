/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationStatus
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.lsp.service.RunningVerification
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageClient
import hu.bme.mit.semantifyr.live.backend.lsp.service.VerificationsChangedParams
import hu.bme.mit.semantifyr.live.backend.utils.currentMdcContext
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.services.LanguageClient
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.TimeSource

@Singleton
class VerificationManager @Inject constructor(
    private val backendConfig: BackendConfig,
) {

    private val logger by loggerFactory()

    private val gate = Semaphore(backendConfig.verification.concurrency)

    val availablePermits: Int
        get() = gate.availablePermits

    val maxPermits = backendConfig.verification.concurrency

    private data class Entry(
        val sessionId: String,
        val verificationId: String,
        val kind: VerificationKind,
        val location: Location,
        val portfolioId: String,
        val startedAt: TimeSource.Monotonic.ValueTimeMark,
        val job: Job,
    )

    private val verifications = ConcurrentHashMap<String, Entry>()

    fun run(
        lspSession: LspSession,
        request: VerificationCaseRequest,
        kind: VerificationKind,
        work: suspend () -> Any?,
    ): CompletableFuture<Any?> {
        val verificationId = UUID.randomUUID().toString()
        val location = request.toLocation()
        val portfolioId = request.portfolio()
        val startedAt = TimeSource.Monotonic.markNow()

        return lspSession.coroutineScope.future {
            val mdcContext = withVerificationIdMdc(verificationId)
            withContext(mdcContext) {
                val entry = Entry(
                    sessionId = lspSession.sessionId,
                    verificationId = verificationId,
                    kind = kind,
                    location = location,
                    portfolioId = portfolioId,
                    startedAt = startedAt,
                    job = coroutineContext.job,
                )
                verifications[verificationId] = entry
                notifyActiveChanged(lspSession.sessionId, lspSession.client())
                try {
                    withPermit {
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
                    notifyActiveChanged(lspSession.sessionId, lspSession.client())
                }
            }
        }
    }

    fun cancel(verificationId: String): Boolean {
        val entry = verifications.remove(verificationId) ?: return false
        entry.job.cancel()
        return true
    }

    fun cancelForSession(sessionId: String): Int {
        val matching = verifications.filterValues { it.sessionId == sessionId }
        for ((verificationId, entry) in matching) {
            verifications.remove(verificationId)
            entry.job.cancel()
        }
        return matching.size
    }

    fun activeFor(sessionId: String): List<RunningVerification> {
        return verifications.values.filter {
            it.sessionId == sessionId
        }.map {
            it.toRunningVerification()
        }
    }

    private fun Entry.toRunningVerification(): RunningVerification {
        return RunningVerification(
            verificationId = verificationId,
            location = location,
            portfolioId = portfolioId,
            kind = kind,
            elapsed = startedAt.elapsedNow(),
        )
    }

    private fun erroredResult(portfolioId: String, message: String): VerificationCaseResult {
        return VerificationCaseResult(VerificationStatus.ERRORED, message, null, portfolioId, null, null)
    }

    private fun notifyActiveChanged(sessionId: String, client: LanguageClient) {
        try {
            (client as SessionLanguageClient).verificationsChanged(
                VerificationsChangedParams(activeFor(sessionId)),
            )
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to send active-changed for sessionId=$sessionId" }
        }
    }

    internal suspend fun <T> withPermit(block: suspend () -> T): T {
        gate.acquire()
        logger.info { "Verification permit acquired (available=$availablePermits/$maxPermits)" }
        try {
            return block()
        } finally {
            gate.release()
            logger.info { "Verification permit released (available=$availablePermits/$maxPermits)" }
        }
    }
}
