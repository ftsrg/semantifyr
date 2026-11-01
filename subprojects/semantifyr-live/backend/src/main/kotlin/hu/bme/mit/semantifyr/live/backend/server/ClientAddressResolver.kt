/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.utils.parseAddressOrNull
import io.ktor.http.Headers

@Singleton
class ClientAddressResolver @Inject constructor(
    config: BackendConfig,
    private val trustedProxies: TrustedProxies,
) {

    private val header = config.server.clientAddressHeader

    fun resolve(peerAddress: String, headers: Headers): String {
        if (header == null || !trustedProxies.isTrusted(peerAddress)) {
            return peerAddress
        }
        val forwarded = headers[header] ?: return peerAddress
        val client = parseAddressOrNull(forwarded.trim()) ?: return peerAddress
        return client.hostAddress
    }
}
