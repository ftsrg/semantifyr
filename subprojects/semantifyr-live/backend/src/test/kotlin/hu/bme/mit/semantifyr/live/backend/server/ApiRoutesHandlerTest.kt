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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path

class ApiRoutesHandlerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createHandler(activeSessions: Int = 3, maxSessions: Int = 64): ApiRoutesHandler {
        val config = BackendConfig()
        val sessionManager = mock(SessionManager::class.java)
        `when`(sessionManager.activeSessions).thenReturn(activeSessions)
        `when`(sessionManager.maxSessions).thenReturn(maxSessions)

        val injector = Guice.createInjector(BackendModule(config), object : AbstractModule() {
            override fun configure() {
                bind(SessionManager::class.java).toInstance(sessionManager)
            }
        })
        return injector.getInstance(ApiRoutesHandler::class.java)
    }

    private fun ApplicationTestBuilder.installApiRoutes(handler: ApiRoutesHandler) {
        install(createApplicationPlugin("api-routes") {
            with(handler) { application.configure() }
        })
        install(createApplicationPlugin("content-negotiation-setup") {
            application.install(ContentNegotiation) { json() }
        })
    }

    @Test
    fun `health endpoint returns 200 with status and session info`() = testApplication {
        installApiRoutes(createHandler(activeSessions = 3, maxSessions = 64))

        val response = client.get("/api/health")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val health = json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertThat(health.status).isEqualTo("ok")
        assertThat(health.activeSessions).isEqualTo(3)
        assertThat(health.maxSessions).isEqualTo(64)
    }

    @Test
    fun `health endpoint returns JSON content type`() = testApplication {
        installApiRoutes(createHandler())

        val response = client.get("/api/health")
        assertThat(response.contentType()?.withoutParameters())
            .isEqualTo(ContentType.Application.Json)
    }

    @Test
    fun `flavors endpoint lists all registered flavors`() = testApplication {
        installApiRoutes(createHandler())

        val response = client.get("/api/flavors")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val flavors = json.decodeFromString<FlavorsResponse>(response.bodyAsText())
        val ids = flavors.flavors.map { it.id }
        assertThat(ids).containsExactly("oxsts", "xsts", "gamma")
    }

    @Test
    fun `flavors endpoint reports verify capability per flavor`() = testApplication {
        installApiRoutes(createHandler())

        val response = client.get("/api/flavors")
        val flavors = json.decodeFromString<FlavorsResponse>(response.bodyAsText())

        val oxsts = flavors.flavors.first { it.id == "oxsts" }
        assertThat(oxsts.verify).isTrue()
        assertThat(oxsts.verifyCommand).isEqualTo("oxsts.case.verify")

        val xsts = flavors.flavors.first { it.id == "xsts" }
        assertThat(xsts.verify).isFalse()
        assertThat(xsts.verifyCommand).isNull()
    }
}

class StaticFilesHandlerTest {

    @Test
    fun `static SPA serves index html at root`(@TempDir webRoot: Path) = testApplication {
        val handler = createStaticFilesHandler(webRoot)
        install(createApplicationPlugin("spa") {
            with(handler) { application.configure() }
        })

        val response = client.get("/")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).contains("<title>SPA</title>")
    }

    @Test
    fun `SPA fallback serves index html for unknown paths`(@TempDir webRoot: Path) = testApplication {
        val handler = createStaticFilesHandler(webRoot)
        install(createApplicationPlugin("spa") {
            with(handler) { application.configure() }
        })

        val response = client.get("/some/deep/path")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).contains("<title>SPA</title>")
    }

    private fun createStaticFilesHandler(webRoot: Path): StaticFilesHandler {
        Files.writeString(webRoot.resolve("index.html"), "<!DOCTYPE html><title>SPA</title>")
        val config = BackendConfig(server = ServerConfig(webRootDirectory = webRoot.toString()))
        val injector = Guice.createInjector(BackendModule(config))
        return injector.getInstance(StaticFilesHandler::class.java)
    }
}
