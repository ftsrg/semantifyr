/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.InetAddress

class AddressRangeTest {

    private fun address(literal: String): InetAddress {
        return InetAddress.ofLiteral(literal)
    }

    @Test
    fun `a single address without a prefix matches only itself`() {
        val range = AddressRange.parse("172.30.0.2")
        assertThat(range.contains(address("172.30.0.2"))).isTrue()
        assertThat(range.contains(address("172.30.0.3"))).isFalse()
    }

    @Test
    fun `a prefix that is not a byte boundary masks the partial byte`() {
        val range = AddressRange.parse("10.0.0.0/12")
        assertThat(range.contains(address("10.15.255.255"))).isTrue()
        assertThat(range.contains(address("10.16.0.0"))).isFalse()
    }

    @Test
    fun `a zero prefix matches every address of the family`() {
        val range = AddressRange.parse("0.0.0.0/0")
        assertThat(range.contains(address("203.0.113.7"))).isTrue()
        assertThat(range.contains(address("2001:db8::7"))).isFalse()
    }

    @Test
    fun `ipv6 ranges match ipv6 addresses`() {
        val range = AddressRange.parse("2001:db8::/32")
        assertThat(range.contains(address("2001:db8:1::1"))).isTrue()
        assertThat(range.contains(address("2001:db9::1"))).isFalse()
        assertThat(range.contains(address("172.30.0.2"))).isFalse()
    }

    @Test
    fun `malformed ranges are rejected`() {
        assertThatThrownBy {
            AddressRange.parse("cloudflared/32")
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            AddressRange.parse("172.30.0.2/33")
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            AddressRange.parse("172.30.0.2/x")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
