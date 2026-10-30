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
import hu.bme.mit.semantifyr.live.backend.data.AdminServerConfigResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminSessionManagerConfigResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminStatusResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminVerificationConfigResponse
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
                validate {
                    if (it.name == "admin" && passwordMatches(it.password)) {
                        UserIdPrincipal(it.name)
                    } else {
                        null
                    }
                }
            }
            session<AdminSession>("admin-session") {
                validate { it }
                challenge {
                    call.respond(HttpStatusCode.Unauthorized)
                }
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
                    call.respond(buildAdminConfigResponse())
                }
                delete("/api/admin/sessions/{sessionId}") {
                    val sessionId = call.parameters["sessionId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    if (sessionManager.cancelSession(sessionId)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
                delete("/api/admin/verifications/{verificationId}") {
                    val verificationId = call.parameters["verificationId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    if (sessionManager.cancelVerification(verificationId)) {
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

    private fun buildAdminConfigResponse(): AdminConfigResponse {
        return AdminConfigResponse(
            development = config.development,
            server = AdminServerConfigResponse(
                port = config.server.port,
                pingPeriod = config.server.pingPeriod,
                pingTimeout = config.server.pingTimeout,
                webRootDirectory = config.server.webRootDirectory,
                adminPasswordSet = !config.server.adminPassword.isNullOrEmpty(),
                wsHandshakesPerPeriod = config.server.wsHandshakesPerPeriod,
                wsHandshakeRatePeriod = config.server.wsHandshakeRatePeriod,
                maxWsFrameSize = config.server.maxWsFrameSize,
                httpsOnlyCookies = config.server.httpsOnlyCookies,
            ),
            sessionManager = AdminSessionManagerConfigResponse(
                maxSessionsGlobal = config.sessionManager.maxSessionsGlobal,
                semanticLibrariesDirectory = config.sessionManager.semanticLibrariesDirectory,
                rootWorkDirectory = config.sessionManager.rootWorkDirectory,
            ),
            verification = AdminVerificationConfigResponse(
                concurrency = config.verification.concurrency,
                timeout = config.verification.timeout,
            ),
        )
    }
}
