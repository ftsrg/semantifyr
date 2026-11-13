/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.utils.process.destroyTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

abstract class ShellBasedBackendExecutor : BackendExecutor {

    protected val logger by loggerFactory()

    protected abstract val binaryName: String

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    override fun isAvailable(): Boolean {
        return probeAvailability(listOf("-V"))
    }

    protected fun probeAvailability(
        probeArgs: List<String>,
        timeoutSeconds: Long = 2,
        expectedExitCode: Int = 0,
        binary: String = binaryName,
    ): Boolean {
        val available = runCatching {
            val process = buildProcess(binary, probeArgs)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyTree()
                false
            } else {
                process.exitValue() == expectedExitCode
            }
        }.getOrElse { false }
        logger.debug { "$binary on PATH: $available" }
        return available
    }

    protected suspend fun runProcess(
        arguments: List<String>,
        workingDirectory: File,
        logFile: File? = null,
        errorFile: File? = null,
    ): Int = coroutineScope {
        logger.info { "Starting $binaryName in ${workingDirectory.absolutePath}" }
        logger.debug { "$binaryName command: ${arguments.joinToString(" ")}" }

        prepareOutputFiles(logFile, errorFile)

        val logRedirect = logFile?.let {
            ProcessBuilder.Redirect.appendTo(it)
        } ?: ProcessBuilder.Redirect.DISCARD

        val errorRedirect = errorFile?.let {
            ProcessBuilder.Redirect.appendTo(it)
        } ?: ProcessBuilder.Redirect.DISCARD

        currentCoroutineContext().ensureActive()

        val process = buildProcess(binaryName, arguments)
            .directory(workingDirectory)
            .redirectOutput(logRedirect)
            .redirectError(errorRedirect)
            .start()

        logger.debug { "$binaryName process started (pid ${process.pid()})" }

        ShellProcessTracker.tracking(process) {
            try {
                runInterruptible(Dispatchers.IO) {
                    process.waitFor()
                }
            } finally {
                withContext(NonCancellable) {
                    process.destroyTree()
                    process.waitFor()
                }
            }
        }

        val exitCode = process.exitValue()
        logger.info { "$binaryName exited with code $exitCode" }
        exitCode
    }

    private fun buildProcess(binary: String, arguments: List<String>): ProcessBuilder {
        val command = if (isWindows) {
            listOf("cmd", "/c", binary) + arguments
        } else {
            listOf(binary) + arguments
        }
        return ProcessBuilder(command)
    }

}
