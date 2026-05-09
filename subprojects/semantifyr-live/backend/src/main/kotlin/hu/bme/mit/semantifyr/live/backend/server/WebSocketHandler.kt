/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.session.SessionLimitReachedException
import hu.bme.mit.semantifyr.live.backend.session.SessionManager
import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException

@Singleton
class WebSocketHandler @Inject constructor(
    private val config: BackendConfig,
    private val sessionManager: SessionManager,
) {

    private val logger by loggerFactory()

    fun Application.configure() {
        install(RateLimit) {
            register(RateLimitName("ws-handshake")) {
                rateLimiter(
                    limit = config.server.wsHandshakesPerPeriod,
                    refillPeriod = config.server.wsHandshakeRatePeriod,
                )
                requestKey {
                    it.request.local.remoteAddress
                }
            }
        }
        routing {
            rateLimit(RateLimitName("ws-handshake")) {
                webSocket("/ws/lsp/{flavor}") {
                    val remoteIp = call.request.local.remoteAddress
                    logger.info { "WebSocket connection from ip=$remoteIp" }

                    val flavorId = call.parameters["flavor"]
                    if (flavorId == null) {
                        logger.warn { "WebSocket connection rejected: missing flavor parameter" }
                        close(CloseReason(4400, "Missing flavor parameter"))
                        return@webSocket
                    }

                    val flavor = FlavorRegistry.get(flavorId)
                    if (flavor == null) {
                        logger.warn { "WebSocket connection rejected: unknown flavor=$flavorId" }
                        close(CloseReason(4404, "Unknown flavor: $flavorId"))
                        return@webSocket
                    }

                    val closeReason = try {
                        sessionManager.runSession(this, remoteIp, flavor)
                        logger.info { "WebSocket session ended normally for ip=$remoteIp flavor=$flavorId" }
                        CloseReason(CloseReason.Codes.NORMAL, "Session ended")
                    } catch (e: SessionLimitReachedException) {
                        logger.warn { "Session limit reached for ip=$remoteIp: ${e.message}" }
                        CloseReason(4429, e.message ?: "Too many sessions")
                    } catch (e: CancellationException) {
                        logger.info { "WebSocket session cancelled for ip=$remoteIp flavor=$flavorId: ${e.message}" }
                        CloseReason(4000, e.message ?: "Session terminated")
                    } catch (e: Throwable) {
                        logger.error { "WebSocket session error for ip=$remoteIp flavor=$flavorId: $e" }
                        CloseReason(4500, "Internal server error")
                    }
                    close(closeReason)
                }
            }
        }
    }
}
