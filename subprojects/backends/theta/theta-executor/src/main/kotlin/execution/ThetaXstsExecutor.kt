/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.wrapper.execution

import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import org.slf4j.Logger
import java.io.File

class ThetaExecutionResult(
    val exitCode: Int,
)

class ThetaExecutionSpecification(
    val workingDirectory: File,
    val command: List<String>,
    val logFile: File? = null,
    val errorFile: File? = null,
)

abstract class ThetaXstsExecutor {

    protected abstract val logger: Logger

    abstract fun isAvailable(): Boolean

    abstract suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult

    protected fun prepareOutputFiles(spec: ThetaExecutionSpecification) {
        spec.logFile?.let {
            it.ensureExists()
            it.bufferedWriter().use { writer ->
                writer.appendLine("Running theta with command:")
                writer.appendLine(spec.command.joinToString(" "))
                writer.appendLine()
            }
            logger.info { "Writing theta stdout to ${it.absolutePath}" }
        }
        spec.errorFile?.let {
            it.ensureExists()
            logger.info { "Writing theta stderr to ${it.absolutePath}" }
        }
    }

    protected fun File.ensureExists(): File {
        parentFile?.mkdirs()
        createNewFile()
        return this
    }

    companion object {
        val logger by loggerFactory()

        fun of(spec: ThetaExecutorSpec = ThetaExecutorSpec.Auto): ThetaXstsExecutor {
            return when (spec) {
                ThetaExecutorSpec.Auto -> autoDetect()
                ThetaExecutorSpec.Shell -> ShellBasedThetaXstsExecutor().requireAvailable("Shell-based Theta executor: theta-xsts-cli is not on PATH.")
                is ThetaExecutorSpec.Docker -> DockerBasedThetaXstsExecutor(spec.image).requireAvailable("Docker-based Theta executor (image ${spec.image}): the Docker CLI is not on PATH.")
            }
        }

        private fun autoDetect(): ThetaXstsExecutor {
            logger.debug("Auto detecting theta executor.")

            val shell = ShellBasedThetaXstsExecutor()
            if (shell.isAvailable()) {
                logger.debug("Shell is available")
                return shell
            }
            logger.debug("Shell executor is not available, fallback to docker.")

            val docker = DockerBasedThetaXstsExecutor()
            if (docker.isAvailable()) {
                return docker
            }
            error("Could not find any working Theta XSTS executor (tried shell and docker).")
        }

        private fun <T : ThetaXstsExecutor> T.requireAvailable(message: String): T {
            check(isAvailable()) { message }
            return this
        }
    }
}
