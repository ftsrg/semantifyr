/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.wrapper.execution

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class ShellBasedThetaXstsExecutor : ThetaXstsExecutor() {

    override fun check(): Boolean {
        val process = ProcessBuilder("theta-xsts-cli", "--version").start()
        val exitCode = process.waitFor()

        return exitCode == 0
    }

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult {
        val processBuilder = createProcessBuilder(thetaExecutionSpecification)
        val process = processBuilder.start()

        try {
            withTimeout(thetaExecutionSpecification) {
                runInterruptible {
                    process.waitFor()
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } finally {
            withContext(NonCancellable) {
                process.inputStream.transferTo(thetaExecutionSpecification.logStream)
                process.errorStream.transferTo(thetaExecutionSpecification.errorStream)

                process.destroyForcibly()
            }
        }

        return ThetaExecutionResult(process.exitValue())
    }

    private fun createProcessBuilder(thetaExecutionSpecification: ThetaExecutionSpecification): ProcessBuilder {
        return ProcessBuilder(
                "theta-xsts-cli",
                *thetaExecutionSpecification.command.toTypedArray()
            )
            .directory(thetaExecutionSpecification.workingDirectory)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
    }

}
