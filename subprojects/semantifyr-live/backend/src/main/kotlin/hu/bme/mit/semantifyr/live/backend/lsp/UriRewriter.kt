/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

class UriRewriter(
    val clientUri: String,
    val serverUri: String,
) {

    fun clientToServer(message: String): String {
        return message.replace(clientUri, serverUri)
    }

    fun serverToClient(message: String): String {
        return message.replace(serverUri, clientUri)
    }
}
