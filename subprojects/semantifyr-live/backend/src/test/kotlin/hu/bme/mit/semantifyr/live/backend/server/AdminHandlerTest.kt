/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import hu.bme.mit.semantifyr.live.backend.PortfolioRegistry
import hu.bme.mit.semantifyr.live.backend.ServerStatus
import hu.bme.mit.semantifyr.live.backend.data.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.data.AdminConfigResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminLoadResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminStatusResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminWhoamiResponse
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.data.VerificationState
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionManager
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationManager
import hu.bme.mit.semantifyr.live.backend.testing.handler
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import hu.bme.mit.semantifyr.portfolios.Portfolios
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val USER_HEADER = "X-Auth-Request-User"

class AdminHandlerTest {

    private val sessionManager = mock<SessionManager>()

    private val verificationManager = mock<VerificationManager>()

    private val serverStatus = ServerStatus().also {
        it.markStartNow()
    }

    private fun signedIn(user: String?): ProxyUserResolver {
        return mock {
            on { resolve(any(), any()) } doReturn user
        }
    }

    private fun createHandler(
        sessionInfos: List<SessionInfo> = emptyList(),
        proxyUserResolver: ProxyUserResolver = signedIn("octocat"),
    ): AdminHandler {
        whenever(sessionManager.getSessionInfos()).thenReturn(sessionInfos)
        whenever(sessionManager.activeSessionsCount).thenReturn(sessionInfos.size)
        whenever(sessionManager.maxSessions).thenReturn(64)
        whenever(verificationManager.runningCount).thenReturn(2)
        whenever(verificationManager.queuedCount).thenReturn(5)
        whenever(verificationManager.maxPermits).thenReturn(4)
        whenever(sessionManager.cancelSession("test-session")).thenReturn(true)
        whenever(sessionManager.cancelSession("nonexistent")).thenReturn(false)
        whenever(sessionManager.cancelVerification("req-1")).thenReturn(true)
        whenever(sessionManager.cancelVerification("nonexistent")).thenReturn(false)

        return testInjector {
            bind(SessionManager::class.java).toInstance(sessionManager)
            bind(VerificationManager::class.java).toInstance(verificationManager)
            bind(ServerStatus::class.java).toInstance(serverStatus)
            bind(ProxyUserResolver::class.java).toInstance(proxyUserResolver)
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
    fun `status endpoint encodes the sessions and the load the managers report`() = testApplication {
        val testSession = SessionInfo(
            sessionId = "test-session",
            remoteIp = "127.0.0.1",
            flavorId = "oxsts-with-sysmlv2-library",
            kind = "embed",
            languageId = "sysml",
            uptime = 30.seconds,
            runningCount = 1,
            queuedCount = 0,
            workingDirectory = "/tmp/test",
            activeVerifications = listOf(
                ActiveVerificationInfo(verificationId = "req-1", portfolioId = "smart", kind = VerificationKind.Verify, state = VerificationState.Running),
            ),
        )
        installAdmin(createHandler(sessionInfos = listOf(testSession)))

        val status = jsonClient().get("/api/admin/status").body<AdminStatusResponse>()
        assertThat(status.sessions).containsExactly(testSession)
        assertThat(status.load).usingRecursiveComparison().ignoringFields("uptime").isEqualTo(
            AdminLoadResponse(
                activeSessions = 1,
                maxSessions = 64,
                runningVerifications = 2,
                queuedVerifications = 5,
                verificationPermits = 4,
                uptime = Duration.ZERO,
            ),
        )
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
        assertThat(config.verification.timeout.isPositive()).isTrue
        val thetaPortfolio = config.portfolios.single {
            it.id == Portfolios.ThetaFull.id
        }
        assertThat(thetaPortfolio.engines).isNotEmpty
        assertThat(
            config.portfolios.map {
                it.id
            },
        ).containsExactlyElementsOf(
            PortfolioRegistry.entries.map {
                it.portfolio.id
            },
        )
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
}
