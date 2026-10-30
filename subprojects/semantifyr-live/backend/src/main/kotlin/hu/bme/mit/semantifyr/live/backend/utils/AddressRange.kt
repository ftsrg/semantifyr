/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.utils

import java.net.InetAddress

class AddressRange private constructor(
    private val network: ByteArray,
    private val prefixLength: Int,
) {

    fun contains(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != network.size) {
            return false
        }
        val fullBytes = prefixLength / 8
        for (index in 0 until fullBytes) {
            if (bytes[index] != network[index]) {
                return false
            }
        }
        val remainingBits = prefixLength % 8
        if (remainingBits == 0) {
            return true
        }
        val mask = (0xFF shl (8 - remainingBits)) and 0xFF
        return (bytes[fullBytes].toInt() and mask) == (network[fullBytes].toInt() and mask)
    }

    companion object {
        fun parse(range: String): AddressRange {
            val network = InetAddress.ofLiteral(range.substringBefore('/')).address
            val prefixLength = if ('/' in range) {
                range.substringAfter('/').toInt()
            } else {
                network.size * 8
            }
            require(prefixLength in 0..network.size * 8) {
                "Invalid prefix length in $range"
            }
            return AddressRange(network, prefixLength)
        }
    }
}
