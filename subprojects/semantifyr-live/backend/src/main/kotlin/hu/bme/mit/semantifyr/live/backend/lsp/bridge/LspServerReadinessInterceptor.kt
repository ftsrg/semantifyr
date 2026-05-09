/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.session.SessionScoped
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlinx.coroutines.CompletableDeferred
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage

private const val PUBLISH_DIAGNOSTICS_METHOD = "textDocument/publishDiagnostics"

@SessionScoped
class LspServerReadinessInterceptor @Inject constructor() : LspMessageInterceptor {

    private val logger by loggerFactory()

    private val ready = CompletableDeferred<Unit>()

    suspend fun awaitReady() {
        ready.await()
    }

    override suspend fun interceptServerMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean {
        if (ready.isCompleted) {
            return false
        }
        if (message is NotificationMessage && message.method == PUBLISH_DIAGNOSTICS_METHOD) {
            logger.info { "LSP server emitted first publishDiagnostics, signalling readiness" }
            ready.complete(Unit)
        }
        return false
    }
}
