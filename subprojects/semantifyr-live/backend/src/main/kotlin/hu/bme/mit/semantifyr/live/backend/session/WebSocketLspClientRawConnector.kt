/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspClientRawConnector
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException

class WebSocketLspClientRawConnector(
    private val webSocketSession: WebSocketSession,
) : LspClientRawConnector {

    override suspend fun receiveFromClient(): String? {
        while (true) {
            try {
                val frame = webSocketSession.incoming.receive()
                if (frame is Frame.Text) {
                    return frame.readText()
                }
            } catch (_: ClosedReceiveChannelException) {
                return null
            }
        }
    }

    override suspend fun sendToClient(raw: String) {
        webSocketSession.send(Frame.Text(raw))
    }
}
