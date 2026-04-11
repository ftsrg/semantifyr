/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import hu.bme.mit.semantifyr.live.backend.utils.info
import hu.bme.mit.semantifyr.live.backend.utils.loggerFactory

@Singleton
class StaticFilesHandler {

    private val logger by loggerFactory()

    @Inject
    private lateinit var config: BackendConfig

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
