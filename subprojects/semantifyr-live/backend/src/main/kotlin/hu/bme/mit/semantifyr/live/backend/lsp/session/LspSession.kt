/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import hu.bme.mit.semantifyr.lang.ide.server.concurrent.WorkManager
import hu.bme.mit.semantifyr.live.backend.WorkspaceLayout
import hu.bme.mit.semantifyr.live.backend.data.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.data.SessionLspInfo
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageServer
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageServerAccess
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionRequestManager
import hu.bme.mit.semantifyr.live.backend.lsp.service.SharedExecutorProvider
import hu.bme.mit.semantifyr.live.backend.lsp.transport.WebSocketLspConnector
import hu.bme.mit.semantifyr.live.backend.utils.withSessionIdMdc
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.services.LanguageClient
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlin.time.TimeSource

class LspSession(
    val sessionContext: SessionContext,
    val languageServices: LanguageServices,
    private val sharedExecutorProvider: SharedExecutorProvider,
    private val verificationManager: VerificationManager,
    private val verificationExecutor: VerificationExecutor,
    private val webSocketSession: WebSocketSession,
    private val libraryRoot: Path?,
) : AutoCloseable {

    private val logger by loggerFactory()

    val sessionId = sessionContext.sessionId
    val flavor = sessionContext.flavor

    // FIXME: too much lateinit public fields

    val workManager = WorkManager()
    val sessionDocumentManager = SessionDocumentManager(languageServices, sessionContext.workingDirectoryPath)
    val sessionLanguageServer = SessionLanguageServer(
        this,
        sessionDocumentManager,
        verificationManager,
        verificationExecutor,
        languageServices,
        workManager,
    )

    lateinit var coroutineScope: CoroutineScope
        private set

    lateinit var requestManager: SessionRequestManager
        private set

    lateinit var webSocketLspConnector: WebSocketLspConnector
        private set

    private lateinit var languageClient: LanguageClient

    private val startMark = TimeSource.Monotonic.markNow()

    fun client(): LanguageClient {
        return languageClient
    }

    internal fun attachClient(client: LanguageClient) {
        languageClient = client
    }

    fun executeCommandUnderReadLock(params: ExecuteCommandParams): CompletableFuture<Any?> {
        return requestManager.runRead { cancelIndicator ->
            val access = SessionLanguageServerAccess.forSession(this, cancelIndicator)
            languageServices.commandService.execute(params, access, cancelIndicator)
        }
    }

    suspend fun run() {
        withSessionScope(this) {
            stageLibraryOnDisk()
            loadLibraryFiles()

            val sessionCoroutineContext = currentCoroutineContext() + // copy session scope
                sharedExecutorProvider.dispatcher + // override the caller dispatcher
                withSessionIdMdc(sessionId)

            coroutineScope = CoroutineScope(sessionCoroutineContext)
            requestManager = SessionRequestManager(
                coroutineScope,
                sessionCoroutineContext,
                languageServices.operationCanceledManager,
            )
            webSocketLspConnector = WebSocketLspConnector(
                webSocketSession,
                sessionLanguageServer,
            )

            // run in the session scope
            coroutineScope.async {
                logger.info { "Session $sessionId started flavor=${flavor.id}" }
                webSocketLspConnector.run()
            }.await()
        }
    }

    fun currentSessionInfo(): SessionInfo {
        val lspInfo = if (::webSocketLspConnector.isInitialized) {
            webSocketLspConnector.getInfo()
        } else {
            SessionLspInfo(Duration.ZERO, Duration.ZERO)
        }
        return SessionInfo(
            sessionId = sessionId,
            remoteIp = sessionContext.remoteIp,
            flavorId = flavor.id,
            uptime = startMark.elapsedNow(),
            workingDirectory = sessionContext.workingDirectoryPath.toString(),
            activeVerifications = verificationManager.activeFor(sessionId).map {
                ActiveVerificationInfo(it.verificationId, it.portfolioId, it.kind, it.elapsed)
            },
            sessionLspInfo = lspInfo,
        )
    }

    override fun close() {
        logger.info { "Closing session $sessionId" }
        if (::coroutineScope.isInitialized) {
            coroutineScope.cancel()
        }
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
