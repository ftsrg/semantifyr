/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.backends.theta.execution.DockerBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.backends.theta.execution.ShellBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import java.io.File

const val THETA_DEFAULT_IMAGE = "ftsrg/theta-xsts-cli:6.28.1"

@JvmField
val ThetaExecutorKey = ExecutorKey(
    name = "theta",
    unavailableHints = listOf(
        "Install theta-xsts-cli and ensure it is on PATH (preferred).",
        "Alternatively, install Docker so the bundled image $THETA_DEFAULT_IMAGE can be pulled.",
    ),
) {
    ThetaXstsExecutor.autoDetect()
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

interface ThetaXstsExecutor : BackendExecutor {
    suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult

    companion object {
        private val logger by loggerFactory()

        fun shell(): ThetaXstsExecutor {
            return ShellBasedThetaXstsExecutor()
        }

        fun docker(image: String = THETA_DEFAULT_IMAGE): ThetaXstsExecutor {
            return DockerBasedThetaXstsExecutor(image)
        }

        fun autoDetect(image: String = THETA_DEFAULT_IMAGE): ThetaXstsExecutor {
            logger.debug { "Auto-detecting Theta executor" }
            val shell = shell()
            if (shell.isAvailable()) {
                logger.debug { "Theta auto-detect: shell available" }
                return shell
            }
            logger.debug { "Theta auto-detect: shell unavailable, falling back to Docker image $image" }
            val docker = docker(image)
            check(docker.isAvailable()) {
                "Could not find any working Theta XSTS executor (tried shell and docker $image)."
            }
            return docker
        }
    }
}
