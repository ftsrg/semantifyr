/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationStatus
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.data.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageClient
import hu.bme.mit.semantifyr.live.backend.lsp.service.VerificationsChangedParams
import hu.bme.mit.semantifyr.live.backend.utils.currentMdcContext
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
import org.eclipse.lsp4j.services.LanguageClient
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.TimeSource

data class VerificationRequest(
    val requestId: String?,
    val kind: VerificationKind,
    val caseLabel: String,
    val portfolioId: String,
)

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
        val request: VerificationRequest,
        val sessionId: String,
        val startedAt: TimeSource.Monotonic.ValueTimeMark,
        val job: Job,
    )

    private val verifications = ConcurrentHashMap<String, Entry>()

    fun run(
        lspSession: LspSession,
        request: VerificationRequest,
        work: suspend () -> Any?,
    ): CompletableFuture<Any?> {
        val requestId = request.requestId ?: UUID.randomUUID().toString()
        val request = request.copy(requestId = requestId)
        val startedAt = TimeSource.Monotonic.markNow()

        return lspSession.coroutineScope.future {
            val mdcContext = currentMdcContext() + ("requestId" to requestId)
            withContext(mdcContext) {
                val entry = Entry(
                    request = request,
                    sessionId = lspSession.sessionId,
                    startedAt = startedAt,
                    job = coroutineContext.job,
                )
                verifications[requestId] = entry
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
                    erroredResult(request, message)
                } catch (e: CancellationException) {
                    logger.info(e) { "Verification cancelled" }
                    null
                } catch (e: Throwable) {
                    val message = "Verification failed: ${e.message ?: e::class.simpleName}"
                    logger.error(e) { message }
                    erroredResult(request, message)
                } finally {
                    verifications.remove(requestId)
                    notifyActiveChanged(lspSession.sessionId, lspSession.client())
                }
            }
        }
    }

    fun cancel(requestId: String): Boolean {
        val entry = verifications.remove(requestId) ?: return false
        entry.job.cancel()
        return true
    }

    fun cancelForSession(sessionId: String): Int {
        val matching = verifications.filterValues { it.sessionId == sessionId }
        for ((requestId, entry) in matching) {
            verifications.remove(requestId)
            entry.job.cancel()
        }
        return matching.size
    }

    fun activeFor(sessionId: String): List<ActiveVerificationInfo> {
        return verifications.values.filter {
            it.sessionId == sessionId
        }.map {
            it.toInfo()
        }
    }

    private fun Entry.toInfo(): ActiveVerificationInfo {
        return ActiveVerificationInfo(
            requestId = request.requestId!!,
            kind = request.kind,
            caseLabel = request.caseLabel,
            portfolioId = request.portfolioId,
            elapsed = startedAt.elapsedNow(),
        )
    }

    private fun erroredResult(request: VerificationRequest, message: String): VerificationCaseResult {
        return VerificationCaseResult(VerificationStatus.ERRORED, message, null, request.portfolioId, null, null)
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
