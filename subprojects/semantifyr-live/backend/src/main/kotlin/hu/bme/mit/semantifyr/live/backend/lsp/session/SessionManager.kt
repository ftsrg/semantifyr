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
import hu.bme.mit.semantifyr.live.backend.WorkspaceLayout
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.exceptions.SessionLimitReachedException
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServiceRegistry
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageServer
import hu.bme.mit.semantifyr.live.backend.lsp.transport.WebSocketLspConnector
import hu.bme.mit.semantifyr.live.backend.utils.MdcContext
import hu.bme.mit.semantifyr.live.backend.utils.currentMdcContextBlocking
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import io.ktor.server.websocket.WebSocketServerSession
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Singleton
class SessionManager @Inject constructor(
    private val backendConfig: BackendConfig,
    private val languageServiceRegistry: LanguageServiceRegistry,
) : AutoCloseable {

    private val logger by loggerFactory()

    private val globalGate = Semaphore(backendConfig.sessionManager.maxSessionsGlobal)
    private val activeSessions = ConcurrentHashMap<String, ActiveSession>()

    val activeSessionsCount: Int
        get() = backendConfig.sessionManager.maxSessionsGlobal - globalGate.availablePermits

    val maxSessions = backendConfig.sessionManager.maxSessionsGlobal

    fun getSessionInfos(): List<SessionInfo> {
        return activeSessions.values.map {
            it.sessionInfoBuilder.build()
        }
    }

    suspend fun runSession(
        webSocketSession: WebSocketServerSession,
        remoteIp: String,
        flavor: Flavor,
    ) {
        if (!globalGate.tryAcquire()) {
            logger.warn { "Global session limit reached (active=$activeSessionsCount, max=$maxSessions)" }
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
        val sessionContext = createSessionContext(webSocketSession, remoteIp, flavor)
        val injector = languageServiceRegistry.injectorFor(flavor)
        withSessionScope(sessionContext) {
            withContext(MdcContext("sessionId" to sessionContext.sessionId)) {
                val sessionRunContext = injector.getInstance(SessionRunContext::class.java)
                val sessionDocumentManager = injector.getInstance(SessionDocumentManager::class.java)
                val sessionLanguageServer = injector.getInstance(SessionLanguageServer::class.java)
                val sessionVerificationManager = injector.getInstance(SessionVerificationManager::class.java)
                val sessionInfoBuilder = injector.getInstance(SessionInfoBuilder::class.java)

                val active = ActiveSession(
                    sessionContext = sessionContext,
                    sessionRunContext = sessionRunContext,
                    sessionDocumentManager = sessionDocumentManager,
                    sessionVerificationManager = sessionVerificationManager,
                    sessionInfoBuilder = sessionInfoBuilder,
                )
                activeSessions[sessionContext.sessionId] = active
                logger.info { "Created session=${sessionContext.sessionId} for ip=$remoteIp flavor=${flavor.id}" }

                try {
                    stageLibraryOnDisk(sessionContext)
                    loadLibraryFiles(sessionContext, sessionDocumentManager)

                    val connector = WebSocketLspConnector(sessionContext.webSocketSession, sessionLanguageServer)
                    val launchContext = currentMdcContextBlocking() + currentSessionScopeElement()
                    try {
                        sessionRunContext.coroutineScope.async(launchContext) {
                            logger.info { "Session ${sessionContext.sessionId} started flavor=${flavor.id}" }
                            connector.run()
                        }.await()
                    } finally {
                        sessionRunContext.coroutineScope.cancel()
                    }
                } finally {
                    activeSessions.remove(sessionContext.sessionId)
                    sessionDocumentManager.unloadAll()
                    cleanupSessionDirectory(sessionContext)
                    logger.info { "Ended session=${sessionContext.sessionId} for ip=$remoteIp" }
                }
            }
        }
    }

    fun cancelSession(sessionId: String): Boolean {
        val active = activeSessions[sessionId]
        if (active != null) {
            logger.info { "Admin cancels sessionId=$sessionId" }
            active.sessionRunContext.coroutineScope.cancel()
            return true
        }
        logger.warn { "Admin cancel for unknown sessionId=$sessionId" }
        return false
    }

    fun cancelVerification(verificationId: String): Boolean {
        logger.info { "Admin cancels verification verificationId=$verificationId" }
        for (active in activeSessions.values) {
            if (active.sessionVerificationManager.cancel(verificationId)) {
                return true
            }
        }
        return false
    }

    override fun close() {
        logger.info { "Closing session manager" }
        for (active in activeSessions.values) {
            active.sessionRunContext.coroutineScope.cancel()
        }
        activeSessions.clear()
    }

    private fun createSessionContext(
        webSocketSession: WebSocketServerSession,
        remoteIp: String,
        flavor: Flavor,
    ): SessionContext {
        val sessionId = UUID.randomUUID().toString()
        val workingDirectoryPath = backendConfig.sessionManager.rootWorkPath.resolve("sessions").resolve(sessionId)
        workingDirectoryPath.toFile().mkdirs()
        return SessionContext(
            sessionId = sessionId,
            remoteIp = remoteIp,
            flavor = flavor,
            workingDirectoryPath = workingDirectoryPath,
            webSocketSession = webSocketSession,
        )
    }

    private fun stageLibraryOnDisk(sessionContext: SessionContext) {
        val layout = sessionContext.flavor.workspaceLayout as? WorkspaceLayout.WithLibrary ?: return
        val source = backendConfig.sessionManager.semanticLibrariesPath?.resolve(layout.libraryRelativePath)
            ?: error("Semantic library root is not configured")
        val target = sessionContext.workingDirectoryPath.resolve(layout.workspaceTargetName)
        require(Files.isDirectory(source)) {
            "Library directory missing for flavor=${sessionContext.flavor.id}: $source"
        }
        if (Files.isDirectory(target)) {
            return
        }
        source.toFile().copyRecursively(target.toFile(), overwrite = true)
    }

    private fun loadLibraryFiles(sessionContext: SessionContext, sessionDocumentManager: SessionDocumentManager) {
        val layout = sessionContext.flavor.workspaceLayout as? WorkspaceLayout.WithLibrary ?: return
        val libraryDir = sessionContext.workingDirectoryPath.resolve(layout.workspaceTargetName)
        sessionDocumentManager.loadLibraryDirectory(libraryDir)
    }

    private fun cleanupSessionDirectory(sessionContext: SessionContext) {
        val dir = sessionContext.workingDirectoryPath
        try {
            dir.toFile().deleteRecursively()
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to clean up session directory $dir" }
        }
    }
}

private class ActiveSession(
    val sessionContext: SessionContext,
    val sessionRunContext: SessionRunContext,
    val sessionDocumentManager: SessionDocumentManager,
    val sessionVerificationManager: SessionVerificationManager,
    val sessionInfoBuilder: SessionInfoBuilder,
)
