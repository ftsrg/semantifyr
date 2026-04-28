/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.execution

import hu.bme.mit.semantifyr.backend.execution.BaseShellExecutor
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutionResult
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutionSpecification
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutor
import hu.bme.mit.semantifyr.logging.loggerFactory

class ShellBasedNuxmvExecutor :
    BaseShellExecutor(),
    NuxmvExecutor {
    override val logger by loggerFactory()
    override val binaryName: String = "nuXmv"

    override fun isAvailable(): Boolean {
        return probeAvailability(probeArgs = listOf("-h"), expectedExitCode = 2)
    }

    override suspend fun execute(nuxmvExecutionSpecification: NuxmvExecutionSpecification): NuxmvExecutionResult {
        val exitCode = runProcess(
            args = listOf("-source", nuxmvExecutionSpecification.commandFile.absolutePath),
            workingDirectory = nuxmvExecutionSpecification.workingDirectory,
            logFile = nuxmvExecutionSpecification.logFile,
            errorFile = nuxmvExecutionSpecification.errorFile,
            header = "Running nuXmv with command file: ${nuxmvExecutionSpecification.commandFile.absolutePath}",
        )
        return NuxmvExecutionResult(exitCode)
    }
}
