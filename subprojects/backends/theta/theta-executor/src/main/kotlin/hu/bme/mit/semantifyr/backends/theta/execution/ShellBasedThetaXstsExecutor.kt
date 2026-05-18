/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.execution

import hu.bme.mit.semantifyr.backend.execution.ShellBasedBackendExecutor
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionResult
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionSpecification
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutor

class ShellBasedThetaXstsExecutor :
    ShellBasedBackendExecutor(),
    ThetaXstsExecutor {

    override val binaryName: String = "theta-xsts-cli"

    override fun isAvailable(): Boolean {
        return probeAvailability(probeArgs = listOf("--version"))
    }

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult {
        val exitCode = runProcess(
            args = thetaExecutionSpecification.command,
            workingDirectory = thetaExecutionSpecification.workingDirectory,
            logFile = thetaExecutionSpecification.logFile,
            errorFile = thetaExecutionSpecification.errorFile,
        )
        return ThetaExecutionResult(exitCode)
    }
}
