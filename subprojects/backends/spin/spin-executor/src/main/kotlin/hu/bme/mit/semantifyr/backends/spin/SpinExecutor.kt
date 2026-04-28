/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin

import hu.bme.mit.semantifyr.backends.spin.execution.ShellBasedSpinExecutor
import java.io.File

sealed interface SpinExecutorSpec {
    object Auto : SpinExecutorSpec

    object Shell : SpinExecutorSpec
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

interface SpinExecutor {
    fun isAvailable(): Boolean

    suspend fun execute(specification: SpinExecutionSpecification): SpinExecutionResult

    suspend fun replayTrail(specification: SpinReplaySpecification): SpinExecutionResult

    companion object {
        fun of(spec: SpinExecutorSpec = SpinExecutorSpec.Auto): SpinExecutor {
            return when (spec) {
                SpinExecutorSpec.Auto, SpinExecutorSpec.Shell -> ShellBasedSpinExecutor().also {
                    check(it.isAvailable()) { "Shell-based Spin executor: spin is not on PATH." }
                }
            }
        }
    }
}
