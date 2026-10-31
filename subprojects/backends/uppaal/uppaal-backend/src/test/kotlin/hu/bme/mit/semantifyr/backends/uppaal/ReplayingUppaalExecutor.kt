/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal

import hu.bme.mit.semantifyr.backend.CapturedToolOutput

class ReplayingUppaalExecutor(
    case: String,
) : UppaalExecutor {
    private val captured = CapturedToolOutput("uppaal", case)
    private val shell by lazy {
        UppaalExecutor.shell()
    }

    override fun isAvailable(): Boolean {
        return true
    }

    override suspend fun execute(uppaalExecutionSpecification: UppaalExecutionSpecification): UppaalExecutionResult {
        val outputs = listOfNotNull(uppaalExecutionSpecification.logFile, uppaalExecutionSpecification.errorFile)
        val exitCode = captured.replay("execute", uppaalExecutionSpecification.workingDirectory, outputs) {
            shell.execute(uppaalExecutionSpecification).exitCode
        }
        return UppaalExecutionResult(exitCode)
    }
}
