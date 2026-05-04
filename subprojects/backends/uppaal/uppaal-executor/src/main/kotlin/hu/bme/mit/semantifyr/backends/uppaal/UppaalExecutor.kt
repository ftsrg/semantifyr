/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal

import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.backends.uppaal.execution.ShellBasedUppaalExecutor
import java.io.File

@JvmField
val UppaalExecutorKey = ExecutorKey<UppaalExecutor>(
    name = "uppaal",
    unavailableHints = listOf(
        "Install Uppaal (https://uppaal.org) and ensure the 'verifyta' binary is on PATH.",
        "On a typical Uppaal install the binary is under <uppaal>/bin-Linux/verifyta.",
    ),
) {
    UppaalExecutor.autoDetect()
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

interface UppaalExecutor : BackendExecutor {
    suspend fun execute(uppaalExecutionSpecification: UppaalExecutionSpecification): UppaalExecutionResult

    companion object {
        fun shell(): UppaalExecutor {
            return ShellBasedUppaalExecutor()
        }

        fun autoDetect(): UppaalExecutor {
            return shell()
        }
    }
}
