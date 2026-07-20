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
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

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
        routing {
            singlePageApplication {
                filesPath = webRoot.toString()
                defaultPage = "index.html"
            }
        }
    }
}
