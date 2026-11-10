/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.data.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.data.AdminConfigResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminStatusResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminWhoamiResponse
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.data.VerificationState
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionManager
import hu.bme.mit.semantifyr.live.backend.testing.handler
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.seconds

private const val USER_HEADER = "X-Auth-Request-User"

class AdminHandlerTest {

    private val sessionManager = mock<SessionManager>()

    private fun signedIn(user: String?): ProxyUserResolver {
        return mock {
            on { resolve(any(), any()) } doReturn user
        }
    }

    private fun createHandler(
        sessionInfos: List<SessionInfo> = emptyList(),
        config: BackendConfig = BackendConfig(),
        proxyUserResolver: ProxyUserResolver? = signedIn("octocat"),
    ): AdminHandler {
        whenever(sessionManager.getSessionInfos()).thenReturn(sessionInfos)
        whenever(sessionManager.cancelSession("test-session")).thenReturn(true)
        whenever(sessionManager.cancelSession("nonexistent")).thenReturn(false)
        whenever(sessionManager.cancelVerification("req-1")).thenReturn(true)
        whenever(sessionManager.cancelVerification("nonexistent")).thenReturn(false)

        return testInjector(config) {
            bind(SessionManager::class.java).toInstance(sessionManager)
            if (proxyUserResolver != null) {
                bind(ProxyUserResolver::class.java).toInstance(proxyUserResolver)
            }
        }.handler<AdminHandler>()
    }

    private fun ApplicationTestBuilder.installAdmin(handler: AdminHandler) {
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }
    }

    private suspend fun ApplicationTestBuilder.everyAdminRouteAnswers(status: HttpStatusCode) {
        val responses = listOf(
            client.get("/api/admin/whoami") {
                header(USER_HEADER, "octocat")
            },
            client.get("/api/admin/status") {
                header(USER_HEADER, "octocat")
            },
            client.get("/api/admin/config") {
                header(USER_HEADER, "octocat")
            },
            client.delete("/api/admin/sessions/test-session") {
                header(USER_HEADER, "octocat")
            },
            client.delete("/api/admin/verifications/req-1") {
                header(USER_HEADER, "octocat")
            },
        )
        assertThat(
            responses.map {
                it.status
            },
        ).containsOnly(status)
    }

    @Test
    fun `whoami names the user the proxy signed in`() = testApplication {
        installAdmin(createHandler())

        val response = jsonClient().get("/api/admin/whoami")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<AdminWhoamiResponse>().user).isEqualTo("octocat")
    }

    @Test
    fun `every admin route answers 403 when the proxy signed nobody in`() = testApplication {
        installAdmin(createHandler(proxyUserResolver = signedIn(null)))

        everyAdminRouteAnswers(HttpStatusCode.Forbidden)
        verify(sessionManager, never()).cancelSession(any())
        verify(sessionManager, never()).cancelVerification(any())
    }

    @Test
    fun `status endpoint returns sessions`() = testApplication {
        val testSession = SessionInfo(
            sessionId = "test-session",
            remoteIp = "127.0.0.1",
            flavorId = "oxsts",
            kind = "embed",
            uptime = 30.seconds,
            workingDirectory = "/tmp/test",
            activeVerifications = listOf(
                ActiveVerificationInfo(verificationId = "req-1", portfolioId = "smart-full", kind = VerificationKind.Verify, state = VerificationState.Running),
            ),
        )
        installAdmin(createHandler(sessionInfos = listOf(testSession)))

        val response = jsonClient().get("/api/admin/status")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val status = response.body<AdminStatusResponse>()
        assertThat(status.sessions).hasSize(1)
        assertThat(status.sessions[0].sessionId).isEqualTo("test-session")
        assertThat(status.sessions[0].remoteIp).isEqualTo("127.0.0.1")
        assertThat(status.sessions[0].flavorId).isEqualTo("oxsts")
        assertThat(status.sessions[0].kind).isEqualTo("embed")
        assertThat(status.sessions[0].activeVerifications).hasSize(1)
        assertThat(status.sessions[0].activeVerifications[0].verificationId).isEqualTo("req-1")
        assertThat(status.sessions[0].activeVerifications[0].portfolioId).isEqualTo("smart-full")
    }

    @Test
    fun `config endpoint returns server configuration`() = testApplication {
        installAdmin(createHandler())

        val response = jsonClient().get("/api/admin/config")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val config = response.body<AdminConfigResponse>()
        assertThat(config.sessionManager.maxSessionsGlobal).isEqualTo(256)
        assertThat(config.verification.concurrency).isEqualTo(4)
        assertThat(config.server.port).isEqualTo(8080)
    }

    @Test
    fun `cancel session returns 200 for an existing session and 404 otherwise`() = testApplication {
        installAdmin(createHandler())

        assertThat(client.delete("/api/admin/sessions/test-session").status).isEqualTo(HttpStatusCode.OK)
        assertThat(client.delete("/api/admin/sessions/nonexistent").status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `cancel verification returns 200 for an existing verification and 404 otherwise`() = testApplication {
        installAdmin(createHandler())

        assertThat(client.delete("/api/admin/verifications/req-1").status).isEqualTo(HttpStatusCode.OK)
        assertThat(client.delete("/api/admin/verifications/nonexistent").status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    suspend fun `over the network the user header counts only from the configured proxy address`() {
        val (trustedStatus, trustedBody) = whoamiFromLoopback(ServerConfig(userHeader = USER_HEADER, trustedProxies = listOf("127.0.0.1/32")))
        assertThat(trustedStatus).isEqualTo(HttpStatusCode.OK)
        assertThat(trustedBody).contains("octocat")

        val (otherStatus, _) = whoamiFromLoopback(ServerConfig(userHeader = USER_HEADER, trustedProxies = listOf("172.30.0.254/32")))
        assertThat(otherStatus).isEqualTo(HttpStatusCode.Forbidden)
    }

    @Test
    suspend fun `the admin API is off without a trusted proxy`() {
        assertThat(whoamiFromLoopback(ServerConfig(userHeader = USER_HEADER)).first).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(whoamiFromLoopback(ServerConfig()).first).isEqualTo(HttpStatusCode.Forbidden)
    }

    private suspend fun whoamiFromLoopback(serverConfig: ServerConfig): Pair<HttpStatusCode, String> {
        val config = BackendConfig(server = serverConfig)
        val handler = createHandler(config = config, proxyUserResolver = null)
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
                val response = it.get("http://127.0.0.1:$port/api/admin/whoami") {
                    header(USER_HEADER, "octocat")
                }
                response.status to response.bodyAsText()
            }
        } finally {
            server.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        }
    }
}
