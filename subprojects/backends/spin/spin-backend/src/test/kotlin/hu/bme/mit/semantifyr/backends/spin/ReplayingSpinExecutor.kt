/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin

import hu.bme.mit.semantifyr.backend.CapturedToolOutput

class ReplayingSpinExecutor(
    case: String,
) : SpinExecutor {
    private val captured = CapturedToolOutput("spin", case)
    private val shell by lazy {
        SpinExecutor.shell()
    }

    override fun isAvailable(): Boolean {
        return true
    }

    override suspend fun execute(specification: SpinExecutionSpecification): SpinExecutionResult {
        val trail = specification.workingDirectory.resolve("${specification.modelFileName}.trail")
        val outputs = listOfNotNull(specification.logFile, trail)
        val exitCode = captured.replay("execute", specification.workingDirectory, outputs) {
            shell.execute(specification).exitCode
        }
        return SpinExecutionResult(exitCode)
    }

    override suspend fun replayTrail(specification: SpinReplaySpecification): SpinExecutionResult {
        val exitCode = captured.replay("replay", specification.workingDirectory, listOf(specification.logFile)) {
            shell.replayTrail(specification).exitCode
        }
        return SpinExecutionResult(exitCode)
    }
}
