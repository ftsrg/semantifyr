/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.execution

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.theta.verification.utils.ensureExistsOutputStream
import hu.bme.mit.semantifyr.backends.theta.wrapper.execution.ThetaExecutionResult
import hu.bme.mit.semantifyr.backends.theta.wrapper.execution.ThetaExecutionSpecification
import hu.bme.mit.semantifyr.backends.theta.wrapper.execution.ThetaXstsExecutorProvider
import hu.bme.mit.semantifyr.semantics.verification.VerificationDispatcher
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.time.Duration

@Serializable
class ThetaVerificationSpecification(
    val workingDirectory: String,
    val name: String,
    val id: Int,
    val parameter: String,
    val timeout: Duration,
) {
    val modelFileName = "$name.xsts"
    val cexFileName = "out.cex"
    val logFileName = "theta.out"
    val errFileName = "theta.err"

    val modelPath = modelFileName
    val artifactsPath = "verification${File.separator}artifacts$id"
    val cexPath = "$artifactsPath${File.separator}$cexFileName"
    val logPath = "$artifactsPath${File.separator}$logFileName"
    val errPath = "$artifactsPath${File.separator}$errFileName"
}

sealed class ThetaVerificationResult(
    val runtimeDetails: ThetaVerificationSpecification
) {
    val hasWitness = File(runtimeDetails.workingDirectory, runtimeDetails.cexPath).exists()
}

class ThetaUnknownVerificationResult(runtimeDetails: ThetaVerificationSpecification) : ThetaVerificationResult(runtimeDetails)
class ThetaSafeVerificationResult(runtimeDetails: ThetaVerificationSpecification) : ThetaVerificationResult(runtimeDetails)
class ThetaUnsafeVerificationResult(runtimeDetails: ThetaVerificationSpecification) : ThetaVerificationResult(runtimeDetails)

fun ThetaVerificationSpecification.toVerificationResult(executionResult: ThetaExecutionResult): ThetaVerificationResult {
    if (executionResult.exitCode != 0) {
        error("Theta execution failed: $this")
    }

    val logFile = File(workingDirectory, logPath)
    if (logFile.exists()) {
        logFile.useLines { lines ->
            for (line in lines) {
                if (line.contains("SafetyResult Unsafe")) {
                    return ThetaUnsafeVerificationResult(this)
                }
                if (line.contains("SafetyResult Safe")) {
                    return ThetaSafeVerificationResult(this)
                }
            }
        }
    }

    return ThetaUnknownVerificationResult(this)
}

class ThetaVerificationExecutor {

    @Inject
    private lateinit var thetaXstsExecutorProvider: ThetaXstsExecutorProvider

    @Inject
    private lateinit var verificationDispatcher: VerificationDispatcher

    private fun ThetaVerificationSpecification.toExecutionSpecification(): ThetaExecutionSpecification {
        val workingDirectory = File(workingDirectory)
        val command = listOf(
            *parameter.split(" ").toTypedArray(),
            "--model", modelPath,
            "--cexfile", cexPath,
        )

        val logStream = workingDirectory.resolve(logPath).ensureExistsOutputStream()
        val errorStream = workingDirectory.resolve(errPath).ensureExistsOutputStream()

        return ThetaExecutionSpecification(
            workingDirectory,
            command,
            logStream,
            errorStream,
            timeout,
        )
    }

    suspend fun execute(thetaVerificationSpecification: ThetaVerificationSpecification): ThetaVerificationResult {
        val thetaExecutor = thetaXstsExecutorProvider.getExecutor()
        val thetaExecutionSpecification = thetaVerificationSpecification.toExecutionSpecification()

        val writer = thetaExecutionSpecification.logStream.bufferedWriter()
        writer.appendLine("Executing with commands:")
        writer.appendLine(thetaExecutionSpecification.command.joinToString(" "))
        writer.appendLine()
        writer.flush()

        val result = verificationDispatcher.execute {
            thetaExecutor.execute(thetaExecutionSpecification)
        }

        return thetaVerificationSpecification.toVerificationResult(result)
    }

}
