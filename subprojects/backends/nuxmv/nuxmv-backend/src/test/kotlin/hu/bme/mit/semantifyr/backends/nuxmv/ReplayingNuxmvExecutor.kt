/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv

import hu.bme.mit.semantifyr.backend.CapturedToolOutput

class ReplayingNuxmvExecutor(
    case: String,
) : NuxmvExecutor {
    private val captured = CapturedToolOutput("nuxmv", case)
    private val shell by lazy {
        NuxmvExecutor.shell()
    }

    override fun isAvailable(): Boolean {
        return true
    }

    override suspend fun execute(nuxmvExecutionSpecification: NuxmvExecutionSpecification): NuxmvExecutionResult {
        val outputs = listOfNotNull(nuxmvExecutionSpecification.logFile)
        val exitCode = captured.replay("execute", nuxmvExecutionSpecification.workingDirectory, outputs) {
            shell.execute(nuxmvExecutionSpecification).exitCode
        }
        return NuxmvExecutionResult(exitCode)
    }
}
