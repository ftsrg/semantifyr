/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backends.theta.verification.ThetaBackendProvider
import hu.bme.mit.semantifyr.backends.theta.verification.ThetaConfig
import hu.bme.mit.semantifyr.backends.theta.verification.theta
import hu.bme.mit.semantifyr.backends.theta.wrapper.execution.ThetaExecutorSpec
import hu.bme.mit.semantifyr.semantics.verification.AvailabilityReport
import hu.bme.mit.semantifyr.semantics.verification.BackendExecutor
import hu.bme.mit.semantifyr.semantics.verification.ExecutionEnvironment
import hu.bme.mit.semantifyr.semantics.verification.VerificationRequest
import hu.bme.mit.semantifyr.semantics.verification.VerificationResult
import hu.bme.mit.semantifyr.semantics.verification.portfolio.Portfolio
import kotlin.time.Duration

internal const val THETA_FAMILY_ID = "theta"

class ThetaFullPortfolio(
    private val internalConcurrency: Int = Int.MAX_VALUE,
    private val stageTimeout: Duration = Duration.INFINITE,
) : Portfolio() {

    override val id: String = "theta-full"
    override val displayName: String = "Theta full portfolio"
    override val description: String = "All Theta CEGAR/BOUNDED configurations in parallel. First decisive wins."
    override val familyId: String = THETA_FAMILY_ID

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return ThetaBackendProvider.probeExecutor(environment.theta ?: ThetaExecutorSpec.Auto)
    }

    override suspend fun verify(
        request: VerificationRequest,
        executor: BackendExecutor,
        environment: ExecutionEnvironment,
    ): VerificationResult {
        return firstDecisive(request, executor, environment, maxConcurrency = internalConcurrency, timeout = stageTimeout) {
            theta(ThetaConfig.Companion.CegarExpl)
            theta(ThetaConfig.Companion.CegarExplPredCombined)
            theta(ThetaConfig.Companion.CegarPredCart)
            theta(ThetaConfig.Companion.BoundedKInduction)
        }
    }
}

class ThetaSinglePortfolio(val config: ThetaConfig) : Portfolio() {
    override val id: String = "theta-${config.id}"
    override val displayName: String = "Theta ${config.id}"
    override val description: String = "Single Theta configuration: ${config.parameters}"
    override val familyId: String = THETA_FAMILY_ID

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return ThetaBackendProvider.probeAvailability(config, environment)
    }

    override suspend fun verify(
        request: VerificationRequest,
        executor: BackendExecutor,
        environment: ExecutionEnvironment,
    ): VerificationResult {
        return firstDecisive(request, executor, environment) {
            theta(config)
        }
    }
}
