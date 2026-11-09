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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
    fun `the SysML worker bundle is served as a script and its library by path`(@TempDir webRoot: Path) = testApplication {
        val library = webRoot.resolve("sysml-worker/library/Kernel Libraries")
        Files.createDirectories(library)
        Files.writeString(webRoot.resolve("sysml-worker/syside-worker.cjs"), "self.postMessage(1)")
        Files.writeString(library.resolve("Base.kerml"), "package Base;")
        Files.writeString(webRoot.resolve("sysml-worker/library/Verification.sysml"), "package Verification;")
        installStaticFiles(webRoot)

        val worker = client.get("/sysml-worker/syside-worker.cjs")
        assertThat(worker.status).isEqualTo(HttpStatusCode.OK)
        assertThat(worker.contentType()?.withoutParameters()).isEqualTo(ContentType.Application.JavaScript)
        assertThat(client.get("/sysml-worker/library/Kernel%20Libraries/Base.kerml").body<String>()).isEqualTo("package Base;")
        assertThat(client.get("/sysml-worker/library/Verification.sysml").body<String>()).isEqualTo("package Verification;")
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
