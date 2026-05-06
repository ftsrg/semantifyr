/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.guice.common.Seed
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.server.Flavor
import hu.bme.mit.semantifyr.live.backend.server.SessionInfo
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import io.ktor.server.websocket.*
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.sync.Semaphore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SessionLimitReachedException(message: String) : RuntimeException(message)

@Singleton
class SessionManager @Inject constructor(
    val config: BackendConfig,
    private val injector: Injector,
) : AutoCloseable {

    private val logger by loggerFactory()

    private val globalGate = Semaphore(config.sessionManager.maxSessionsGlobal)

    private val deviceSessionManagers = ConcurrentHashMap<String, DeviceSessionManager>()

    val activeSessions: Int
        get() = config.sessionManager.maxSessionsGlobal - globalGate.availablePermits

    val maxSessions = config.sessionManager.maxSessionsGlobal

    fun getSessionInfos(): List<SessionInfo> {
        return deviceSessionManagers.values.flatMap { deviceManager ->
            deviceManager.liveSessions.values.map { session ->
                session.getSessionInfo()
            }
        }
    }

    suspend fun runSession(
        webSocketSession: WebSocketServerSession,
        remoteIp: String,
        flavor: Flavor,
    ) {
        if (!globalGate.tryAcquire()) {
            logger.warn { "Global session limit reached (active=$activeSessions, max=$maxSessions)" }
            throw SessionLimitReachedException("Global session limit reached, please try again later.")
        }
        logger.info { "Global permit acquired for ip=$remoteIp (active=$activeSessions/$maxSessions)" }
        try {
            val deviceSessionManager = deviceSessionManagers.computeIfAbsent(remoteIp) {
                DeviceSessionManager(it)
            }
            try {
                deviceSessionManager.runSession(webSocketSession, flavor)
            } finally {
                deviceSessionManagers.compute(remoteIp) { _, existing ->
                    if (existing === deviceSessionManager && existing.liveSessions.isEmpty()) null else existing
                }
            }
        } finally {
            globalGate.release()
            logger.info { "Global permit released for ip=$remoteIp (active=$activeSessions/$maxSessions)" }
        }
    }

    private fun createLspSession(
        flavor: Flavor,
        remoteIp: String,
        webSocketSession: WebSocketSession,
    ): LspSession {
        val sessionId = UUID.randomUUID().toString()
        val context = SessionContext(
            sessionId = sessionId,
            remoteIp = remoteIp,
            flavor = flavor,
            webSocketSession = webSocketSession,
            workingDirectoryPath = config.sessionManager.rootWorkPath.resolve("sessions").resolve(sessionId),
        )
        val seed = Seed().apply {
            seed(SessionContext::class.java, context)
        }
        return withSessionScope(seed) {
            injector.getInstance(LspSession::class.java)
        }
    }

    fun cancelSession(sessionId: String): Boolean {
        for (deviceManager in deviceSessionManagers.values) {
            val session = deviceManager.liveSessions[sessionId]
            if (session != null) {
                logger.info { "Admin cancels sessionId=$sessionId" }
                session.close()
                return true
            }
        }
        logger.warn { "Admin cancel request for unknown sessionId=$sessionId" }
        return false
    }

    suspend fun cancelVerification(sessionId: String, requestId: String): Boolean {
        for (deviceManager in deviceSessionManagers.values) {
            val lspSession = deviceManager.liveSessions[sessionId]
            if (lspSession != null) {
                logger.info { "Admin cancels verification (sessionId=$sessionId, requestId=$requestId)" }
                lspSession.cancelVerification(requestId)
                return true
            }
        }
        logger.warn { "Admin cancel verification for unknown sessionId=$sessionId (requestId=$requestId)" }
        return false
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
        internal val liveSessions = ConcurrentHashMap<String, LspSession>()

        suspend fun runSession(webSocketSession: WebSocketServerSession, flavor: Flavor) {
            if (!deviceGate.tryAcquire()) {
                logger.warn { "Session limit reached for ip=$remoteIp" }
                throw SessionLimitReachedException("Session limit reached for $remoteIp")
            }
            val liveSession = createLspSession(flavor, remoteIp, webSocketSession)
            liveSessions[liveSession.sessionId] = liveSession
            logger.info { "Created session=${liveSession.sessionId} for ip=$remoteIp with flavor=${flavor.id}" }
            try {
                liveSession.run()
            } finally {
                liveSessions.remove(liveSession.sessionId)
                liveSession.close()
                deviceGate.release()
                logger.info { "Ended session for ip=$remoteIp" }
            }
        }

        fun close() {
            logger.info { "Closing sessions for ip=$remoteIp" }
            for (liveSession in liveSessions.values) {
                liveSession.close()
            }
        }
    }

}
