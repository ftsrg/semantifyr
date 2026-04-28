/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.utils

import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler

val lspMessageHandler = MessageJsonHandler(
    mapOf(
        "textDocument/didOpen" to JsonRpcMethod.notification(
            "textDocument/didOpen",
            DidOpenTextDocumentParams::class.java,
        ),
        "textDocument/didChange" to JsonRpcMethod.notification(
            "textDocument/didChange",
            DidChangeTextDocumentParams::class.java,
        ),
        "workspace/executeCommand" to JsonRpcMethod.request(
            "workspace/executeCommand",
            Any::class.java,
            ExecuteCommandParams::class.java,
        ),
    ),
)
