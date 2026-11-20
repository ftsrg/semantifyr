/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.testing.handler
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val USER_HEADER = "X-Auth-Request-User"

class AdminProxyTrustTest {

    @Test
    suspend fun `over the network the user header counts only from the configured proxy address`() {
        assertThat(whoamiFromLoopback(ServerConfig(userHeader = USER_HEADER, trustedProxies = listOf("127.0.0.1/32")))).isEqualTo(HttpStatusCode.OK)
        assertThat(whoamiFromLoopback(ServerConfig(userHeader = USER_HEADER, trustedProxies = listOf("172.30.0.254/32")))).isEqualTo(HttpStatusCode.Forbidden)
    }

    @Test
    suspend fun `the admin API is off without a trusted proxy`() {
        assertThat(whoamiFromLoopback(ServerConfig(userHeader = USER_HEADER))).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(whoamiFromLoopback(ServerConfig())).isEqualTo(HttpStatusCode.Forbidden)
    }

    private suspend fun whoamiFromLoopback(serverConfig: ServerConfig): HttpStatusCode {
        val handler = testInjector(BackendConfig(server = serverConfig)).handler<AdminHandler>()
        val server = embeddedServer(Netty, port = 0) {
            install(ContentNegotiation) {
                json()
            }
            with(handler) {
                configure()
            }
        }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().first().port
            return HttpClient(CIO).use {
                it.get("http://127.0.0.1:$port/api/admin/whoami") {
                    header(USER_HEADER, "octocat")
                }.status
            }
        } finally {
            server.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        }
    }
}
