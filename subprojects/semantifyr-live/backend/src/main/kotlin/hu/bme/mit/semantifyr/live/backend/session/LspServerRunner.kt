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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import hu.bme.mit.semantifyr.live.backend.utils.debug
import hu.bme.mit.semantifyr.live.backend.utils.info
import hu.bme.mit.semantifyr.live.backend.utils.loggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

private fun Process.destroyTree() {
    val handle = toHandle()
    handle.descendants().forEach { child ->
        try {
            child.destroy()
        } catch (_: Throwable) {
            child.destroyForcibly()
        }
    }
    handle.destroy()
}

@Singleton
class LspServerRunner {

    private val logger by loggerFactory()

    @Inject
    private lateinit var config: BackendConfig

    suspend fun runLspWith(
        flavor: Flavor,
        workingDirectoryPath: Path,
        scope: CoroutineScope,
        block: suspend (stdin: OutputStream, stdout: InputStream) -> Unit,
    ) {
        val process = startProcess(flavor, workingDirectoryPath)

        val stderrJob = scope.launch(Dispatchers.IO) {
            runInterruptible {
                process.errorStream.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        logger.debug { "lsp.stderr: $line" }
                    }
                }
            }
        }

        try {
            block(process.outputStream, process.inputStream)
        } finally {
            withContext(NonCancellable) {
                try {
                    process.outputStream.close()
                } catch (_: Throwable) {
                    // already closed
                }
                process.destroyTree()
                stderrJob.cancelAndJoin()
                logger.info { "LSP process stopped workspace=$workingDirectoryPath" }
            }
        }
    }

    private fun startProcess(flavor: Flavor, workingDirectoryPath: Path): Process {
        val lspBinariesPath = config.sessionManager.lspBinariesPath
            ?: error("Binary directory is not set. Set it via the environment or in the config file under sessionManager.lspBinariesDirectory.")
        val binary = lspBinariesPath.resolve(flavor.binaryRelativePath)

        require(Files.isExecutable(binary)) {
            "LSP binary for flavor '${flavor.id}' not found or not executable: $binary"
        }

        val builder = ProcessBuilder(binary.absolutePathString())
            .directory(workingDirectoryPath.toFile())
            .redirectErrorStream(false)
        builder.redirectError(ProcessBuilder.Redirect.PIPE)

        logger.info { "Spawning LSP server flavor=${flavor.id} binary=$binary workspace=$workingDirectoryPath" }

        return builder.start()
    }
}
