/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv

import hu.bme.mit.semantifyr.backends.nuxmv.execution.ShellBasedNuxmvExecutor
import java.io.File

sealed interface NuxmvExecutorSpec {
    object Auto : NuxmvExecutorSpec

    object Shell : NuxmvExecutorSpec
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

interface NuxmvExecutor {
    fun isAvailable(): Boolean

    suspend fun execute(nuxmvExecutionSpecification: NuxmvExecutionSpecification): NuxmvExecutionResult

    companion object {
        fun of(spec: NuxmvExecutorSpec = NuxmvExecutorSpec.Auto): NuxmvExecutor {
            return when (spec) {
                NuxmvExecutorSpec.Auto, NuxmvExecutorSpec.Shell -> ShellBasedNuxmvExecutor().also {
                    check(it.isAvailable()) { "Shell-based nuXmv executor: nuXmv is not on PATH." }
                }
            }
        }
    }
}
