/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.execution

import hu.bme.mit.semantifyr.backend.execution.ShellBasedBackendExecutor
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutionResult
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutionSpecification
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutor

class ShellBasedNuxmvExecutor :
    ShellBasedBackendExecutor(),
    NuxmvExecutor {
    override val binaryName = "nuXmv"

    override fun isAvailable(): Boolean {
        return probeAvailability(probeArgs = listOf("-h"), expectedExitCode = 2)
    }

    override suspend fun execute(nuxmvExecutionSpecification: NuxmvExecutionSpecification): NuxmvExecutionResult {
        val exitCode = runProcess(
            args = listOf("-source", nuxmvExecutionSpecification.commandFile.absolutePath),
            workingDirectory = nuxmvExecutionSpecification.workingDirectory,
            logFile = nuxmvExecutionSpecification.logFile,
            errorFile = nuxmvExecutionSpecification.errorFile,
        )
        return NuxmvExecutionResult(exitCode)
    }
}
