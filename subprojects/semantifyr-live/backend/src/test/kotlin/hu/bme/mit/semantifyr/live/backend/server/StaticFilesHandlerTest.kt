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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class StaticFilesHandlerTest {

    @Test
    fun `static SPA serves index html at root`(@TempDir webRoot: Path) = testApplication {
        installStaticFiles(webRoot)

        val response = client.get("/")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<String>()).contains("<title>SPA</title>")
    }

    @Test
    fun `SPA fallback serves index html for unknown paths`(@TempDir webRoot: Path) = testApplication {
        installStaticFiles(webRoot)

        val response = client.get("/some/deep/path")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<String>()).contains("<title>SPA</title>")
    }

    @Test
    fun `a missing hashed asset is not answered with the SPA page`(@TempDir webRoot: Path) = testApplication {
        installStaticFiles(webRoot)

        assertThat(client.get("/assets/index-removed.js").status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(client.get("/removed.png").status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `hashed assets are immutable and everything else revalidates`(@TempDir webRoot: Path) = testApplication {
        installStaticFiles(webRoot)

        assertThat(client.get("/assets/index-abc.js").headers[HttpHeaders.CacheControl]).isEqualTo("public, max-age=31536000, immutable")
        assertThat(client.get("/").headers[HttpHeaders.CacheControl]).isEqualTo("no-cache")
        assertThat(client.get("/admin").headers[HttpHeaders.CacheControl]).isEqualTo("no-cache")
        assertThat(client.get("/sw.js").headers[HttpHeaders.CacheControl]).isEqualTo("no-cache")
    }

    private fun ApplicationTestBuilder.installStaticFiles(webRoot: Path) {
        Files.writeString(webRoot.resolve("index.html"), "<!DOCTYPE html><title>SPA</title>")
        Files.writeString(webRoot.resolve("sw.js"), "self.skipWaiting()")
        Files.createDirectories(webRoot.resolve("assets"))
        Files.writeString(webRoot.resolve("assets/index-abc.js"), "export {}")
        val config = BackendConfig(server = ServerConfig(webRootDirectory = webRoot.toString()))
        val handler = Guice.createInjector(BackendModule(config)).getInstance(StaticFilesHandler::class.java)
        install(
            createApplicationPlugin("spa") {
                with(handler) {
                    application.configure()
                }
            },
        )
    }
}
