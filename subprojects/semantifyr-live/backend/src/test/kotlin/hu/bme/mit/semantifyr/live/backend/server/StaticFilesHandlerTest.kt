/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Guice
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.BackendModule
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class StaticFilesHandlerTest {

    @Test
    fun `static SPA serves index html at root`(@TempDir webRoot: Path) = testApplication {
        val handler = createStaticFilesHandler(webRoot)
        install(
            createApplicationPlugin("spa") {
                with(handler) {
                    application.configure()
                }
            },
        )

        val response = client.get("/")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<String>()).contains("<title>SPA</title>")
    }

    @Test
    fun `SPA fallback serves index html for unknown paths`(@TempDir webRoot: Path) = testApplication {
        val handler = createStaticFilesHandler(webRoot)
        install(
            createApplicationPlugin("spa") {
                with(handler) {
                    application.configure()
                }
            },
        )

        val response = client.get("/some/deep/path")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<String>()).contains("<title>SPA</title>")
    }

    private fun createStaticFilesHandler(webRoot: Path): StaticFilesHandler {
        Files.writeString(webRoot.resolve("index.html"), "<!DOCTYPE html><title>SPA</title>")
        val config = BackendConfig(server = ServerConfig(webRootDirectory = webRoot.toString()))
        val injector = Guice.createInjector(BackendModule(config))
        return injector.getInstance(StaticFilesHandler::class.java)
    }
}
