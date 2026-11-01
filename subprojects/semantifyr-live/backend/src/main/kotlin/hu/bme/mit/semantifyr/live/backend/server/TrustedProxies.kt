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
import hu.bme.mit.semantifyr.live.backend.utils.parseAddressOrNull

@Singleton
class TrustedProxies @Inject constructor(
    config: BackendConfig,
) {

    private val ranges = config.server.trustedProxies.map {
        AddressRange.parse(it)
    }

    fun isTrusted(peerAddress: String): Boolean {
        val peer = parseAddressOrNull(peerAddress) ?: return false
        return ranges.any {
            it.contains(peer)
        }
    }
}
