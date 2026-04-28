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
    suspend fun handleClientMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean = true
    suspend fun handleServerMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean = true
}

interface LspBridge {
    suspend fun sendToLspServer(message: Message)
    suspend fun sendToLspServer(raw: String)

    suspend fun sendToLspClient(message: Message)
    suspend fun sendToLspClient(raw: String)

    fun recordError()
}
