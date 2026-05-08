/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.server.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.server.SessionInfo
import hu.bme.mit.semantifyr.live.backend.session.SessionScoped
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage

interface SessionInfoProvider {
    fun getSessionInfo(): SessionInfo
}

interface SessionControlManager {
    fun listInFlight(): List<ActiveVerificationInfo>
    suspend fun cancelInFlight(requestId: String): Boolean
    suspend fun cancelAllInFlight(): Int
}

@SessionScoped
class SemantifyrLiveMethodInterceptor @Inject constructor(
    private val sessionInfoProvider: SessionInfoProvider,
    private val sessionControlManager: SessionControlManager,
) : LspMessageInterceptor {

    private val logger by loggerFactory()

    override suspend fun interceptClientMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean {
        if (message !is RequestMessage) {
            return false
        }
        when (message.method) {
            SemantifyrLiveMethods.SESSION_INFO -> {
                logger.info { "Intercepted ${message.method} (id=${message.id})" }
                respond(bridge, message, sessionInfoProvider.getSessionInfo())
            }
            SemantifyrLiveMethods.INFLIGHT_LIST -> {
                logger.info { "Intercepted ${message.method} (id=${message.id})" }
                respond(bridge, message, InflightChangedParams(sessionControlManager.listInFlight()))
            }
            SemantifyrLiveMethods.INFLIGHT_CANCEL -> {
                val params = message.params as InflightCancelParams
                logger.info { "Intercepted ${message.method} (id=${message.id}, requestId=${params.requestId})" }
                respond(bridge, message, sessionControlManager.cancelInFlight(params.requestId))
            }
            SemantifyrLiveMethods.INFLIGHT_CANCEL_ALL -> {
                logger.info { "Intercepted ${message.method} (id=${message.id})" }
                respond(bridge, message, sessionControlManager.cancelAllInFlight())
            }
            else -> return false
        }
        return true
    }

    private suspend fun respond(
        bridge: LspBridge,
        request: RequestMessage,
        result: Any?,
    ) {
        val response = ResponseMessage().apply {
            rawId = request.rawId
            this.result = result
        }
        bridge.sendToLspClient(response)
    }
}
