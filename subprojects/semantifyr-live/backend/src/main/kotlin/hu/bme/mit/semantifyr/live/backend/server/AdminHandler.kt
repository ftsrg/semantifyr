/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.PortfolioRegistry
import hu.bme.mit.semantifyr.live.backend.ServerStatus
import hu.bme.mit.semantifyr.live.backend.data.AdminConfigResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminLoadResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminPortfolioResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminServerConfigResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminSessionManagerConfigResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminStatusResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminVerificationConfigResponse
import hu.bme.mit.semantifyr.live.backend.data.AdminWhoamiResponse
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionManager
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationManager
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

private const val PROXY_USER = "proxy-user"

@Singleton
class AdminHandler @Inject constructor(
    private val config: BackendConfig,
    private val sessionManager: SessionManager,
    private val proxyUserResolver: ProxyUserResolver,
    private val verificationManager: VerificationManager,
    private val serverStatus: ServerStatus,
) {

    private val logger by loggerFactory()

    fun Application.configure() {
        if (config.server.userHeader == null) {
            logger.info { "No proxy user header configured, the admin API answers 403" }
        }
        install(Authentication) {
            provider(PROXY_USER) {
                authenticate {
                    val user = proxyUserResolver.resolve(it.call.request.local.remoteAddress, it.call.request.headers)
                    if (user == null) {
                        // 401 asks the client to authenticate, but only the proxy in front signs anyone in
                        it.challenge(PROXY_USER, AuthenticationFailedCause.NoCredentials) { challenge, call ->
                            call.respond(HttpStatusCode.Forbidden)
                            challenge.complete()
                        }
                    } else {
                        it.principal(UserIdPrincipal(user))
                    }
                }
            }
        }

        routing {
            authenticate(PROXY_USER) {
                get("/api/admin/whoami") {
                    call.respond(AdminWhoamiResponse(user = checkNotNull(call.principal<UserIdPrincipal>()).name))
                }
                get("/api/admin/status") {
                    call.respond(
                        AdminStatusResponse(
                            sessions = sessionManager.getSessionInfos(),
                            load = AdminLoadResponse(
                                activeSessions = sessionManager.activeSessionsCount,
                                maxSessions = sessionManager.maxSessions,
                                runningVerifications = verificationManager.runningCount,
                                queuedVerifications = verificationManager.queuedCount,
                                verificationPermits = verificationManager.maxPermits,
                                uptime = serverStatus.uptime,
                            ),
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

    private fun buildAdminConfigResponse(): AdminConfigResponse {
        return AdminConfigResponse(
            server = AdminServerConfigResponse(
                port = config.server.port,
                pingPeriod = config.server.pingPeriod,
                pingTimeout = config.server.pingTimeout,
                webRootDirectory = config.server.webRootDirectory,
                wsHandshakesPerPeriod = config.server.wsHandshakesPerPeriod,
                wsHandshakeRatePeriod = config.server.wsHandshakeRatePeriod,
                maxWsFrameSize = config.server.maxWsFrameSize,
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
            portfolios = PortfolioRegistry.entries.map {
                AdminPortfolioResponse(
                    id = it.portfolio.id,
                    engines = it.portfolio.executorKeys.map { key ->
                        key.name
                    }.sorted(),
                )
            },
        )
    }
}
