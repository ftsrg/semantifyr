/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv

import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.backends.nuxmv.execution.ShellBasedNuxmvExecutor
import java.io.File

@JvmField
val NuxmvExecutorKey = ExecutorKey(
    name = "nuxmv",
    unavailableHints = listOf(
        "Download nuXmv from https://nuxmv.fbk.eu/download.html and add its bin folder to PATH.",
    ),
) {
    NuxmvExecutor.autoDetect()
}

class NuxmvExecutionResult(
    val exitCode: Int,
)

class NuxmvExecutionSpecification(
    val workingDirectory: File,
    val commandFile: File,
    val logFile: File? = null,
    val errorFile: File? = null,
)

interface NuxmvExecutor : BackendExecutor {
    suspend fun execute(nuxmvExecutionSpecification: NuxmvExecutionSpecification): NuxmvExecutionResult

    companion object {
        fun shell(): NuxmvExecutor {
            return ShellBasedNuxmvExecutor()
        }

        fun autoDetect(): NuxmvExecutor {
            return shell()
        }
    }
}
