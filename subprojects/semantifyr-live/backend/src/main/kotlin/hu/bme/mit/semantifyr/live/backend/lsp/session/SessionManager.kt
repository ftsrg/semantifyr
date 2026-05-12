/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.exceptions.SessionLimitReachedException
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServiceRegistry
import hu.bme.mit.semantifyr.live.backend.lsp.service.SharedExecutorProvider
import hu.bme.mit.semantifyr.live.backend.utils.MdcContext
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import io.ktor.server.websocket.WebSocketServerSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Singleton
class SessionManager @Inject constructor(
    private val backendConfig: BackendConfig,
    private val languageServiceRegistry: LanguageServiceRegistry,
    private val sharedExecutorProvider: SharedExecutorProvider,
    private val verificationManager: VerificationManager,
    private val verificationExecutor: VerificationExecutor,
) : AutoCloseable {

    private val logger by loggerFactory()

    private val globalGate = Semaphore(backendConfig.sessionManager.maxSessionsGlobal)
    private val liveSessions = ConcurrentHashMap<String, LspSession>()

    val activeSessions: Int
        get() = backendConfig.sessionManager.maxSessionsGlobal - globalGate.availablePermits

    val maxSessions = backendConfig.sessionManager.maxSessionsGlobal

    fun getSessionInfos(): List<SessionInfo> {
        return liveSessions.values.map {
            it.currentSessionInfo()
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
        try {
            doRunSession(webSocketSession, remoteIp, flavor)
        } finally {
            globalGate.release()
        }
    }

    private suspend fun doRunSession(
        webSocketSession: WebSocketServerSession,
        remoteIp: String,
        flavor: Flavor,
    ) {
        val session = createLspSession(flavor, remoteIp, webSocketSession)
        liveSessions[session.sessionId] = session
        logger.info { "Created session=${session.sessionId} for ip=$remoteIp flavor=${flavor.id}" }
        try {
            session.run()
        } finally {
            liveSessions.remove(session.sessionId)
            session.close()
            cleanupSessionDirectory(session)
            logger.info { "Ended session=${session.sessionId} for ip=$remoteIp" }
        }
    }

    fun cancelSession(sessionId: String): Boolean {
        val session = liveSessions[sessionId]
        if (session != null) {
            logger.info { "Admin cancels sessionId=$sessionId" }
            session.close()
            return true
        }
        logger.warn { "Admin cancel for unknown sessionId=$sessionId" }
        return false
    }

    fun cancelVerification(sessionId: String, requestId: String): Boolean {
        logger.info { "Admin cancels verification sessionId=$sessionId requestId=$requestId" }
        return verificationManager.cancel(requestId)
    }

    override fun close() {
        logger.info { "Closing session manager" }
        for (session in liveSessions.values) {
            session.close()
        }
        liveSessions.clear()
    }

    private fun createLspSession(
        flavor: Flavor,
        remoteIp: String,
        webSocketSession: WebSocketServerSession,
    ): LspSession {
        val sessionId = UUID.randomUUID().toString()
        val workingDirectoryPath = backendConfig.sessionManager.rootWorkPath.resolve("sessions").resolve(sessionId)
        workingDirectoryPath.toFile().mkdirs()
        val sessionContext = SessionContext(
            sessionId = sessionId,
            remoteIp = remoteIp,
            flavor = flavor,
            workingDirectoryPath = workingDirectoryPath,
        )
        return LspSession(
            sessionContext = sessionContext,
            languageServices = languageServiceRegistry.forFlavor(flavor),
            sharedExecutorProvider = sharedExecutorProvider,
            verificationManager = verificationManager,
            verificationExecutor = verificationExecutor,
            webSocketSession = webSocketSession,
            libraryRoot = backendConfig.sessionManager.semanticLibrariesPath,
        )
    }

    private fun cleanupSessionDirectory(session: LspSession) {
        val dir = session.sessionContext.workingDirectoryPath
        try {
            dir.toFile().deleteRecursively()
        } catch (e: Throwable) {
            logger.warn { "Failed to clean up session directory $dir: $e" }
        }
    }
}
