/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import io.ktor.http.Headers
import io.ktor.http.headersOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClientAddressResolverTest {

    private fun resolver(
        header: String? = "X-Forwarded-For",
        trustedProxies: List<String> = listOf("172.30.0.254/32"),
    ): ClientAddressResolver {
        val config = BackendConfig(server = ServerConfig(clientAddressHeader = header, trustedProxies = trustedProxies))
        return ClientAddressResolver(config, TrustedProxies(config))
    }

    private fun forwarded(value: String): Headers {
        return headersOf("X-Forwarded-For", value)
    }

    @Test
    fun `without a configured header the peer address is the client`() {
        assertThat(resolver(header = null).resolve("172.30.0.254", forwarded("203.0.113.7")))
            .isEqualTo("172.30.0.254")
    }

    @Test
    fun `the proxy forwards the client address`() {
        assertThat(resolver().resolve("172.30.0.254", forwarded("203.0.113.7")))
            .isEqualTo("203.0.113.7")
    }

    @Test
    fun `any other peer cannot spoof the client address`() {
        assertThat(resolver().resolve("172.30.0.253", forwarded("203.0.113.7")))
            .isEqualTo("172.30.0.253")
        assertThat(resolver().resolve("172.30.0.9", forwarded("203.0.113.7")))
            .isEqualTo("172.30.0.9")
    }

    @Test
    fun `without trusted proxies nobody forwards the client address`() {
        assertThat(resolver(trustedProxies = emptyList()).resolve("172.30.0.254", forwarded("203.0.113.7")))
            .isEqualTo("172.30.0.254")
    }

    @Test
    fun `the proxy without the header is the client`() {
        assertThat(resolver().resolve("172.30.0.254", Headers.Empty))
            .isEqualTo("172.30.0.254")
    }

    @Test
    fun `a header value that is not a single address is ignored`() {
        assertThat(resolver().resolve("172.30.0.254", forwarded("example.org")))
            .isEqualTo("172.30.0.254")
        assertThat(resolver().resolve("172.30.0.254", forwarded("203.0.113.7, 198.51.100.1")))
            .isEqualTo("172.30.0.254")
    }

    @Test
    fun `a peer that is not an address literal is not trusted`() {
        assertThat(resolver().resolve("localhost", forwarded("203.0.113.7")))
            .isEqualTo("localhost")
    }

    @Test
    fun `ipv6 client addresses are forwarded`() {
        assertThat(resolver().resolve("172.30.0.254", forwarded("2001:db8::7")))
            .isEqualTo("2001:db8:0:0:0:0:0:7")
    }

    @Test
    fun `a subnet trusts every peer inside it`() {
        val resolver = resolver(trustedProxies = listOf("172.30.0.0/24"))
        assertThat(resolver.resolve("172.30.0.200", forwarded("203.0.113.7")))
            .isEqualTo("203.0.113.7")
        assertThat(resolver.resolve("172.30.1.2", forwarded("203.0.113.7")))
            .isEqualTo("172.30.1.2")
    }
}
