/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin

import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.backends.spin.execution.ShellBasedSpinExecutor
import java.io.File

@JvmField
val SpinExecutorKey = ExecutorKey(
    name = "spin",
    unavailableHints = listOf(
        "Install Spin from https://spinroot.com/spin/Src/ and make sure both 'spin' and a C compiler (gcc) are on PATH.",
    ),
) {
    SpinExecutor.autoDetect()
}

class SpinExecutionResult(
    val exitCode: Int,
)

class SpinExecutionSpecification(
    val workingDirectory: File,
    val modelFileName: String,
    val extraArguments: List<String> = emptyList(),
    val logFile: File? = null,
    val errorFile: File? = null,
)

class SpinReplaySpecification(
    val workingDirectory: File,
    val modelFileName: String,
    val logFile: File,
    val errorFile: File? = null,
)

interface SpinExecutor : BackendExecutor {
    suspend fun execute(specification: SpinExecutionSpecification): SpinExecutionResult

    suspend fun replayTrail(specification: SpinReplaySpecification): SpinExecutionResult

    companion object {
        fun shell(): SpinExecutor {
            return ShellBasedSpinExecutor()
        }

        fun autoDetect(): SpinExecutor {
            return shell()
        }
    }
}
