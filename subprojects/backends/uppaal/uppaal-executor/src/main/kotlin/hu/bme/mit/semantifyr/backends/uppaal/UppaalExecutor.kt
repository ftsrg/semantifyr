/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal

import hu.bme.mit.semantifyr.backends.uppaal.execution.ShellBasedUppaalExecutor
import java.io.File

sealed interface UppaalExecutorSpec {
    object Auto : UppaalExecutorSpec

    object Shell : UppaalExecutorSpec
}

class UppaalExecutionResult(
    val exitCode: Int,
)

class UppaalExecutionSpecification(
    val workingDirectory: File,
    val command: List<String>,
    val logFile: File? = null,
    val errorFile: File? = null,
)

interface UppaalExecutor {
    fun isAvailable(): Boolean

    suspend fun execute(uppaalExecutionSpecification: UppaalExecutionSpecification): UppaalExecutionResult

    companion object {
        fun of(spec: UppaalExecutorSpec = UppaalExecutorSpec.Auto): UppaalExecutor {
            return when (spec) {
                UppaalExecutorSpec.Auto, UppaalExecutorSpec.Shell -> ShellBasedUppaalExecutor().also {
                    check(it.isAvailable()) { "Shell-based Uppaal executor: verifyta is not on PATH." }
                }
            }
        }
    }
}
