/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.execution

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

class ShellBasedThetaExecutor : ThetaExecutor {

    override suspend fun execute(
        thetaRuntimeDetails: ThetaRuntimeDetails,
        parameter: String,
        timeout: Long,
        timeUnit: TimeUnit
    ): ThetaVerificationResult {
        val artifactsDir = File(thetaRuntimeDetails.workingDirectory, thetaRuntimeDetails.artifactsPath)
        artifactsDir.deleteRecursively()
        artifactsDir.mkdirs()

        val processBuilder = ProcessBuilder(
                "theta-xsts-cli",
                *parameter.split(" ").toTypedArray(),
                "--model", thetaRuntimeDetails.modelPath,
                "--cexfile", thetaRuntimeDetails.cexPath,
            )
            .directory(File(thetaRuntimeDetails.workingDirectory))
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)

        val process = processBuilder.start()

        val logFile = File(thetaRuntimeDetails.workingDirectory, thetaRuntimeDetails.logPath)
        val errFile = File(thetaRuntimeDetails.workingDirectory, thetaRuntimeDetails.errPath)

        try {
            withTimeout(timeout.toDuration(timeUnit.toDurationUnit())) {
                process.waitFor()
            }
        } catch (e: TimeoutCancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } finally {
            withContext(NonCancellable) {
                process.inputStream.transferTo(logFile.outputStream())
                process.errorStream.transferTo(errFile.outputStream())
            }
        }

        if (process.exitValue() != 0) {
            throw IllegalStateException("Theta execution failed with code ${process.exitValue()}.")
        }

        return thetaRuntimeDetails.toVerificationResult()
    }

}
