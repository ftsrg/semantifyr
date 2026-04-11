/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.utils.info
import hu.bme.mit.semantifyr.live.backend.utils.loggerFactory
import hu.bme.mit.semantifyr.live.backend.utils.warn
import io.ktor.server.websocket.*
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.MDC
import java.util.concurrent.ConcurrentHashMap

class SessionLimitReachedException(message: String) : RuntimeException(message)

@Singleton
class SessionManager @Inject constructor(
    val config: BackendConfig
) : AutoCloseable {

    private val logger by loggerFactory()

    @Inject
    private lateinit var liveSessionFactory: LiveSession.Factory

    private val globalGate = Semaphore(config.sessionManager.maxSessionsGlobal)

    // TODO: empty DeviceSessionManagers are never removed (small memory leak per unique IP)
    private val deviceSessionManagers = ConcurrentHashMap<String, DeviceSessionManager>()

    val activeSessions: Int
        get() = config.sessionManager.maxSessionsGlobal - globalGate.availablePermits

    val maxSessions = config.sessionManager.maxSessionsGlobal

    suspend fun runSession(webSocketSession: WebSocketServerSession, remoteIp: String, flavor: Flavor) {
        if (!globalGate.tryAcquire()) {
            logger.warn { "Global session limit reached (active=$activeSessions, max=$maxSessions)" }
            throw SessionLimitReachedException("Global session limit reached, please try again later.")
        }
        logger.info { "Global permit acquired for ip=$remoteIp (active=$activeSessions/$maxSessions)" }
        try {
            val deviceSessionManager = deviceSessionManagers.computeIfAbsent(remoteIp) {
                DeviceSessionManager(it)
            }
            deviceSessionManager.runSession(webSocketSession, flavor)
        } finally {
            globalGate.release()
            logger.info { "global permit released for ip=$remoteIp (active=$activeSessions)" }
        }
    }

    override fun close() {
        logger.info { "Closing session manager, stopping all active sessions" }
        for (deviceSessionManager in deviceSessionManagers.values) {
            deviceSessionManager.close()
        }
        deviceSessionManagers.clear()
    }

    inner class DeviceSessionManager(
        val remoteIp: String,
    ) {
        private val deviceGate = Semaphore(config.sessionManager.maxSessionsPerIp)
        private val liveSessions = ConcurrentHashMap<String, LiveSession>()

        suspend fun runSession(webSocketSession: WebSocketServerSession, flavor: Flavor) {
            if (!deviceGate.tryAcquire()) {
                logger.warn { "Session limit reached for ip=$remoteIp" }
                throw SessionLimitReachedException("Session limit reached for $remoteIp")
            }
            val sessionId = java.util.UUID.randomUUID().toString()

            MDC.put("sessionId", sessionId)
            val liveSession = liveSessionFactory.create(flavor, sessionId)
            liveSessions[liveSession.sessionId] = liveSession
            logger.info { "Created session for ip=$remoteIp with flavor=${flavor.id}" }
            try {
                liveSession.run(webSocketSession)
            } finally {
                liveSessions.remove(liveSession.sessionId)
                liveSession.close()
                deviceGate.release()
                logger.info { "Ended session for ip=$remoteIp" }
                MDC.remove("sessionId")
            }
        }

        fun close() {
            for (liveSession in liveSessions.values) {
                MDC.put("sessionId", liveSession.sessionId)
                logger.info { "Force closing session for ip=$remoteIp" }
                liveSession.close()
                MDC.remove("sessionId")
            }
        }
    }

}
