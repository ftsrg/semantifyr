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
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.data.SessionLspInfo
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionManager
import hu.bme.mit.semantifyr.live.backend.testing.handler
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Base64
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class AdminHandlerTest {

    private val adminPassword = "test-password"

    private fun basicAuth(username: String = "admin", password: String = adminPassword): String {
        return "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    }

    private fun createHandler(
        sessionInfos: List<SessionInfo> = emptyList(),
    ): AdminHandler {
        val config = BackendConfig(
            server = ServerConfig(adminPassword = adminPassword, httpsOnlyCookies = false),
        )
        val sessionManager = mock(SessionManager::class.java)
        `when`(sessionManager.getSessionInfos()).thenReturn(sessionInfos)
        `when`(sessionManager.cancelSession("test-session")).thenReturn(true)
        `when`(sessionManager.cancelSession("nonexistent")).thenReturn(false)
        `when`(sessionManager.cancelVerification("test-session", "req-1")).thenReturn(true)
        `when`(sessionManager.cancelVerification("test-session", "nonexistent")).thenReturn(false)

        return testInjector(config) {
            bind(SessionManager::class.java).toInstance(sessionManager)
        }.handler<AdminHandler>()
    }

    @Test
    fun `status endpoint returns 401 without auth`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }

        val response = client.get("/api/admin/status")
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `status endpoint returns 401 with wrong password`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }

        val response = client.get("/api/admin/status") {
            header(HttpHeaders.Authorization, basicAuth(password = "wrong"))
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `status endpoint returns sessions with valid auth`() = testApplication {
        val testSession = SessionInfo(
            sessionId = "test-session",
            remoteIp = "127.0.0.1",
            flavorId = "oxsts",
            uptime = 30.seconds,
            workingDirectory = "/tmp/test",
            activeVerifications = listOf(ActiveVerificationInfo(requestId = "req-1", portfolioId = "smart-full")),
            sessionLspInfo = SessionLspInfo(
                timeSinceLastClientMessage = 2.seconds,
                timeSinceLastServerMessage = 1.seconds,
            ),
        )
        val handler = createHandler(sessionInfos = listOf(testSession))
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }
        val client = jsonClient()

        val response = client.get("/api/admin/status") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val status = response.body<AdminStatusResponse>()
        assertThat(status.sessions).hasSize(1)
        assertThat(status.sessions[0].sessionId).isEqualTo("test-session")
        assertThat(status.sessions[0].remoteIp).isEqualTo("127.0.0.1")
        assertThat(status.sessions[0].flavorId).isEqualTo("oxsts")
        assertThat(status.sessions[0].sessionLspInfo.timeSinceLastClientMessage).isEqualTo(2.seconds)
        assertThat(status.sessions[0].sessionLspInfo.timeSinceLastServerMessage).isEqualTo(1.seconds)
        assertThat(status.sessions[0].activeVerifications).hasSize(1)
        assertThat(status.sessions[0].activeVerifications[0].requestId).isEqualTo("req-1")
        assertThat(status.sessions[0].activeVerifications[0].portfolioId).isEqualTo("smart-full")
    }

    @Test
    fun `config endpoint returns server configuration`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }
        val client = jsonClient()

        val response = client.get("/api/admin/config") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val config = response.body<AdminConfigResponse>()
        assertThat(config.maxSessionsGlobal).isEqualTo(256)
        assertThat(config.verificationConcurrency).isEqualTo(4)
    }

    @Test
    fun `cancel session returns 200 for existing session`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }

        val response = client.delete("/api/admin/sessions/test-session") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `cancel session returns 404 for nonexistent session`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }

        val response = client.delete("/api/admin/sessions/nonexistent") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `cancel verification returns 200 for existing verification`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }

        val response = client.delete("/api/admin/sessions/test-session/verifications/req-1") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `cancel verification returns 404 for nonexistent verification`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }

        val response = client.delete("/api/admin/sessions/test-session/verifications/nonexistent") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `admin endpoints reject when no password is configured`() = testApplication {
        val sessionManager = mock(SessionManager::class.java)
        val handler = testInjector {
            bind(SessionManager::class.java).toInstance(sessionManager)
        }.handler<AdminHandler>()

        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }

        val response = client.get("/api/admin/status") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `login sets a cookie that authenticates subsequent requests`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }
        val client = createClient {
            install(ClientContentNegotiation) { json() }
            install(HttpCookies)
        }

        val unauthenticated = client.get("/api/admin/whoami")
        assertThat(unauthenticated.status).isEqualTo(HttpStatusCode.Unauthorized)

        val login = client.post("/api/admin/login") {
            contentType(ContentType.Application.Json)
            setBody(AdminLoginRequest(password = adminPassword))
        }
        assertThat(login.status).isEqualTo(HttpStatusCode.OK)

        val whoami = client.get("/api/admin/whoami")
        assertThat(whoami.status).isEqualTo(HttpStatusCode.OK)

        val status = client.get("/api/admin/status")
        assertThat(status.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `login rejects an incorrect password`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }
        val client = createClient {
            install(ClientContentNegotiation) { json() }
            install(HttpCookies)
        }

        val login = client.post("/api/admin/login") {
            contentType(ContentType.Application.Json)
            setBody(AdminLoginRequest(password = "wrong-password"))
        }
        assertThat(login.status).isEqualTo(HttpStatusCode.Unauthorized)

        val whoami = client.get("/api/admin/whoami")
        assertThat(whoami.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `logout clears the session cookie`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }
        val client = createClient {
            install(ClientContentNegotiation) { json() }
            install(HttpCookies)
        }

        client.post("/api/admin/login") {
            contentType(ContentType.Application.Json)
            setBody(AdminLoginRequest(password = adminPassword))
        }
        assertThat(client.get("/api/admin/whoami").status).isEqualTo(HttpStatusCode.OK)

        client.post("/api/admin/logout")
        assertThat(client.get("/api/admin/whoami").status).isEqualTo(HttpStatusCode.Unauthorized)
    }
}
