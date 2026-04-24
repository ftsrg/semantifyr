/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationMetrics
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.verification.ThetaBackend
import hu.bme.mit.semantifyr.verification.ThetaConfig
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.portfolio.BackendExecutor
import hu.bme.mit.semantifyr.verification.portfolio.PortfolioScope
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verification.portfolio.withSubPath
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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
) : VerificationPortfolio() {

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
        progress: ProgressContext,
    ): VerificationResult {
        return firstDecisive(executor, stageTimeout) {
            runTheta(ThetaConfig.CegarExpl, request.withSubPath("cegarexpl"), environment)
            runTheta(ThetaConfig.CegarExplPredCombined, request.withSubPath("cegarexplpred"), environment)
            runTheta(ThetaConfig.CegarPredCart, request.withSubPath("cegarexplcart"), environment)
            runTheta(ThetaConfig.BoundedKInduction, request.withSubPath("kinduction"), environment)
        } ?: VerificationResult.inconclusive(
            metadata = VerificationRunMetadata(
                backendId = "",
                startedAt = Clock.System.now(),
                caseQualifiedName = ""
            ),
            metrics = VerificationMetrics(

            ),
            message = "$id: no decisive result within $stageTimeout"
        )
    }

}

class ThetaSinglePortfolio(val config: ThetaConfig) : VerificationPortfolio() {
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
        progress: ProgressContext,
    ): VerificationResult {
        return executor.runTheta(config, request, environment)
    }

}
