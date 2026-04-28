/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BuildInfo
import hu.bme.mit.semantifyr.live.backend.FlavorRegistry
import hu.bme.mit.semantifyr.live.backend.session.SessionManager
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@Singleton
class ApiRoutesHandler @Inject constructor(
    private val sessionManager: SessionManager,
) {

    private val logger by loggerFactory()

    fun Application.configure() {
        routing {
            route("/api/health") {
                install(CORS) {
                    anyHost()
                    anyMethod()
                }
                get {
                    logger.debug { "/api/health" }
                    call.respond(HealthResponse(status = "ok"))
                }
            }
            get("/api/info") {
                logger.debug { "/api/info" }
                call.respond(
                    InfoResponse(
                        uptime = BuildInfo.uptime,
                        commit = BuildInfo.commit,
                        buildTime = BuildInfo.buildTime,
                        activeSessions = sessionManager.activeSessions,
                        maxSessions = sessionManager.maxSessions,
                    ),
                )
            }
            get("/api/flavors") {
                logger.debug { "/api/flavors" }
                call.respond(
                    FlavorsResponse(
                        flavors = FlavorRegistry.flavors.map { FlavorResponse.fromFlavor(it) },
                    ),
                )
            }
        }
    }
}
