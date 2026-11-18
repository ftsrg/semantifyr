/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.benchmark.MeasuredProcesses

class RunexecThetaXstsExecutor(private val processes: MeasuredProcesses) : ThetaXstsExecutor {

    override fun isAvailable(): Boolean {
        return true
    }

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult {
        val workingDirectory = thetaExecutionSpecification.workingDirectory
        val outputFile = thetaExecutionSpecification.logFile ?: workingDirectory.resolve("theta.out")
        val errorFile = thetaExecutionSpecification.errorFile ?: workingDirectory.resolve("theta.err")
        val observation = processes.run(
            command = listOf("theta-xsts-cli") + thetaExecutionSpecification.command,
            workingDirectory = workingDirectory.toPath(),
            outputFile = outputFile.toPath(),
            errorFile = errorFile.toPath(),
        )
        val signal = observation.exitSignal
        if (signal != null) {
            return ThetaExecutionResult(128 + signal)
        }
        return ThetaExecutionResult(checkNotNull(observation.exitCode))
    }
}
