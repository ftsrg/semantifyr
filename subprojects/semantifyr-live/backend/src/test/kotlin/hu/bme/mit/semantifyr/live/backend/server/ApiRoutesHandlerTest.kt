/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.ServerStatus
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.data.FlavorsResponse
import hu.bme.mit.semantifyr.live.backend.data.HealthResponse
import hu.bme.mit.semantifyr.live.backend.data.InfoResponse
import hu.bme.mit.semantifyr.live.backend.data.LibrariesResponse
import hu.bme.mit.semantifyr.live.backend.data.PortfoliosResponse
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionManager
import hu.bme.mit.semantifyr.live.backend.testing.handler
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path

class ApiRoutesHandlerTest {

    private fun createHandler(
        activeSessions: Int = 3,
        maxSessions: Int = 64,
        config: BackendConfig = BackendConfig(),
    ): ApiRoutesHandler {
        val sessionManager = mock<SessionManager>()
        whenever(sessionManager.activeSessionsCount).thenReturn(activeSessions)
        whenever(sessionManager.maxSessions).thenReturn(maxSessions)
        val serverStatus = ServerStatus().also {
            it.markStartNow()
        }

        return testInjector(config) {
            bind(SessionManager::class.java).toInstance(sessionManager)
            bind(ServerStatus::class.java).toInstance(serverStatus)
        }.handler<ApiRoutesHandler>()
    }

    @Test
    fun `health endpoint returns 200 with status`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }
        val client = jsonClient()

        val health = client.get("/api/health").body<HealthResponse>()
        assertThat(health.status).isEqualTo("ok")
    }

    @Test
    fun `info endpoint returns server info`() = testApplication {
        val handler = createHandler(activeSessions = 3, maxSessions = 64)
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }
        val client = jsonClient()

        val info = client.get("/api/info").body<InfoResponse>()
        assertThat(info.activeSessions).isEqualTo(3)
        assertThat(info.maxSessions).isEqualTo(64)
        assertThat(info.uptime).isNotNull()
        assertThat(info.version).isNotEmpty().isNotEqualTo("unknown")
        assertThat(info.commit).isNotEmpty()
        assertThat(info.buildTime).isNotEmpty()
    }

    @Test
    fun `health endpoint returns JSON content type`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }

        val response = client.get("/api/health")
        assertThat(response.contentType()?.withoutParameters())
            .isEqualTo(ContentType.Application.Json)
    }

    @Test
    fun `flavors endpoint lists all registered flavors`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }
        val client = jsonClient()

        val flavors = client.get("/api/flavors").body<FlavorsResponse>()
        val ids = flavors.flavors.map { it.id }
        assertThat(ids).containsExactly("oxsts", "oxsts-with-gamma-library", "oxsts-with-sysmlv2-library", "gamma")
    }

    @Test
    fun `flavors endpoint reports verification capability per flavor`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }
        val client = jsonClient()

        val flavors = client.get("/api/flavors").body<FlavorsResponse>()

        val oxsts = flavors.flavors.first { it.id == "oxsts" }
        assertThat(oxsts.verificationCommand).isEqualTo("oxsts.case.verify")
        assertThat(oxsts.validateWitnessCommand).isEqualTo("oxsts.case.validateWitness")
        assertThat(oxsts.validateWitnessByCase).isFalse()
        assertThat(oxsts.description).isNotBlank()

        val gamma = flavors.flavors.first { it.id == "gamma" }
        assertThat(gamma.validateWitnessCommand).isEqualTo("gamma.witness.validate")
        assertThat(gamma.validateWitnessByCase).isTrue()
    }

    @Test
    fun `portfolios endpoint exposes the demo portfolio set`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }
        val client = jsonClient()

        val response = client.get("/api/portfolios").body<PortfoliosResponse>()
        val ids = response.portfolios.map { it.id }
        // The six demo entries. Availability depends on host binaries, so we assert presence
        // rather than the boolean.
        assertThat(ids).containsExactly(
            "smart-full",
            "all-agree-full",
            "theta-full",
            "nuxmv-ic3-invar",
            "spin-safe-dfs",
            "uppaal-default",
        )
        val auto = response.portfolios.first { it.id == "smart-full" }
        assertThat(auto.displayName).isEqualTo("Auto")
        assertThat(response.defaultPortfolio).isEqualTo("smart-full")
    }

    @Test
    fun `the website can read health, flavors and portfolios from another origin`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }

        for (path in listOf("/api/health", "/api/flavors", "/api/portfolios")) {
            val response = client.get(path) {
                header(HttpHeaders.Origin, "https://ftsrg.mit.bme.hu")
            }
            assertThat(response.headers[HttpHeaders.AccessControlAllowOrigin]).`as`(path).isEqualTo("*")
        }
        val info = client.get("/api/info") {
            header(HttpHeaders.Origin, "https://ftsrg.mit.bme.hu")
        }
        assertThat(info.headers[HttpHeaders.AccessControlAllowOrigin]).isNull()
    }

    @Test
    fun `libraries endpoint lists every model file of each library`(@TempDir tmp: Path) = testApplication {
        val semanticLibraries = tmp.resolve("semantic-libraries")
        val webRoot = tmp.resolve("web")
        val files = listOf(
            "semantic-libraries/gamma/Statecharts.oxsts",
            "semantic-libraries/gamma/Ports.oxsts",
            "semantic-libraries/sysmlv2/States.oxsts",
            "web/sysml-worker/library/Verification.sysml",
            "web/sysml-worker/library/Kernel Libraries/Kernel Data Type Library/ScalarValues.kerml",
            "web/sysml-worker/library/Kernel Libraries/Kernel Data Type Library/.meta.json",
        )
        for (file in files) {
            val path = tmp.resolve(file)
            Files.createDirectories(path.parent)
            Files.writeString(path, "")
        }
        val handler = createHandler(
            config = BackendConfig(
                server = ServerConfig(webRootDirectory = webRoot.toString()),
                sessionManager = SessionManagerConfig(semanticLibrariesDirectory = semanticLibraries.toString()),
            ),
        )
        installSemantifyrApp {
            with(handler) {
                configure()
            }
        }

        val libraries = jsonClient().get("/api/libraries").body<LibrariesResponse>()

        assertThat(libraries.builtin).containsExactly("semantifyr.oxsts")
        assertThat(libraries.semantic).containsOnlyKeys("oxsts-with-gamma-library", "oxsts-with-sysmlv2-library", "gamma")
        assertThat(libraries.semantic["gamma"]).containsExactly("Ports.oxsts", "Statecharts.oxsts")
        assertThat(libraries.semantic["oxsts-with-sysmlv2-library"]).containsExactly("States.oxsts")
        assertThat(libraries.sysml).containsExactly("Kernel Libraries/Kernel Data Type Library/ScalarValues.kerml", "Verification.sysml")
    }
}
