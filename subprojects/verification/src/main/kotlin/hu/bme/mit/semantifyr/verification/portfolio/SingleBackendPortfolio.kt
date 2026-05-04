/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.portfolio

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendConfig
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.verification.ProgressContext
import kotlin.time.Clock

class SingleBackendPortfolio<T : BackendConfig>(
    val backend: VerificationBackend<T>,
    val config: T,
) : VerificationPortfolio() {

    override val id: String = "${backend.id}-${config.id}"
    override val displayName: String = "${backend.id} ${config.id}"
    override val description: String = "Single ${backend.id} configuration: ${config.id}"

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
            backend.verify(parentInjector, config, request, environment)
        }
        return PortfolioOutcome.Decided(backendResult).toBackendVerificationResult(startedAt)
    }
}
