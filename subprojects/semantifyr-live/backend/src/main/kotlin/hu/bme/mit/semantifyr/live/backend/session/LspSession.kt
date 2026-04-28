/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.name.Named
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.lsp.UriRewriter
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionInfoProvider
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionVerificationManager
import hu.bme.mit.semantifyr.live.backend.server.SessionInfo
import hu.bme.mit.semantifyr.live.backend.utils.currentMdcContext
import hu.bme.mit.semantifyr.live.backend.utils.withRequestId
import hu.bme.mit.semantifyr.live.backend.utils.withSessionId
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

// JSON-RPC reserved range (-32800..-32899) for server-defined errors; -32800 signals a request was cancelled.
private const val VERIFICATION_TIMEOUT_ERROR_CODE = -32800
const val VERIFICATION_ENQUEUED_NOTIFICATION = "semantifyr/verification/enqueued"

class VerificationTracker(
    val requestId: String,
    val sessionId: String,
) {
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
    val flavor: Flavor,
    @param:Named("remoteIp") val remoteIp: String,
    @param:Named("sessionId") val sessionId: String,
    @param:Named("workingDirectoryPath") private val workingDirectoryPath: Path,
    private val config: BackendConfig,
    private val globalVerificationManager: GlobalVerificationManager,
    private val lspServerRunner: LspServerRawRunner,
    private val uriRewriter: UriRewriter,
    // Provider breaks the Guice construction cycle (proxy -> interceptors -> this session).
    // By the time the proxy is resolved, this session is already in the scope cache, so the
    // interceptors can inject the session-backed interfaces directly without needing proxies.
    private val lspMessageProxyProvider: Provider<LspMessageProxy>,
) : AutoCloseable,
    SessionInfoProvider,
    SessionVerificationManager {

    private val logger by loggerFactory()

    private val startMark = TimeSource.Monotonic.markNow()
    private val verificationTrackers = mutableMapOf<String, VerificationTracker>()
    private lateinit var coroutineScope: CoroutineScope
    private var started = false

    // Resolved eagerly after LspSession is placed in the SessionScope cache, so the provider
    // chain (LspMessageProxy -> List<Interceptor> -> SessionInfoProvider -> this session) resolves
    // cleanly without Guice needing to proxy interfaces for cycle breaking.
    private lateinit var lspMessageProxy: LspMessageProxy

    /**
     * Called by [SessionManager] inside the session scope after construction so the
     * provider chain can resolve while the scope is still active.
     */
    internal fun initialize() {
        lspMessageProxy = lspMessageProxyProvider.get()
    }

    val activeVerificationIds: Set<String>
        get() = verificationTrackers.keys.toSet()

    override fun getSessionInfo(): SessionInfo {
        return SessionInfo(
            sessionId = sessionId,
            remoteIp = remoteIp,
            flavorId = flavor.id,
            uptime = startMark.elapsedNow(),
            workingDirectory = workingDirectoryPath.toString(),
            activeVerifications = activeVerificationIds,
            started = started,
            bridgeInfo = lspMessageProxy.getInfo(),
        )
    }

    suspend fun run() = withSessionId(sessionId) {
        check(!started) { "Session has already been started!" }
        started = true

        logger.info { "Starting session (flavor=${flavor.id}, workspace=$workingDirectoryPath)" }

        // Snapshot the surrounding MDC (sessionId, possibly more from a parent withSessionId/...)
        // so it propagates across coroutine suspensions inside this scope.
        coroutineScope = CoroutineScope(currentCoroutineContext() + currentMdcContext())
        coroutineScope.launch { doRun() }.join()
    }

    private suspend fun doRun() = coroutineScope {
        val runnerJob = launch { lspServerRunner.run() }
        val idleWatchdog = launch { watchForIdleEviction() }
        try {
            lspMessageProxy.run()
        } finally {
            runnerJob.cancel()
            idleWatchdog.cancel()
        }
    }

    /**
     * Evicts the session if the client has been silent for longer than
     * [ServerConfig.sessionIdleTimeout][hu.bme.mit.semantifyr.live.backend.ServerConfig.sessionIdleTimeout].
     * This threshold must be configured larger than [VerificationConfig.timeout] so long
     * verifications (which legitimately produce client silence) don't get evicted.
     */
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

    // --- SessionVerificationManager ---

    override suspend fun enqueueVerification(requestId: String, requestMessage: String): Unit = withRequestId(requestId) {
        logger.info { "Verification request received" }

        val tracker = VerificationTracker(requestId, sessionId)
        verificationTrackers[requestId] = tracker

        // currentMdcContext() snapshots the surrounding withSessionId/withRequestId so the
        // launched job's log lines inherit them across coroutine suspensions.
        coroutineScope.launch(currentMdcContext()) {
            try {
                logger.info { "Verification request enqueued" }
                lspMessageProxy.sendToLspClient(enqueuedNotification(requestId))

                globalVerificationManager.withPermit {
                    logger.info { "Verification started" }
                    // Interceptors capture the client-form raw message before the proxy's
                    // per-message rewrite runs, so the deferred forward here has to rewrite
                    // URIs itself; otherwise the LSP server sees file:///workspace/... paths.
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
            }
        }
    }

    suspend fun sendCancelToLspServer(requestId: String) {
        logger.info { "Sending $/cancelRequest to LSP server (requestId=$requestId)" }
        val notification = NotificationMessage().apply {
            method = "$/cancelRequest"
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
        val tracker = verificationTrackers[requestId] ?: return
        logger.info { "Cancelling verification (requestId=$requestId)" }
        tracker.cancel(CancellationException("Verification cancelled"))
    }

    override fun close() = withSessionId(sessionId) {
        logger.info { "Closing session" }
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
