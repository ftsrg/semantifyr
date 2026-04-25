/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backends.theta.execution.DockerBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.backends.theta.execution.ShellBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.logging.loggerFactory
import java.io.File

sealed interface ThetaExecutorSpec {
    object Auto : ThetaExecutorSpec

    object Shell : ThetaExecutorSpec

    data class Docker(
        val image: String = DEFAULT_IMAGE,
    ) : ThetaExecutorSpec {
        companion object {
            const val DEFAULT_IMAGE = "ftsrg/theta-xsts-cli:6.28.1"
        }
    }
}

class ThetaExecutionResult(
    val exitCode: Int,
)

class ThetaExecutionSpecification(
    val workingDirectory: File,
    val command: List<String>,
    val logFile: File? = null,
    val errorFile: File? = null,
)

interface ThetaXstsExecutor {
    fun isAvailable(): Boolean

    suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult

    companion object {
        val logger by loggerFactory()

        fun of(spec: ThetaExecutorSpec = ThetaExecutorSpec.Auto): ThetaXstsExecutor {
            return when (spec) {
                ThetaExecutorSpec.Auto -> autoDetect()
                ThetaExecutorSpec.Shell -> ShellBasedThetaXstsExecutor().also {
                    check(it.isAvailable()) { "Shell-based Theta executor: theta-xsts-cli is not on PATH." }
                }
                is ThetaExecutorSpec.Docker -> DockerBasedThetaXstsExecutor(spec.image).also {
                    check(it.isAvailable()) { "Docker-based Theta executor (image ${spec.image}): the Docker CLI is not on PATH." }
                }
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
    }
}
