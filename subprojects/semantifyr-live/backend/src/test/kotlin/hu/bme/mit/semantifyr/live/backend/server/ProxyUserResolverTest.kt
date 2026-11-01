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

class ProxyUserResolverTest {

    private fun resolver(
        header: String? = "X-Auth-Request-User",
        trustedProxies: List<String> = listOf("172.30.0.254/32"),
    ): ProxyUserResolver {
        val config = BackendConfig(server = ServerConfig(userHeader = header, trustedProxies = trustedProxies))
        return ProxyUserResolver(config, TrustedProxies(config))
    }

    private fun user(value: String): Headers {
        return headersOf("X-Auth-Request-User", value)
    }

    @Test
    fun `the proxy names the user`() {
        assertThat(resolver().resolve("172.30.0.254", user("octocat"))).isEqualTo("octocat")
    }

    @Test
    fun `the header is rejected from any other peer`() {
        assertThat(resolver().resolve("172.30.0.2", user("octocat"))).isNull()
        assertThat(resolver().resolve("172.30.0.9", user("octocat"))).isNull()
        assertThat(resolver().resolve("203.0.113.7", user("octocat"))).isNull()
        assertThat(resolver().resolve("localhost", user("octocat"))).isNull()
    }

    @Test
    fun `without trusted proxies nobody is signed in`() {
        assertThat(resolver(trustedProxies = emptyList()).resolve("172.30.0.254", user("octocat"))).isNull()
    }

    @Test
    fun `without a configured header nobody is signed in`() {
        assertThat(resolver(header = null).resolve("172.30.0.254", user("octocat"))).isNull()
    }

    @Test
    fun `the proxy without the header or with a blank one signs nobody in`() {
        assertThat(resolver().resolve("172.30.0.254", Headers.Empty)).isNull()
        assertThat(resolver().resolve("172.30.0.254", user("  "))).isNull()
    }
}
