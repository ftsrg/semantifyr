/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import hu.bme.mit.semantifyr.live.backend.lsp.createLspMessageHandler
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.messages.Message

val testLspMessageHandler = createLspMessageHandler()

fun Message.serialize(): String {
    return testLspMessageHandler.serialize(this)
}

fun parseLspMessage(raw: String): Message {
    return testLspMessageHandler.parseMessage(raw)
}
