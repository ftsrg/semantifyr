/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.wrapper.execution

import hu.bme.mit.semantifyr.backends.theta.wrapper.utils.destroyTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class ShellBasedThetaXstsExecutor : ThetaXstsExecutor() {

    override fun check(): Boolean {
        val process = ProcessBuilder("theta-xsts-cli", "--version").start()
        val exitCode = process.waitFor()

        return exitCode == 0
    }

    fun CoroutineScope.startReaderJob(inputStream: InputStream, outputStream: OutputStream) = launch(Dispatchers.IO) {
        val writer = outputStream.bufferedWriter()
        try {
            runInterruptible {
                inputStream.bufferedReader().lineSequence().forEach {
                    writer.appendLine(it)
                }
            }
        } catch (_: Exception) {
            // swallow IO and cancellation exceptions
        } finally {
            writer.flush()
        }
    }

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification) = coroutineScope {
        val process = createProcessBuilder(thetaExecutionSpecification).start()

        val stdoutJob = startReaderJob(process.inputStream, thetaExecutionSpecification.logStream)
        val stderrJob = startReaderJob(process.errorStream, thetaExecutionSpecification.errorStream)

        try {
            withTimeout(thetaExecutionSpecification) {
                runInterruptible(Dispatchers.IO) {
                    process.waitFor()
                }
            }
        } finally {
            withContext(NonCancellable) {
                process.destroyTree()
                process.waitFor()
                stdoutJob.cancelAndJoin()
                stderrJob.cancelAndJoin()
            }
        }

        ThetaExecutionResult(process.exitValue())
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
