/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.execution

import hu.bme.mit.semantifyr.backend.execution.ShellBasedBackendExecutor
import hu.bme.mit.semantifyr.backends.spin.SpinExecutionResult
import hu.bme.mit.semantifyr.backends.spin.SpinExecutionSpecification
import hu.bme.mit.semantifyr.backends.spin.SpinExecutor
import hu.bme.mit.semantifyr.backends.spin.SpinReplaySpecification

class ShellBasedSpinExecutor :
    ShellBasedBackendExecutor(),
    SpinExecutor {
    override val binaryName = "spin"

    override suspend fun execute(specification: SpinExecutionSpecification): SpinExecutionResult {
        val args = listOf("-search") + specification.extraArguments + listOf(specification.modelFileName)
        val exitCode = runProcess(
            args = args,
            workingDirectory = specification.workingDirectory,
            logFile = specification.logFile,
            errorFile = specification.errorFile,
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
        )
        return SpinExecutionResult(exitCode)
    }
}
