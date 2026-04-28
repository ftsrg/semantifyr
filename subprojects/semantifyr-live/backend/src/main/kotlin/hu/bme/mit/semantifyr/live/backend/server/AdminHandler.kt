/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.session.SessionManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@Singleton
class AdminHandler @Inject constructor(
    private val config: BackendConfig,
    private val sessionManager: SessionManager,
) {

    fun Application.configure() {
        install(Authentication) {
            basic("admin") {
                realm = "Semantifyr Admin"
                validate { credentials ->
                    val password = config.server.adminPassword
                    if (!password.isNullOrEmpty() &&
                        credentials.name == "admin" &&
                        credentials.password == password
                    ) {
                        UserIdPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
            }
        }

        routing {
            authenticate("admin") {
                get("/api/admin/status") {
                    call.respond(
                        AdminStatusResponse(
                            sessions = sessionManager.getSessionInfos(),
                        ),
                    )
                }
                get("/api/admin/config") {
                    call.respond(
                        AdminConfigResponse(
                            maxSessionsGlobal = config.sessionManager.maxSessionsGlobal,
                            maxSessionsPerIp = config.sessionManager.maxSessionsPerIp,
                            verificationConcurrency = config.verification.concurrency,
                            verificationTimeout = config.verification.timeout,
                        ),
                    )
                }
                delete("/api/admin/sessions/{sessionId}") {
                    val sessionId = call.parameters["sessionId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    if (sessionManager.cancelSession(sessionId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
                delete("/api/admin/sessions/{sessionId}/verifications/{requestId}") {
                    val sessionId = call.parameters["sessionId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val requestId = call.parameters["requestId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    if (sessionManager.cancelVerification(sessionId, requestId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}
