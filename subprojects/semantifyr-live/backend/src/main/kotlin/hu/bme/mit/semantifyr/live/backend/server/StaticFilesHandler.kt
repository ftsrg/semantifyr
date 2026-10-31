/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path

private const val HASHED_ASSETS_DIRECTORY = "assets"
private const val IMMUTABLE = "public, max-age=31536000, immutable"
private const val REVALIDATE = "no-cache"

@Singleton
class StaticFilesHandler @Inject constructor(
    private val config: BackendConfig,
) {

    private val logger by loggerFactory()

    fun Application.configure() {
        val webRoot = config.server.webRootPath
        if (webRoot == null) {
            logger.info { "No web root configured, running in API-only mode" }
            return
        }

        logger.info { "Serving frontend SPA from $webRoot" }
        val hashedAssets = webRoot.resolve(HASHED_ASSETS_DIRECTORY)
        val index = webRoot.resolve("index.html").toFile()
        routing {
            staticFiles("/", webRoot.toFile()) {
                modify { file, call ->
                    val cacheControl = if (file.toPath().startsWith(hashedAssets)) {
                        IMMUTABLE
                    } else {
                        REVALIDATE
                    }
                    call.response.header(HttpHeaders.CacheControl, cacheControl)
                }
                // a missing file stays a 404, so no cache stores index.html under the URL of a script removed by a deploy
                fallback { requestedPath, call ->
                    val path = Path.of(requestedPath)
                    if (path.startsWith(HASHED_ASSETS_DIRECTORY) || path.fileName.toString().contains('.')) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.response.header(HttpHeaders.CacheControl, REVALIDATE)
                        call.respondFile(index)
                    }
                }
            }
        }
    }
}
