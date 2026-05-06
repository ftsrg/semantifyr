/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspClientRawConnector
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException

@SessionScoped
class WebSocketLspClientRawConnector @Inject constructor(
    context: SessionContext,
) : LspClientRawConnector {

    private val webSocketSession = context.webSocketSession

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
