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

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    override fun check(): Boolean {
        return try {
            thetaProcessBuilder("--version").start().waitFor() == 0
        } catch (e: Exception) {
            false
        }
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

        // TODO: handle theta errors if any

        ThetaExecutionResult(process.exitValue())
    }

    private fun createProcessBuilder(thetaExecutionSpecification: ThetaExecutionSpecification): ProcessBuilder {
        return thetaProcessBuilder(thetaExecutionSpecification.command)
            .directory(thetaExecutionSpecification.workingDirectory)
    }

    private fun thetaProcessBuilder(vararg args: String): ProcessBuilder {
        val args = thetaCmd(args.toList())

        return ProcessBuilder(args)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
    }

    private fun thetaProcessBuilder(args: List<String>): ProcessBuilder {
        val args = thetaCmd(args)

        return ProcessBuilder(args)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
    }

    private fun thetaCmd(args: List<String>): List<String> {
        return if (isWindows) {
            listOf("cmd", "/c", "theta-xsts-cli") + args
        } else {
            listOf("theta-xsts-cli") + args
        }
    }

}
