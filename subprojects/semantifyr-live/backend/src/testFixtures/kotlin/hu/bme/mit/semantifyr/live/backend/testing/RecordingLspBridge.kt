/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspBridge
import org.eclipse.lsp4j.jsonrpc.messages.Message

class RecordingLspBridge : LspBridge {
    val toServer = mutableListOf<Message>()
    val toServerRaw = mutableListOf<String>()
    val toClient = mutableListOf<Message>()
    val toClientRaw = mutableListOf<String>()
    var errorCount: Int = 0
        private set

    override suspend fun sendToLspServer(message: Message) {
        toServer += message
    }

    override suspend fun sendToLspServer(raw: String) {
        toServerRaw += raw
    }

    override suspend fun sendToLspClient(message: Message) {
        toClient += message
    }

    override suspend fun sendToLspClient(raw: String) {
        toClientRaw += raw
    }

    override fun recordError() {
        errorCount++
    }
}
