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
import hu.bme.mit.semantifyr.live.backend.session.WorkspaceSweeper
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*

@Singleton
class Server @Inject constructor(
    private val config: BackendConfig,
    private val sessionManager: SessionManager,
    private val workspaceSweeper: WorkspaceSweeper,
    private val apiRoutesHandler: ApiRoutesHandler,
    private val webSocketHandler: WebSocketHandler,
    private val adminHandler: AdminHandler,
    private val staticFilesHandler: StaticFilesHandler,
) : AutoCloseable {

    private val logger by loggerFactory()

    private lateinit var ktorServer: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    fun start() {
        check(!this::ktorServer.isInitialized) {
            "The server has already been started!"
        }

        workspaceSweeper.sweep()

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
            install(WebSockets) {
                pingPeriod = config.server.pingPeriod
                timeout = config.server.pingTimeout
                maxFrameSize = config.server.maxWsFrameSize
            }

            with(apiRoutesHandler) { configure() }
            with(adminHandler) { configure() }
            with(webSocketHandler) { configure() }
            with(staticFilesHandler) { configure() }
        }
    }
}
