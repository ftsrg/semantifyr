/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.execution

import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionResult
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionSpecification
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutor
import hu.bme.mit.semantifyr.backends.theta.utils.destroyTree
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ShellBasedThetaXstsExecutor : ThetaXstsExecutor() {

    override val logger by loggerFactory()

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    override fun isAvailable(): Boolean {
        val available = runCatching {
            val process = thetaProcessBuilder(listOf("--version"))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        }.getOrElse { false }
        logger.debug { "theta-xsts-cli on PATH: $available" }
        return available
    }

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification) = coroutineScope {
        logger.info { "Starting theta-xsts-cli in ${thetaExecutionSpecification.workingDirectory.absolutePath}" }
        logger.debug { "theta-xsts-cli command: ${thetaExecutionSpecification.command.joinToString(" ")}" }

        prepareOutputFiles(thetaExecutionSpecification)

        val logRedirect = thetaExecutionSpecification.logFile?.let {
            ProcessBuilder.Redirect.appendTo(it)
        } ?: ProcessBuilder.Redirect.DISCARD

        val errorRedirect = thetaExecutionSpecification.errorFile?.let {
            ProcessBuilder.Redirect.appendTo(it)
        } ?: ProcessBuilder.Redirect.DISCARD

        val process = thetaProcessBuilder(thetaExecutionSpecification.command)
            .directory(thetaExecutionSpecification.workingDirectory)
            .redirectOutput(logRedirect)
            .redirectError(errorRedirect)
            .start()

        logger.debug { "theta-xsts-cli process started (pid ${process.pid()})" }

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

        logger.info { "theta-xsts-cli exited with code ${process.exitValue()}" }

        ThetaExecutionResult(process.exitValue())
    }

    private fun thetaProcessBuilder(args: List<String>): ProcessBuilder {
        val cmd = if (isWindows) listOf("cmd", "/c", "theta-xsts-cli") + args else listOf("theta-xsts-cli") + args

        return ProcessBuilder(cmd)
    }

}
