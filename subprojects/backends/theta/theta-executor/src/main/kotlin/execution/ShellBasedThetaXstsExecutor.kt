/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.execution

import hu.bme.mit.semantifyr.backend.execution.BaseShellExecutor
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionResult
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionSpecification
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutor
import hu.bme.mit.semantifyr.logging.loggerFactory

class ShellBasedThetaXstsExecutor :
    BaseShellExecutor(),
    ThetaXstsExecutor {
    override val logger by loggerFactory()
    override val binaryName: String = "theta-xsts-cli"

    override fun isAvailable(): Boolean = probeAvailability(probeArgs = listOf("--version"))

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult {
        val exitCode = runProcess(
            args = thetaExecutionSpecification.command,
            workingDirectory = thetaExecutionSpecification.workingDirectory,
            logFile = thetaExecutionSpecification.logFile,
            errorFile = thetaExecutionSpecification.errorFile,
            header = "Running theta with command: ${thetaExecutionSpecification.command.joinToString(" ")}",
        )
        return ThetaExecutionResult(exitCode)
    }
}
