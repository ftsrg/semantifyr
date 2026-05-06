/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.lsp.UriRewriter
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionControlInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionControlManager
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionInfoProvider
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionVerificationManager
import hu.bme.mit.semantifyr.live.backend.server.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.server.SessionInfo
import hu.bme.mit.semantifyr.live.backend.server.VerificationKind
import hu.bme.mit.semantifyr.live.backend.utils.MdcContext
import hu.bme.mit.semantifyr.live.backend.utils.currentMdcContext
import hu.bme.mit.semantifyr.live.backend.utils.withRequestId
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

// JSON-RPC reserved range (-32800..-32899) for server-defined errors; -32800 signals a request was cancelled.
private const val VERIFICATION_TIMEOUT_ERROR_CODE = -32800
const val VERIFICATION_ENQUEUED_NOTIFICATION = "semantifyr/verification/enqueued"

class VerificationTracker(
    val requestId: String,
    val sessionId: String,
    val kind: VerificationKind,
    val caseLabel: String?,
    val portfolioId: String?,
) {
    val startMark = TimeSource.Monotonic.markNow()
    private val result = CompletableDeferred<String>()

    fun complete(responseMessage: String) {
        result.complete(responseMessage)
    }

    fun cancel(cause: CancellationException) {
        result.cancel(cause)
    }

    suspend fun await(): String {
        return result.await()
    }
}

@SessionScoped
class LspSession @Inject constructor(
    private val context: SessionContext,
    private val config: BackendConfig,
    private val globalVerificationManager: GlobalVerificationManager,
    private val lspServerRunner: LspServerRawRunner,
    private val uriRewriter: UriRewriter,
    lspMessageProxyProvider: Provider<LspMessageProxy>,
) : AutoCloseable,
    SessionInfoProvider,
    SessionVerificationManager,
    SessionControlManager {

    private val logger by loggerFactory()

    val sessionId get() = context.sessionId
    val remoteIp get() = context.remoteIp
    val flavor get() = context.flavor

    private val startMark = TimeSource.Monotonic.markNow()
    private val verificationTrackers = mutableMapOf<String, VerificationTracker>()
    private val lspMessageProxy = lspMessageProxyProvider.get()
    private lateinit var coroutineScope: CoroutineScope
    private var started = false

    val activeVerifications get() = verificationTrackers.values.map {
        ActiveVerificationInfo(
            requestId = it.requestId,
            kind = it.kind,
            caseLabel = it.caseLabel,
            portfolioId = it.portfolioId,
            elapsed = it.startMark.elapsedNow(),
        )
    }

    override fun getSessionInfo(): SessionInfo {
        return SessionInfo(
            sessionId = sessionId,
            remoteIp = remoteIp,
            flavorId = flavor.id,
            uptime = startMark.elapsedNow(),
            workingDirectory = context.workingDirectoryPath.toString(),
            activeVerifications = activeVerifications,
            started = started,
            bridgeInfo = lspMessageProxy.getInfo(),
        )
    }

    suspend fun run() {
        check(!started) { "Session has already been started!" }
        started = true

        coroutineScope = CoroutineScope(currentCoroutineContext() + MdcContext("sessionId" to sessionId))
        coroutineScope.launch { doRun() }.join()
    }

    private suspend fun doRun() = coroutineScope {
        logger.info { "Starting session (flavor=${flavor.id}, workspace=${context.workingDirectoryPath})" }
        val runnerJob = launch { lspServerRunner.run() }
        val idleWatchdog = launch { watchForIdleEviction() }
        try {
            lspMessageProxy.run()
        } finally {
            runnerJob.cancel()
            idleWatchdog.cancel()
        }
    }

    private suspend fun watchForIdleEviction() {
        val idleTimeout = config.server.sessionIdleTimeout
        while (true) {
            delay(idleTimeout)
            val silentFor = lspMessageProxy.getInfo().timeSinceLastClientMessage
            if (silentFor >= idleTimeout) {
                logger.warn { "Evicting idle session (silent for $silentFor, threshold=$idleTimeout)" }
                coroutineScope.cancel(CancellationException("Idle session evicted"))
                return
            }
        }
    }

    override suspend fun enqueueVerification(
        requestId: String,
        requestMessage: String,
        kind: VerificationKind,
        caseLabel: String?,
        portfolioId: String?,
    ): Unit = withRequestId(requestId) {
        logger.info {
            "Verification request received (kind=$kind, case=${caseLabel ?: "?"}, portfolio=${portfolioId ?: "default"})"
        }

        val tracker = VerificationTracker(requestId, sessionId, kind, caseLabel, portfolioId)
        verificationTrackers[requestId] = tracker
        notifyInflightChanged()

        launchInSession {
            try {
                logger.info { "Verification request enqueued" }
                lspMessageProxy.sendToLspClient(enqueuedNotification(requestId))

                globalVerificationManager.withPermit {
                    logger.info { "Verification started" }

                    lspMessageProxy.sendToLspServer(uriRewriter.clientToServer(requestMessage))

                    val response = withTimeout(config.verification.timeout) {
                        tracker.await()
                    }
                    lspMessageProxy.sendToLspClient(response)
                }
            } catch (_: TimeoutCancellationException) {
                logger.warn { "Verification timed out (timeout=${config.verification.timeout})" }
                sendCancelToLspServer(requestId)
                lspMessageProxy.recordError()
                lspMessageProxy.sendToLspClient(timeoutError(requestId))
            } catch (_: CancellationException) {
                // Normal cancellation: admin cancel or session close.
            } finally {
                verificationTrackers.remove(requestId)
                notifyInflightChanged()
            }
        }
    }

    override fun listInFlight() = activeVerifications

    override suspend fun cancelInFlight(requestId: String) = cancelTracker(requestId, "Cancelled by client")

    override suspend fun cancelAllInFlight(): Int {
        val ids = verificationTrackers.keys.toList()
        if (ids.isEmpty()) {
            return 0
        }
        logger.info { "Cancelling ${ids.size} in-flight job(s) for session" }
        for (id in ids) {
            cancelTracker(id, "Cancelled by client (cancelAll)")
        }
        return ids.size
    }

    private suspend fun cancelTracker(requestId: String, reason: String): Boolean {
        val tracker = verificationTrackers[requestId] ?: return false
        logger.info { "Cancelling verification (requestId=$requestId, kind=${tracker.kind}, case=${tracker.caseLabel ?: "?"}, reason=$reason)" }
        sendCancelToLspServer(requestId)
        tracker.cancel(CancellationException(reason))
        return true
    }

    private fun notifyInflightChanged() {
        if (!started) {
            return
        }
        val notification = SessionControlInterceptor.changedNotification(activeVerifications)
        launchInSession {
            lspMessageProxy.sendToLspClient(notification)
        }
    }

    private fun launchInSession(block: suspend CoroutineScope.() -> Unit): Job {
        return coroutineScope.launch(currentMdcContext(), block = block)
    }

    suspend fun sendCancelToLspServer(requestId: String) {
        logger.info { "Sending \$/cancelRequest to LSP server (requestId=$requestId)" }
        val notification = NotificationMessage().apply {
            method = "\$/cancelRequest"
            params = mapOf("id" to requestId)
        }
        lspMessageProxy.sendToLspServer(notification)
    }

    override fun isVerificationTracked(requestId: String): Boolean {
        return requestId in verificationTrackers
    }

    override suspend fun completeVerification(requestId: String, responseMessage: String) {
        val tracker = verificationTrackers[requestId] ?: return
        logger.info { "Verification completed (requestId=$requestId)" }
        tracker.complete(responseMessage)
    }

    override suspend fun cancelVerification(requestId: String) {
        cancelTracker(requestId, "Verification cancelled")
    }

    override fun close() {
        logger.info { "Closing session (sessionId=$sessionId)" }
        if (started) {
            coroutineScope.cancel(CancellationException("Session terminated"))
        }
    }

    private fun timeoutError(requestId: String): ResponseMessage {
        return ResponseMessage().apply {
            id = requestId
            error = ResponseError(VERIFICATION_TIMEOUT_ERROR_CODE, "Verification timed out", null)
        }
    }

    private fun enqueuedNotification(requestId: String): NotificationMessage {
        return NotificationMessage().apply {
            method = VERIFICATION_ENQUEUED_NOTIFICATION
            params = mapOf("requestId" to requestId)
        }
    }
}
