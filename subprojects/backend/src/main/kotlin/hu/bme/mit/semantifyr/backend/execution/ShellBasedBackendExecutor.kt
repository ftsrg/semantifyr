/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import hu.bme.mit.semantifyr.backend.BackendTimeoutException
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.utils.process.destroyTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
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
    ): Int = coroutineScope {
        logger.info { "Starting $binaryName in ${workingDirectory.absolutePath}" }
        logger.debug { "$binaryName command: ${args.joinToString(" ")}" }

        prepareOutputFiles(logFile, errorFile)

        val runexec = coroutineContext[Runexec]?.takeUnless { isWindows }
        val runexecResults = runexec?.let {
            File.createTempFile("runexec-", ".txt", workingDirectory)
        }

        val builder = if (runexec != null && runexecResults != null) {
            ProcessBuilder(runexec.wrap(listOf(binaryName) + args, workingDirectory, logFile, errorFile))
                .redirectOutput(runexecResults)
                .redirectErrorStream(true)
        } else {
            val logRedirect = logFile?.let {
                ProcessBuilder.Redirect.appendTo(it)
            } ?: ProcessBuilder.Redirect.DISCARD

            val errorRedirect = errorFile?.let {
                ProcessBuilder.Redirect.appendTo(it)
            } ?: ProcessBuilder.Redirect.DISCARD

            buildProcess(args)
                .redirectOutput(logRedirect)
                .redirectError(errorRedirect)
        }

        val process = builder
            .directory(workingDirectory)
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

        val exitCode = if (runexec != null && runexecResults != null) {
            runexecExitCode(runexec, runexecResults, process.exitValue())
        } else {
            process.exitValue()
        }
        logger.info { "$binaryName exited with code $exitCode" }
        exitCode
    }

    private fun runexecExitCode(
        runexec: Runexec,
        results: File,
        runexecExitCode: Int,
    ): Int {
        val output = results.readText()
        val run = RunexecRun.parse(output)
        runexec.record(run)
        if (run.isTimeout) {
            throw BackendTimeoutException("$binaryName exceeded the runexec ${run.terminationReason} limit")
        }
        check(run.terminationReason != "memory") { "$binaryName exceeded the runexec memory limit" }
        return run.exitCode ?: error("runexec exited with $runexecExitCode without running $binaryName: ${output.lines().lastOrNull { it.isNotBlank() }.orEmpty()}")
    }

    private fun buildProcess(args: List<String>): ProcessBuilder {
        val cmd = if (isWindows) {
            listOf("cmd", "/c", binaryName) + args
        } else {
            listOf(binaryName) + args
        }
        return ProcessBuilder(cmd)
    }

}
