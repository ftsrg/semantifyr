/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage

object SemantifyrCommands {
    const val SESSION_INFO_COMMAND = "semantifyr.session.info"

    fun isCommand(message: RequestMessage, commandId: String): Boolean {
        val params = message.params as? ExecuteCommandParams

        return params?.command == commandId
    }

}
