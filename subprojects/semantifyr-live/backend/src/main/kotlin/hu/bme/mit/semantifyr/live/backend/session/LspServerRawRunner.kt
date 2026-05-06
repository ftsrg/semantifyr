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
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private fun Process.destroyTree() {
    val handle = toHandle()
    handle.descendants().forEach { child ->
        try {
            child.destroy()
        } catch (_: IllegalStateException) {
            child.destroyForcibly()
        }
    }
    handle.destroy()
}

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
            val process = startProcess()
            try {
                launchOutgoingPump(process)
                launchIncomingPump(process)
                launchStderrDrain(process)
                runInterruptible(Dispatchers.IO) { process.waitFor() }
                logger.info { "LSP process exited (flavor=${flavor.id}, exitCode=${process.exitValue()})" }
            } finally {
                withContext(NonCancellable) {
                    outgoing.close()
                    incoming.close()
                    process.destroyTree()
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

    private fun startProcess(): Process {
        val lspBinariesPath = config.sessionManager.lspBinariesPath ?: error("Binary directory is not set. Set it via sessionManager.lspBinariesDirectory or the environment.")
        val binary = lspBinariesPath.resolve(flavor.binaryRelativePath)
        require(Files.isExecutable(binary)) {
            "LSP binary for flavor '${flavor.id}' not found or not executable: $binary"
        }

        logger.info { "Spawning LSP server (flavor=${flavor.id}, binary=$binary, workspace=$workingDirectoryPath)" }
        return ProcessBuilder(binary.absolutePathString(), "--code-lens-mode=none")
            .directory(workingDirectoryPath.toFile())
            .redirectErrorStream(false)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    }

    private fun CoroutineScope.launchOutgoingPump(process: Process) {
        launch(Dispatchers.IO) {
            try {
                outgoing.consumeEach { raw ->
                    runInterruptible {
                        LspFrameCodec.writeFrame(process.outputStream, raw)
                    }
                }
            } catch (_: IOException) {
                // process stdin closed during shutdown
            }
            logger.debug { "Outgoing pump ended" }
        }
    }

    private fun CoroutineScope.launchIncomingPump(process: Process) {
        launch(Dispatchers.IO) {
            try {
                while (true) {
                    val raw = runInterruptible {
                        LspFrameCodec.readFrame(process.inputStream)
                    } ?: break
                    incoming.send(raw)
                }
            } catch (_: IOException) {
                // process stdout closed during shutdown
            } finally {
                incoming.close()
            }
            logger.debug { "Incoming pump ended" }
        }
    }

    private fun CoroutineScope.launchStderrDrain(process: Process) {
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
