/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage

object LspMessages {

    fun request(
        id: String,
        method: String,
        params: Any? = null,
    ): RequestMessage {
        return RequestMessage().apply {
            this.id = id
            this.method = method
            this.params = params
        }
    }

    fun notification(
        method: String,
        params: Any? = null,
    ): NotificationMessage {
        return NotificationMessage().apply {
            this.method = method
            this.params = params
        }
    }

    fun response(
        id: String,
        result: Any? = null,
    ): ResponseMessage {
        return ResponseMessage().apply {
            this.id = id
            this.result = result
        }
    }

    fun executeCommand(
        id: String,
        command: String,
        arguments: List<Any> = emptyList(),
    ): RequestMessage {
        return request(id, "workspace/executeCommand", ExecuteCommandParams(command, arguments))
    }

    fun didOpen(
        uri: String,
        text: String,
        languageId: String = "oxsts",
        version: Int = 1,
    ): NotificationMessage {
        return notification(
            method = "textDocument/didOpen",
            params = DidOpenTextDocumentParams(
                TextDocumentItem().apply {
                    this.uri = uri
                    this.languageId = languageId
                    this.version = version
                    this.text = text
                },
            ),
        )
    }

    fun publishDiagnostics(uri: String): NotificationMessage {
        return notification(
            method = "textDocument/publishDiagnostics",
            params = mapOf("uri" to uri, "diagnostics" to emptyList<Any>()),
        )
    }
}
