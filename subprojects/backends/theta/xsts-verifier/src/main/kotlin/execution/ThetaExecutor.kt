/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.execution

import java.io.File
import java.util.concurrent.TimeUnit

class ThetaRuntimeDetails(
    val workingDirectory: String,
    val name: String,
    val id: Int,
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
    val runtimeDetails: ThetaRuntimeDetails
) {
    val hasWitness = File(runtimeDetails.workingDirectory, runtimeDetails.cexPath).exists()
}
class ThetaUnknownVerificationResult(runtimeDetails: ThetaRuntimeDetails) : ThetaVerificationResult(runtimeDetails)
class ThetaSafeVerificationResult(runtimeDetails: ThetaRuntimeDetails) : ThetaVerificationResult(runtimeDetails)
class ThetaUnsafeVerificationResult(runtimeDetails: ThetaRuntimeDetails) : ThetaVerificationResult(runtimeDetails)
class ThetaErrorVerificationResult(runtimeDetails: ThetaRuntimeDetails) : ThetaVerificationResult(runtimeDetails) {
    val failureMessage = "Theta execution failed, see: ${runtimeDetails.workingDirectory}/${runtimeDetails.errPath}"
}

fun ThetaRuntimeDetails.toVerificationResult(): ThetaVerificationResult {
    val errorFile = File(workingDirectory, errPath)
    if (errorFile.exists() && errorFile.useLines { it.any { it.isNotEmpty() } }) {
        return ThetaErrorVerificationResult(this)
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

interface ThetaExecutor {

    fun initialize() {

    }

    suspend fun execute(
        thetaRuntimeDetails: ThetaRuntimeDetails,
        parameter: String,
        timeout: Long = 3,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): ThetaVerificationResult

}
