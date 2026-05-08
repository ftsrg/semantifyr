/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.lsp.LspFrameCodec
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspServerRawConnector
import hu.bme.mit.semantifyr.live.backend.server.WorkspaceLayout
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.utils.process.destroyTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@SessionScoped
class LspServerRawRunner @Inject constructor(
    private val context: SessionContext,
    private val config: BackendConfig,
) : LspServerRawConnector {

    private val logger by loggerFactory()

    private val flavor
        get() = context.flavor
    private val workingDirectoryPath
        get() = context.workingDirectoryPath

    private val outgoing = Channel<String>(Channel.BUFFERED)
    private val incoming = Channel<String>(Channel.BUFFERED)

    override suspend fun sendToServer(raw: String) {
        outgoing.send(raw)
    }

    override suspend fun receiveFromServer(): String? {
        return incoming.receiveCatching().getOrNull()
    }

    suspend fun run() = coroutineScope {
        logger.info { "Running LSP server (flavor=${flavor.id}, workspace=$workingDirectoryPath)" }

        initializeWorkspace()
        try {
            val serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("127.0.0.1", 0))
            }
            try {
                val port = serverSocket.localPort
                logger.info { "Listening for LSP server on 127.0.0.1:$port (flavor=${flavor.id})" }
                val process = startProcess(port)
                try {
                    launchProcessOutputDrain(process)
                    val clientSocket = acceptClientConnection(serverSocket, process)
                    try {
                        launchOutgoingPump(clientSocket)
                        launchIncomingPump(clientSocket)
                        runInterruptible(Dispatchers.IO) { process.waitFor() }
                        logger.info { "LSP process exited (flavor=${flavor.id}, exitCode=${process.exitValue()})" }
                    } finally {
                        withContext(NonCancellable) {
                            outgoing.close()
                            incoming.close()
                            try {
                                clientSocket.close()
                            } catch (_: IOException) {
                                // already closed
                            }
                        }
                    }
                } finally {
                    withContext(NonCancellable) {
                        process.destroyTree()
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    try {
                        serverSocket.close()
                    } catch (_: IOException) {
                        // already closed
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                cleanUpWorkspace()
            }
        }
    }

    private fun initializeWorkspace() {
        logger.info { "Initializing workspace $workingDirectoryPath" }
        workingDirectoryPath.createDirectories()
        workingDirectoryPath.resolve(flavor.fileName).writeText("")
        val layout = flavor.workspaceLayout
        if (layout is WorkspaceLayout.WithLibrary) {
            copyLibrary(layout)
        }
    }

    private fun copyLibrary(layout: WorkspaceLayout.WithLibrary) {
        val semanticLibrariesPath = config.sessionManager.semanticLibrariesPath
            ?: error("Semantic libraries directory is not set. Set it via sessionManager.semanticLibrariesDirectory or the environment.")
        val librarySource = semanticLibrariesPath.resolve(layout.libraryRelativePath)
        require(Files.isDirectory(librarySource)) {
            "Library directory for flavor '${flavor.id}' not found: $librarySource"
        }
        val libraryTarget = workingDirectoryPath.resolve(layout.workspaceTargetName)
        logger.info { "Copying library $librarySource -> $libraryTarget" }
        librarySource.toFile().copyRecursively(libraryTarget.toFile(), overwrite = true)
    }

    private fun startProcess(port: Int): Process {
        val lspBinariesPath = config.sessionManager.lspBinariesPath ?: error("Binary directory is not set. Set it via sessionManager.lspBinariesDirectory or the environment.")
        val binary = lspBinariesPath.resolve(flavor.binaryRelativePath)
        require(Files.isExecutable(binary)) {
            "LSP binary for flavor '${flavor.id}' not found or not executable: $binary"
        }

        logger.info { "Spawning LSP server (flavor=${flavor.id}, binary=$binary, workspace=$workingDirectoryPath, socketPort=$port)" }
        return ProcessBuilder(binary.absolutePathString(), "--socket=$port", "--code-lens-mode=none")
            .directory(workingDirectoryPath.toFile())
            .redirectErrorStream(false)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    }

    private suspend fun CoroutineScope.acceptClientConnection(serverSocket: ServerSocket, process: Process): Socket {
        val watchdog = launch(Dispatchers.IO) {
            runInterruptible { process.waitFor() }
            try {
                serverSocket.close()
            } catch (_: IOException) {
                // already closed
            }
        }
        try {
            val socket = runInterruptible(Dispatchers.IO) { serverSocket.accept() }
            watchdog.cancel()
            logger.info { "LSP server connected from ${socket.remoteSocketAddress} (flavor=${flavor.id})" }
            return socket
        } catch (e: IOException) {
            if (!process.isAlive) {
                error("LSP process exited before connecting back (flavor=${flavor.id}, exitCode=${process.exitValue()})")
            }
            throw e
        }
    }

    private fun CoroutineScope.launchOutgoingPump(socket: Socket) {
        launch(Dispatchers.IO) {
            try {
                outgoing.consumeEach { raw ->
                    runInterruptible {
                        LspFrameCodec.writeFrame(socket.getOutputStream(), raw)
                    }
                }
            } catch (_: IOException) {
                // socket closed during shutdown
            }
            logger.debug { "Outgoing pump ended" }
        }
    }

    private fun CoroutineScope.launchIncomingPump(socket: Socket) {
        launch(Dispatchers.IO) {
            try {
                while (true) {
                    val raw = runInterruptible {
                        LspFrameCodec.readFrame(socket.getInputStream())
                    } ?: break
                    incoming.send(raw)
                }
            } catch (_: IOException) {
                // socket closed during shutdown
            } finally {
                incoming.close()
            }
            logger.debug { "Incoming pump ended" }
        }
    }

    private fun CoroutineScope.launchProcessOutputDrain(process: Process) {
        launch(Dispatchers.IO) {
            runInterruptible {
                process.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        logger.debug { "lsp.stdout: $line" }
                    }
                }
            }
        }
        launch(Dispatchers.IO) {
            runInterruptible {
                process.errorStream.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        logger.debug { "lsp.stderr: $line" }
                    }
                }
            }
        }
    }

    private fun cleanUpWorkspace() {
        try {
            logger.info { "Cleaning workspace $workingDirectoryPath" }
            workingDirectoryPath.toFile().deleteRecursively()
        } catch (e: IOException) {
            logger.error { "Failed to delete workspace $workingDirectoryPath: $e" }
        } catch (e: SecurityException) {
            logger.error { "Failed to delete workspace $workingDirectoryPath: $e" }
        }
    }
}
