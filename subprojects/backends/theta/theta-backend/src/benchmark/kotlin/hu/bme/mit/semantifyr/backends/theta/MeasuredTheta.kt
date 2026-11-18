/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.benchmark.ContextSource
import hu.bme.mit.semantifyr.benchmark.MeasuredProcesses
import hu.bme.mit.semantifyr.benchmark.commandOutputOrNull
import hu.bme.mit.semantifyr.compiler.oxstsInjector
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.verifier.internal.TrivialVerifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlin.time.Clock

@Serializable
data class ThetaResult(
    val verdict: VerificationVerdict,
    val message: String?,
)

object ThetaInstallation : ContextSource<String?>("theta-xsts-cli", String.serializer().nullable) {
    override fun record(): String? {
        return commandOutputOrNull("theta-xsts-cli", "--version")?.lines()?.last()
    }
}

class MeasuredTheta(
    private val thetaConfig: ThetaConfig = ThetaConfig.CegarExpl,
) {
    fun verify(compiled: FlattenedCompilationContext, processes: MeasuredProcesses): ThetaResult {
        val trivialVerifier = oxstsInjector.getInstance(TrivialVerifier::class.java)
        if (trivialVerifier.isTriviallyVerifiable(compiled.inlinedOxsts)) {
            val result = trivialVerifier.verifyTrivially(compiled.inlinedOxsts, Clock.System.now())
            return ThetaResult(result.verdict, result.message)
        }
        val environment = ExecutionEnvironment.builder()
            .put(ThetaExecutorKey) {
                RunexecThetaXstsExecutor(processes)
            }
            .build()
        val request = BackendVerificationRequest(
            inlinedOxsts = compiled.inlinedOxsts,
            artifactOutputPath = processes.directory,
            flatteningInfo = compiled.flatteningInfo,
        )
        val result = runBlocking {
            ThetaBackend().verify(oxstsInjector, thetaConfig, request, environment)
        }
        return ThetaResult(result.verdict, result.message)
    }
}
