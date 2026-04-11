/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.session.SessionManager
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.*
import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.utils.info
import hu.bme.mit.semantifyr.live.backend.utils.loggerFactory

@Singleton
class Server : AutoCloseable {

    private val logger by loggerFactory()

    @Inject
    private lateinit var config: BackendConfig

    @Inject
    private lateinit var sessionManager: SessionManager

    @Inject
    private lateinit var apiRoutesHandler: ApiRoutesHandler

    @Inject
    private lateinit var webSocketHandler: WebSocketHandler

    @Inject
    private lateinit var staticFilesHandler: StaticFilesHandler

    private lateinit var ktorServer: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    fun start() {
        check(!this::ktorServer.isInitialized) {
            "The server has already been started!"
        }

        ktorServer = createKtorServer()

        logger.info { "Starting server on :${config.server.port}" }

        ktorServer.start(wait = true)
    }

    override fun close() {
        logger.info { "Shutting down server" }
        try {
            if (::ktorServer.isInitialized) {
                ktorServer.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
            }
        } catch (_: Throwable) {
            // best-effort
        }
        try {
            sessionManager.close()
        } catch (_: Throwable) {
            // best-effort
        }
    }

    private fun createKtorServer(): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return embeddedServer(Netty, port = config.server.port) {
            install(ContentNegotiation) {
                json()
            }
            install(CORS) {
                for (origin in config.server.cors.allowedOrigins) {
                    allowHost(origin, listOf("http", "https"))
                }
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Options)
            }
            install(WebSockets) {
                pingPeriod = config.server.pingPeriod
                timeout = config.server.pingTimeout
                maxFrameSize = Long.MAX_VALUE
            }

            with(apiRoutesHandler) { configure() }
            with(webSocketHandler) { configure() }
            with(staticFilesHandler) { configure() }
        }
    }
}
