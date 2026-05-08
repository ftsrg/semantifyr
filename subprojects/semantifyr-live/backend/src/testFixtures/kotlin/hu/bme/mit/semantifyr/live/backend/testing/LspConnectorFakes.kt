/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspClientRawConnector
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspServerRawConnector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel

class FakeLspClientRawConnector : LspClientRawConnector {
    private val incoming = Channel<String>(Channel.UNLIMITED)
    val sentToClient = Channel<String>(Channel.UNLIMITED)

    override suspend fun receiveFromClient(): String? {
        return runCatching {
            incoming.receive()
        }.getOrNull()
    }

    override suspend fun sendToClient(raw: String) {
        sentToClient.send(raw)
    }

    fun simulateClientSent(raw: String) {
        incoming.trySend(raw)
    }

    fun closeIncoming() {
        incoming.close()
    }
}

class FakeLspServerRawConnector : LspServerRawConnector {
    private val incoming = Channel<String>(Channel.UNLIMITED)
    val sentToServer = Channel<String>(Channel.UNLIMITED)

    override suspend fun sendToServer(raw: String) {
        sentToServer.send(raw)
    }

    override suspend fun receiveFromServer(): String {
        val result = incoming.receiveCatching()
        return result.getOrNull() ?: throw CancellationException("server channel closed")
    }

    fun simulateServerSent(raw: String) {
        incoming.trySend(raw)
    }
}
