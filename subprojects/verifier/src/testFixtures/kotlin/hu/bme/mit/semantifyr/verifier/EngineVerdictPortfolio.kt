/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendConfig
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioLeg
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio

// an engine suite compares the engine's own verdict with the case's, also where its soundness direction does not trust it
class EngineVerdictPortfolio<T : BackendConfig>(
    private val backend: VerificationBackend<T>,
    private val configuration: T,
) : VerificationPortfolio() {

    override val id = "${backend.id}-${configuration.id}"
    override val displayName = "${backend.id} ${configuration.id}"
    override val description = "The verdict of ${backend.id} ${configuration.id}, trusted or not"
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
        return gate.withPermit {
            backend.verify(parentInjector, configuration, request, environment)
        }
    }
}
