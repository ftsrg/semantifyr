/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.TimeUnit

abstract class BaseShellExecutor {

    protected abstract val logger: Logger

    protected abstract val binaryName: String

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    protected fun probeAvailability(
        probeArgs: List<String>,
        timeoutSeconds: Long = 2,
        expectedExitCode: Int = 0,
    ): Boolean {
        val available = runCatching {
            val process = buildProcess(probeArgs)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == expectedExitCode
            }
        }.getOrElse { false }
        logger.debug { "$binaryName on PATH: $available" }
        return available
    }

    protected suspend fun runProcess(
        args: List<String>,
        workingDirectory: File,
        logFile: File? = null,
        errorFile: File? = null,
        header: String? = null,
    ): Int = coroutineScope {
        logger.info { "Starting $binaryName in ${workingDirectory.absolutePath}" }
        logger.debug { "$binaryName command: ${args.joinToString(" ")}" }

        prepareOutputFiles(logFile, errorFile, header)

        val logRedirect = logFile?.let { ProcessBuilder.Redirect.appendTo(it) } ?: ProcessBuilder.Redirect.DISCARD
        val errorRedirect = errorFile?.let { ProcessBuilder.Redirect.appendTo(it) } ?: ProcessBuilder.Redirect.DISCARD

        val process = buildProcess(args)
            .directory(workingDirectory)
            .redirectOutput(logRedirect)
            .redirectError(errorRedirect)
            .start()

        logger.debug { "$binaryName process started (pid ${process.pid()})" }

        ShellProcessTracker.track(process)
        try {
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
        } finally {
            ShellProcessTracker.untrack(process)
        }

        val exitCode = process.exitValue()
        logger.info { "$binaryName exited with code $exitCode" }
        exitCode
    }

    open fun isAvailable(): Boolean {
        return probeAvailability(listOf("-V"))
    }

    private fun buildProcess(args: List<String>): ProcessBuilder {
        val cmd = if (isWindows) {
            listOf("cmd", "/c", binaryName) + args
        } else {
            listOf(binaryName) + args
        }
        return ProcessBuilder(cmd)
    }

    private fun prepareOutputFiles(
        logFile: File?,
        errorFile: File?,
        header: String?,
    ) {
        logFile?.let { file ->
            file.ensureExists()
            file.bufferedWriter().use { writer ->
                if (header != null) writer.appendLine(header)
                writer.appendLine()
            }
        }
        errorFile?.ensureExists()
    }

    private fun File.ensureExists(): File {
        parentFile?.mkdirs()
        createNewFile()
        return this
    }
}

fun Process.destroyTree() {
    val handle = toHandle()
    handle.descendants().forEach { it.destroy() }
    handle.destroy()
}
