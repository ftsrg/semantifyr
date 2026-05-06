/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import org.eclipse.lsp4j.jsonrpc.messages.Message

interface LspClientRawConnector {
    suspend fun sendToClient(raw: String)
    suspend fun receiveFromClient(): String?
}

interface LspServerRawConnector {
    suspend fun sendToServer(raw: String)
    suspend fun receiveFromServer(): String?
}

interface LspMessageInterceptor {
    /**
     * Returns `true` if the interceptor consumed the message.
     */
    suspend fun interceptClientMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean = false

    /**
     * Returns `true` if the interceptor consumed the message.
     */
    suspend fun interceptServerMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean = false
}

interface LspBridge {
    suspend fun sendToLspServer(message: Message)
    suspend fun sendToLspServer(raw: String)

    suspend fun sendToLspClient(message: Message)
    suspend fun sendToLspClient(raw: String)

    fun recordError()
}
