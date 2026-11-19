/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.theta.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioOutcome
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioTask
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlin.time.Clock

class ThetaFullPortfolio(
    private val theta: VerificationBackend<ThetaConfig> = ThetaBackend(),
) : VerificationPortfolio() {
    override val id: String = "theta"
    override val displayName: String = "Theta"
    override val description: String = "Runs Theta's CEGAR and k-induction configurations side by side and takes the first answer."

    private val tasks: List<PortfolioTask<ThetaConfig>> = listOf(
        PortfolioTask(theta, ThetaConfig.CegarExpl, "cegarexpl"),
        PortfolioTask(theta, ThetaConfig.CegarExplPredCombined, "cegarexplpred"),
        PortfolioTask(theta, ThetaConfig.CegarPredCart, "cegarexplcart"),
        PortfolioTask(theta, ThetaConfig.BoundedKInduction, "kinduction"),
    )

    override val legs = tasks.map {
        it.leg
    }

    override suspend fun verify(
        parentInjector: Injector,
        request: BackendVerificationRequest,
        gate: ConcurrencyGate,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
        readBack: suspend (BackendVerificationResult) -> BackendVerificationResult,
    ): BackendVerificationResult {
        val startedAt = Clock.System.now()
        val availability = availability(environment)
        if (availability is AvailabilityReport.Unavailable) {
            logger.info { "$id: no available Theta executor on this host" }
            return PortfolioOutcome.NoDecision(availability.reason, emptyList()).toBackendVerificationResult(startedAt)
        }
        progress.reportProgress("racing ${tasks.size} Theta configurations")
        val outcome = firstDecisive(startCase(parentInjector, request, gate, environment, progress, readBack)) {
            legs(tasks)
        }
        return outcome.toBackendVerificationResult(startedAt)
    }
}
