/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.data.AdminConfigResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminStatusResponse
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.session
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.Serializable
import java.security.SecureRandom

@Serializable
data class AdminSession(val createdAt: Long)

@Serializable
data class AdminLoginRequest(val password: String)

@Singleton
class AdminHandler @Inject constructor(
    private val config: BackendConfig,
    private val sessionManager: SessionManager,
) {

    private val sessionSigningKey = ByteArray(32).also {
        SecureRandom().nextBytes(it)
    }

    fun Application.configure() {
        install(Sessions) {
            cookie<AdminSession>("admin_session") {
                cookie.httpOnly = true
                cookie.secure = config.server.httpsOnlyCookies
                cookie.maxAgeInSeconds = 8 * 3600
                cookie.extensions["SameSite"] = "Strict"
                transform(SessionTransportTransformerMessageAuthentication(sessionSigningKey))
            }
        }
        install(Authentication) {
            basic("admin-basic") {
                realm = "Semantifyr Admin"
                validate { credentials ->
                    if (credentials.name == "admin" && passwordMatches(credentials.password)) {
                        UserIdPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
            }
            session<AdminSession>("admin-session") {
                validate { it }
                challenge { call.respond(HttpStatusCode.Unauthorized) }
            }
        }

        routing {
            post("/api/admin/login") {
                val request = call.receive<AdminLoginRequest>()
                if (!passwordMatches(request.password)) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                call.sessions.set(AdminSession(createdAt = System.currentTimeMillis()))
                call.respond(HttpStatusCode.OK)
            }
            post("/api/admin/logout") {
                call.sessions.clear<AdminSession>()
                call.respond(HttpStatusCode.OK)
            }
            authenticate("admin-session", "admin-basic") {
                get("/api/admin/whoami") {
                    call.respond(HttpStatusCode.OK)
                }
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

    private fun passwordMatches(candidate: String): Boolean {
        val expected = config.server.adminPassword
        return !expected.isNullOrEmpty() && candidate == expected
    }
}
