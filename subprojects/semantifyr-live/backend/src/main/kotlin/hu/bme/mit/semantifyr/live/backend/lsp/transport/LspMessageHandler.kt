/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.transport

import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.data.VerificationState
import hu.bme.mit.semantifyr.live.backend.lsp.adapters.DurationTypeAdapter
import hu.bme.mit.semantifyr.live.backend.lsp.adapters.VerificationKindTypeAdapter
import hu.bme.mit.semantifyr.live.backend.lsp.adapters.VerificationStateTypeAdapter
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import kotlin.time.Duration

fun createLspMessageJsonHandler(
    supportedMethods: Map<String, JsonRpcMethod> = emptyMap(),
): MessageJsonHandler {
    return MessageJsonHandler(supportedMethods) {
        CommandGson.configure(it)
        it.registerTypeAdapter(Duration::class.java, DurationTypeAdapter())
        it.registerTypeAdapter(VerificationKind::class.java, VerificationKindTypeAdapter())
        it.registerTypeAdapter(VerificationState::class.java, VerificationStateTypeAdapter())
    }
}
