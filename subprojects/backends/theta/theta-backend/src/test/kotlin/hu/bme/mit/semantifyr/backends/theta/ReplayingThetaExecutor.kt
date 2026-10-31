/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backend.CapturedToolOutput

class ReplayingThetaExecutor(
    case: String,
) : ThetaXstsExecutor {
    private val captured = CapturedToolOutput("theta", case)
    private val shell by lazy {
        ThetaXstsExecutor.shell()
    }

    override fun isAvailable(): Boolean {
        return true
    }

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult {
        val workingDirectory = thetaExecutionSpecification.workingDirectory
        val command = thetaExecutionSpecification.command
        val cexFile = workingDirectory.resolve(command[command.indexOf("--cexfile") + 1])
        val outputs = listOfNotNull(thetaExecutionSpecification.logFile, cexFile)
        val exitCode = captured.replay("execute", workingDirectory, outputs) {
            shell.execute(thetaExecutionSpecification).exitCode
        }
        return ThetaExecutionResult(exitCode)
    }
}
