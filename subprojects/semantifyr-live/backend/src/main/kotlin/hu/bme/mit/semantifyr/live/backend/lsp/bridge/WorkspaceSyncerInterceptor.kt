/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.lsp.WorkspaceSyncer
import hu.bme.mit.semantifyr.live.backend.session.SessionScoped
import org.eclipse.lsp4j.jsonrpc.messages.Message

@SessionScoped
class WorkspaceSyncerInterceptor @Inject constructor(
    private val workspaceSyncer: WorkspaceSyncer,
) : LspMessageInterceptor {

    // FIXME: we should also sync the workspace in the other way around: when the lsp server changes something

    override suspend fun interceptClientMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean {
        workspaceSyncer.handleOutgoingMessage(message)
        return false
    }

}
