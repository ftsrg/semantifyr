/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.AbstractModule
import com.google.inject.Guice
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.BackendModule
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.session.SessionManager
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.wheneverBlocking
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
            server = ServerConfig(adminPassword = adminPassword),
        )
        val sessionManager = mock(SessionManager::class.java)
        `when`(sessionManager.getSessionInfos()).thenReturn(sessionInfos)
        `when`(sessionManager.cancelSession("test-session")).thenReturn(true)
        `when`(sessionManager.cancelSession("nonexistent")).thenReturn(false)
        wheneverBlocking { sessionManager.cancelVerification("test-session", "req-1") }.thenReturn(true)
        wheneverBlocking { sessionManager.cancelVerification("test-session", "nonexistent") }.thenReturn(false)

        val injector = Guice.createInjector(
            BackendModule(config),
            object : AbstractModule() {
                override fun configure() {
                    bind(SessionManager::class.java).toInstance(sessionManager)
                }
            },
        )
        return injector.getInstance(AdminHandler::class.java)
    }

    private fun ApplicationTestBuilder.installAdmin(handler: AdminHandler) {
        install(
            createApplicationPlugin("admin") {
                with(handler) { application.configure() }
            },
        )
        install(
            createApplicationPlugin("content-negotiation-setup") {
                application.install(ContentNegotiation) { json() }
            },
        )
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ClientContentNegotiation) { json() }
    }

    @Test
    fun `status endpoint returns 401 without auth`() = testApplication {
        installAdmin(createHandler())

        val response = client.get("/api/admin/status")
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `status endpoint returns 401 with wrong password`() = testApplication {
        installAdmin(createHandler())

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
            activeVerifications = setOf("req-1"),
            started = true,
            bridgeInfo = LspProxyInfo(
                clientMessageCount = 10,
                serverMessageCount = 15,
                errorCount = 0,
                timeSinceLastClientMessage = 2.seconds,
                timeSinceLastServerMessage = 1.seconds,
            ),
        )
        installAdmin(createHandler(sessionInfos = listOf(testSession)))
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
        assertThat(status.sessions[0].started).isTrue()
        assertThat(status.sessions[0].bridgeInfo?.clientMessageCount).isEqualTo(10)
        assertThat(status.sessions[0].bridgeInfo?.serverMessageCount).isEqualTo(15)
        assertThat(status.sessions[0].activeVerifications).containsExactly("req-1")
    }

    @Test
    fun `config endpoint returns server configuration`() = testApplication {
        installAdmin(createHandler())
        val client = jsonClient()

        val response = client.get("/api/admin/config") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val config = response.body<AdminConfigResponse>()
        assertThat(config.maxSessionsGlobal).isEqualTo(32)
        assertThat(config.maxSessionsPerIp).isEqualTo(4)
        assertThat(config.verificationConcurrency).isEqualTo(4)
    }

    @Test
    fun `cancel session returns 200 for existing session`() = testApplication {
        installAdmin(createHandler())

        val response = client.delete("/api/admin/sessions/test-session") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `cancel session returns 404 for nonexistent session`() = testApplication {
        installAdmin(createHandler())

        val response = client.delete("/api/admin/sessions/nonexistent") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `cancel verification returns 200 for existing verification`() = testApplication {
        installAdmin(createHandler())

        val response = client.delete("/api/admin/sessions/test-session/verifications/req-1") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `cancel verification returns 404 for nonexistent verification`() = testApplication {
        installAdmin(createHandler())

        val response = client.delete("/api/admin/sessions/test-session/verifications/nonexistent") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `admin endpoints reject when no password is configured`() = testApplication {
        val config = BackendConfig()
        val sessionManager = mock(SessionManager::class.java)
        val injector = Guice.createInjector(
            BackendModule(config),
            object : AbstractModule() {
                override fun configure() {
                    bind(SessionManager::class.java).toInstance(sessionManager)
                }
            },
        )
        val handler = injector.getInstance(AdminHandler::class.java)
        installAdmin(handler)

        val response = client.get("/api/admin/status") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }
}
