/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.wrapper.execution

import kotlinx.coroutines.withTimeout
import org.apache.commons.io.output.NullOutputStream
import java.io.File
import java.io.OutputStream
import kotlin.time.Duration

class ThetaExecutionResult(
    val exitCode: Int,
)

class ThetaExecutionSpecification(
    val workingDirectory: File,
    val command: List<String>,
    val logStream: OutputStream = NullOutputStream.INSTANCE,
    val errorStream: OutputStream = NullOutputStream.INSTANCE,
    val timeout: Duration,
)

abstract class ThetaXstsExecutor {

    abstract fun check(): Boolean

    open fun initialize() {

    }

    abstract suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult

    protected suspend inline fun <T> withTimeout(thetaExecutionSpecification: ThetaExecutionSpecification, crossinline block: suspend () -> T): T {
        return withTimeout(thetaExecutionSpecification.timeout) {
            block()
        }
    }

}
