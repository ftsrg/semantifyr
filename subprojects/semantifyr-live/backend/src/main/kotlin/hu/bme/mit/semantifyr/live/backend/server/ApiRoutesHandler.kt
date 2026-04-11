/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.FlavorRegistry
import hu.bme.mit.semantifyr.live.backend.session.SessionManager
import hu.bme.mit.semantifyr.live.backend.utils.debug
import hu.bme.mit.semantifyr.live.backend.utils.loggerFactory
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@Singleton
class ApiRoutesHandler {

    private val logger by loggerFactory()

    @Inject
    private lateinit var sessionManager: SessionManager

    fun Application.configure() {
        routing {
            get("/api/health") {
                logger.debug { "/api/health" }

                call.respond(HealthResponse(
                    status = "ok",
                    activeSessions = sessionManager.activeSessions,
                    maxSessions = sessionManager.maxSessions,
                ))
            }
            get("/api/flavors") {
                logger.debug { "/api/flavors" }

                call.respond(FlavorsResponse(
                    flavors = FlavorRegistry.flavors.map { FlavorResponse.fromFlavor(it) },
                ))
            }
        }
    }
}
