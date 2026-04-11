/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.FlavorRegistry
import hu.bme.mit.semantifyr.live.backend.session.SessionLimitReachedException
import hu.bme.mit.semantifyr.live.backend.session.SessionManager
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import hu.bme.mit.semantifyr.live.backend.utils.info
import hu.bme.mit.semantifyr.live.backend.utils.loggerFactory
import hu.bme.mit.semantifyr.live.backend.utils.warn
import hu.bme.mit.semantifyr.live.backend.utils.error
import kotlinx.coroutines.CancellationException

@Singleton
class WebSocketHandler {

    private val logger by loggerFactory()

    @Inject
    private lateinit var sessionManager: SessionManager

    fun Application.configure() {
        routing {
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

                try {
                    sessionManager.runSession(this, remoteIp, flavor)
                    logger.info { "WebSocket session ended normally for ip=$remoteIp flavor=$flavorId" }
                } catch (e: SessionLimitReachedException) {
                    logger.warn { "Session limit reached for ip=$remoteIp: ${e.message}" }
                    close(CloseReason(4429, e.message ?: "Too many sessions"))
                } catch (e: CancellationException) {
                    logger.info { "WebSocket session cancelled for ip=$remoteIp flavor=$flavorId" }
                    throw e
                } catch (e: Throwable) {
                    logger.error { "WebSocket session error for ip=$remoteIp flavor=$flavorId: $e" }
                    close(CloseReason(4500, "Internal server error"))
                }

                close(CloseReason(CloseReason.Codes.NORMAL, "Session ended"))
            }
        }
    }
}
