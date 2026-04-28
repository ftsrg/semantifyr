/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.execution

import hu.bme.mit.semantifyr.backend.execution.BaseShellExecutor
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutionResult
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutionSpecification
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutor
import hu.bme.mit.semantifyr.logging.loggerFactory

class ShellBasedUppaalExecutor :
    BaseShellExecutor(),
    UppaalExecutor {
    override val logger by loggerFactory()
    override val binaryName: String = "verifyta"

    override fun isAvailable(): Boolean = probeAvailability(listOf("-v"))

    override suspend fun execute(uppaalExecutionSpecification: UppaalExecutionSpecification): UppaalExecutionResult {
        val exitCode = runProcess(
            args = uppaalExecutionSpecification.command,
            workingDirectory = uppaalExecutionSpecification.workingDirectory,
            logFile = uppaalExecutionSpecification.logFile,
            errorFile = uppaalExecutionSpecification.errorFile,
            header = "Running verifyta with command: ${uppaalExecutionSpecification.command.joinToString(" ")}",
        )
        return UppaalExecutionResult(exitCode)
    }
}
