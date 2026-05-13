/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Inject
import hu.bme.mit.semantifyr.lang.ide.server.concurrent.WorkManager
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.WorkspaceLayout
import hu.bme.mit.semantifyr.live.backend.data.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageServer
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageServerAccess
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionRequestManager
import hu.bme.mit.semantifyr.live.backend.lsp.transport.WebSocketLspConnector
import hu.bme.mit.semantifyr.live.backend.utils.currentMdcContextBlocking
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.services.LanguageClient
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.time.TimeSource

@SessionScoped
class LspSession @Inject constructor(
    val sessionContext: SessionContext,
    val languageServices: LanguageServices,
    val sessionDocumentManager: SessionDocumentManager,
    val workManager: WorkManager,
    val requestManager: SessionRequestManager,
    private val sessionClient: SessionClient,
    private val runContext: SessionRunContext,
    private val verificationManager: VerificationManager,
    private val verificationExecutor: VerificationExecutor,
    backendConfig: BackendConfig,
) : AutoCloseable {

    private val logger by loggerFactory()

    val sessionId = sessionContext.sessionId
    val flavor = sessionContext.flavor

    val coroutineScope: CoroutineScope
        get() = runContext.coroutineScope

    private val libraryRoot = backendConfig.sessionManager.semanticLibrariesPath

    private val sessionLanguageServer = SessionLanguageServer(
        this,
        sessionDocumentManager,
        verificationManager,
        verificationExecutor,
        languageServices,
        workManager,
    )

    private val startMark = TimeSource.Monotonic.markNow()

    fun client(): LanguageClient {
        return sessionClient.get()
    }

    internal fun attachClient(client: LanguageClient) {
        sessionClient.attach(client)
    }

    fun executeCommandUnderReadLock(params: ExecuteCommandParams): CompletableFuture<Any?> {
        return requestManager.runRead { cancelIndicator ->
            val access = SessionLanguageServerAccess.forSession(this, cancelIndicator)
            languageServices.commandService.execute(params, access, cancelIndicator)
        }
    }

    suspend fun run() {
        stageLibraryOnDisk()
        loadLibraryFiles()

        val connector = WebSocketLspConnector(sessionContext.webSocketSession, sessionLanguageServer)
        val launchContext = currentMdcContextBlocking() + currentSessionScopeElement()
        try {
            coroutineScope.async(launchContext) {
                logger.info { "Session $sessionId started flavor=${flavor.id}" }
                connector.run()
            }.await()
        } finally {
            coroutineScope.cancel()
        }
    }

    fun currentSessionInfo(): SessionInfo {
        return SessionInfo(
            sessionId = sessionId,
            remoteIp = sessionContext.remoteIp,
            flavorId = flavor.id,
            uptime = startMark.elapsedNow(),
            workingDirectory = sessionContext.workingDirectoryPath.toString(),
            activeVerifications = verificationManager.activeFor(sessionId).map {
                ActiveVerificationInfo(it.verificationId, it.portfolioId, it.kind, it.state, it.elapsed)
            },
        )
    }

    override fun close() {
        logger.info { "Closing session $sessionId" }
        coroutineScope.cancel()
        sessionDocumentManager.unloadAll()
    }

    private fun stageLibraryOnDisk() {
        val layout = flavor.workspaceLayout as? WorkspaceLayout.WithLibrary ?: return
        val source = libraryRoot?.resolve(layout.libraryRelativePath)
            ?: error("Semantic library root is not configured")
        val target = sessionContext.workingDirectoryPath.resolve(layout.workspaceTargetName)
        require(Files.isDirectory(source)) {
            "Library directory missing for flavor=${flavor.id}: $source"
        }
        if (Files.isDirectory(target)) {
            return
        }
        source.toFile().copyRecursively(target.toFile(), overwrite = true)
    }

    private fun loadLibraryFiles() {
        val layout = flavor.workspaceLayout as? WorkspaceLayout.WithLibrary ?: return
        val libraryDir = sessionContext.workingDirectoryPath.resolve(layout.workspaceTargetName)
        sessionDocumentManager.loadLibraryDirectory(libraryDir)
    }
}
