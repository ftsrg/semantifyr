/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backends.theta.verification.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.verification.ThetaConfig
import hu.bme.mit.semantifyr.semantics.verification.AvailabilityReport
import hu.bme.mit.semantifyr.semantics.verification.BackendExecutor
import hu.bme.mit.semantifyr.semantics.verification.ExecutionEnvironment
import hu.bme.mit.semantifyr.semantics.verification.VerificationRequest
import hu.bme.mit.semantifyr.semantics.verification.VerificationResult
import hu.bme.mit.semantifyr.semantics.verification.portfolio.Portfolio
import hu.bme.mit.semantifyr.semantics.verification.portfolio.PortfolioScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal const val THETA_FAMILY_ID = "theta"

fun PortfolioScope.runTheta(
    config: ThetaConfig,
    request: VerificationRequest,
    environment: ExecutionEnvironment,
) {
    async {
        executor.withPermit {
            ThetaBackend.verify(config, request, environment)
        }
    }
}

suspend fun BackendExecutor.runTheta(
    config: ThetaConfig,
    request: VerificationRequest,
    environment: ExecutionEnvironment,
): VerificationResult {
    return withPermit {
        ThetaBackend.verify(config, request, environment)
    }
}

class ThetaFullPortfolio(
    private val stageTimeout: Duration = 10.minutes,
) : Portfolio() {

    override val id: String = "theta-full"
    override val displayName: String = "Theta full portfolio"
    override val description: String = "All Theta CEGAR/BOUNDED configurations in parallel. First decisive wins."
    override val familyId: String = THETA_FAMILY_ID

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return ThetaBackend.probeAvailability(ThetaConfig.CegarExpl, environment)
        // should probe all and return if at least one can run -> all ord together
    }

    override suspend fun verify(
        request: VerificationRequest,
        executor: BackendExecutor,
        environment: ExecutionEnvironment,
    ): VerificationResult {
        return firstDecisive(executor, stageTimeout) {
            runTheta(ThetaConfig.CegarExpl, request, environment)
            runTheta(ThetaConfig.CegarExplPredCombined, request, environment)
            runTheta(ThetaConfig.CegarPredCart, request, environment)
            runTheta(ThetaConfig.BoundedKInduction, request, environment)
        } ?: VerificationResult.inconclusive("$id: no decisive result within $stageTimeout")
    }
}

class ThetaSinglePortfolio(val config: ThetaConfig) : Portfolio() {
    override val id: String = "theta-${config.id}"
    override val displayName: String = "Theta ${config.id}"
    override val description: String = "Single Theta configuration: ${config.parameters}"
    override val familyId: String = THETA_FAMILY_ID

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return ThetaBackend.probeAvailability(config, environment)
    }

    override suspend fun verify(
        request: VerificationRequest,
        executor: BackendExecutor,
        environment: ExecutionEnvironment,
    ): VerificationResult {
        return executor.runTheta(config, request, environment)
    }
}
