/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
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
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackend
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackend
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.backends.theta.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioOutcome
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioTask
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlin.time.Clock

class AllAgreeFullPortfolio(
    private val theta: VerificationBackend<ThetaConfig> = ThetaBackend(),
    private val nuxmv: VerificationBackend<NuxmvConfig> = NuxmvBackend(),
    private val uppaal: VerificationBackend<UppaalConfig> = UppaalBackend(),
    private val spin: VerificationBackend<SpinConfig> = SpinBackend(),
) : VerificationPortfolio() {
    override val id: String = "all-agree"
    override val displayName: String = "All agree"
    override val description: String = "Runs every installed backend and requires their trusted answers to agree."

    private val tasks: List<PortfolioTask<*>> = listOf(
        PortfolioTask(theta, ThetaConfig.CegarExplPredCombined, "theta-cegar-combined"),
        PortfolioTask(nuxmv, NuxmvConfig.Ic3Invar, "nuxmv-ic3"),
        PortfolioTask(uppaal, UppaalConfig.Default, "uppaal-default"),
        PortfolioTask(spin, SpinConfig.SafeDfs, "spin-safe-dfs"),
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
            logger.info { "$id: no available backends on this host" }
            return PortfolioOutcome.NoDecision(availability.reason, emptyList()).toBackendVerificationResult(startedAt)
        }
        val available = tasks.filter {
            it.leg.isAvailable(environment)
        }
        logger.info {
            val subPaths = available.joinToString {
                it.subPath
            }
            "$id: requiring agreement across $subPaths (timeout ${request.timeout})"
        }
        progress.reportProgress("running ${available.size} backend(s)")

        val outcome = all(startCase(parentInjector, request, gate, environment, progress, readBack)) {
            legs(available)
        }

        return outcome.toBackendVerificationResult(startedAt)
    }
}
