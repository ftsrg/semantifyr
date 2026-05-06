/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import com.google.gson.JsonParser
import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.server.SessionInfo
import hu.bme.mit.semantifyr.live.backend.session.SessionScoped
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlinx.serialization.json.Json
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage

interface SessionInfoProvider {
    fun getSessionInfo(): SessionInfo
}

@SessionScoped
class SessionInfoMessageInterceptor @Inject constructor(
    private val sessionInfoProvider: SessionInfoProvider,
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

        val params = message.params as? ExecuteCommandParams ?: return false
        if (params.command != SemantifyrCommands.SESSION_INFO_COMMAND) {
            return false
        }

        logger.info { "Intercepted ${SemantifyrCommands.SESSION_INFO_COMMAND} request" }

        val response = ResponseMessage().apply {
            rawId = message.rawId
            result = JsonParser.parseString(Json.encodeToString(sessionInfoProvider.getSessionInfo()))
        }

        bridge.sendToLspClient(response)

        return true
    }
}
