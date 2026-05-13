/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.transport

import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.lsp.adapters.VerificationKindTypeAdapter
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler

fun createLspMessageJsonHandler(
    supportedMethods: Map<String, JsonRpcMethod> = emptyMap(),
): MessageJsonHandler {
    return MessageJsonHandler(supportedMethods) {
        CommandGson.configure(it)
        it.registerTypeAdapter(VerificationKind::class.java, VerificationKindTypeAdapter())
    }
}
