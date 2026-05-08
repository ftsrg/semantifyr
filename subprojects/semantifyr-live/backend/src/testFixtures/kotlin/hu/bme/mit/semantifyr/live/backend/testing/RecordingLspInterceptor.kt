/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspBridge
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspMessageInterceptor
import org.eclipse.lsp4j.jsonrpc.messages.Message

class RecordingLspInterceptor(
    private val consumeClient: Boolean = false,
    private val consumeServer: Boolean = false,
) : LspMessageInterceptor {
    val clientSeen = mutableListOf<String>()
    val serverSeen = mutableListOf<String>()

    override suspend fun interceptClientMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean {
        clientSeen += raw
        return consumeClient
    }

    override suspend fun interceptServerMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean {
        serverSeen += raw
        return consumeServer
    }
}
