/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.execution

import hu.bme.mit.semantifyr.backend.execution.BaseShellExecutor
import hu.bme.mit.semantifyr.backends.spin.SpinExecutionResult
import hu.bme.mit.semantifyr.backends.spin.SpinExecutionSpecification
import hu.bme.mit.semantifyr.backends.spin.SpinExecutor
import hu.bme.mit.semantifyr.backends.spin.SpinReplaySpecification
import hu.bme.mit.semantifyr.logging.loggerFactory

class ShellBasedSpinExecutor :
    BaseShellExecutor(),
    SpinExecutor {
    override val logger by loggerFactory()
    override val binaryName: String = "spin"

    override suspend fun execute(specification: SpinExecutionSpecification): SpinExecutionResult {
        val args = listOf("-search") + specification.extraArguments + listOf(specification.modelFileName)
        val exitCode = runProcess(
            args = args,
            workingDirectory = specification.workingDirectory,
            logFile = specification.logFile,
            errorFile = specification.errorFile,
            header = "Running spin on ${specification.modelFileName}",
        )
        return SpinExecutionResult(exitCode)
    }

    override suspend fun replayTrail(specification: SpinReplaySpecification): SpinExecutionResult {
        val args = listOf("-t", "-p", "-g", specification.modelFileName)
        val exitCode = runProcess(
            args = args,
            workingDirectory = specification.workingDirectory,
            logFile = specification.logFile,
            errorFile = specification.errorFile,
            header = "Replaying spin trail for ${specification.modelFileName}",
        )
        return SpinExecutionResult(exitCode)
    }
}
