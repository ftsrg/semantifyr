/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.portfolio

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendConfig
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.verifier.ProgressContext
import kotlin.time.Clock

class SingleBackendPortfolio<T : BackendConfig>(
    val backend: VerificationBackend<T>,
    val configuration: T,
) : VerificationPortfolio() {

    override val id: String = "${backend.id}-${configuration.id}"
    override val displayName: String = "${backend.id} ${configuration.id}"
    override val description: String = "Runs the ${configuration.id} configuration of ${backend.id} alone."
    override val executorKeys: Set<ExecutorKey<*>> = setOf(backend.executorKey)
    override val legs = listOf(PortfolioLeg("${backend.id}:${configuration.id}", configuration.soundness))

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return environment.availability(backend.executorKey)
    }

    override suspend fun verify(
        parentInjector: Injector,
        request: BackendVerificationRequest,
        gate: ConcurrencyGate,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult {
        val startedAt = Clock.System.now()
        val backendResult = gate.withPermit {
            backend.verify(parentInjector, configuration, request, environment)
        }
        return PortfolioOutcome.Decided(withholdUntrusted(backendResult, request)).toBackendVerificationResult(startedAt)
    }

    // an untrusted Failed may carry a spurious counterexample, so the witness goes with the verdict
    private fun withholdUntrusted(result: BackendVerificationResult, request: BackendVerificationRequest): BackendVerificationResult {
        if (!result.isDecisive || result.isTrusted) {
            return result
        }
        val property = if (request.inlinedOxsts.property.expression is EF) {
            "EF"
        } else {
            "AG"
        }
        val direction = when (result.soundness) {
            SoundnessDirection.Exact -> "exact"
            SoundnessDirection.OverApproximating -> "over-approximating"
            SoundnessDirection.UnderApproximating -> "under-approximating"
        }
        return result.copy(
            verdict = VerificationVerdict.Inconclusive,
            isTrusted = false,
            inlinedWitness = null,
            message = "${backend.id}'s ${result.verdict} on an $property property is not trusted: the leg is $direction",
        )
    }
}
