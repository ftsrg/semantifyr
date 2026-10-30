/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.utils.AddressRange
import io.ktor.http.Headers
import java.net.InetAddress

@Singleton
class ClientAddressResolver @Inject constructor(
    config: BackendConfig,
) {

    private val header = config.server.clientAddressHeader

    private val trustedProxies = config.server.trustedProxies.map {
        AddressRange.parse(it)
    }

    fun resolve(peerAddress: String, headers: Headers): String {
        if (header == null) {
            return peerAddress
        }
        val peer = parseAddressOrNull(peerAddress) ?: return peerAddress
        if (trustedProxies.none { it.contains(peer) }) {
            return peerAddress
        }
        val forwarded = headers[header] ?: return peerAddress
        val client = parseAddressOrNull(forwarded.trim()) ?: return peerAddress
        return client.hostAddress
    }

    private fun parseAddressOrNull(literal: String): InetAddress? {
        return try {
            InetAddress.ofLiteral(literal)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
