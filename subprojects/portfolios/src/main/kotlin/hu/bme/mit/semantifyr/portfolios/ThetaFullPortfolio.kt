/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.theta.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verification.portfolio.withSubPath
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ThetaFullPortfolio(
    private val stageTimeout: Duration = 10.minutes,
    private val tasks: List<PortfolioTask<ThetaConfig>> = defaultThetaTasks(),
) : VerificationPortfolio() {
    override val id: String = "theta-full"
    override val displayName: String = "Theta full portfolio"
    override val description: String = "Races all Theta configurations in parallel."

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        val anyAvailable = tasks.any {
            it.isAvailable(environment)
        }
        return if (anyAvailable) {
            AvailabilityReport.Available
        } else {
            AvailabilityReport.Unavailable(
                reason = "No Theta executor is available on this host.",
            )
        }
    }

    override suspend fun verify(
        parentInjector: Injector,
        request: BackendVerificationRequest,
        gate: ConcurrencyGate,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult {
        val startedAt = Clock.System.now()
        val outcome = firstDecisive(gate, stageTimeout) {
            for (task in tasks) {
                async {
                    gate.withPermit {
                        task.run(parentInjector, request.withSubPath(task.subPath), environment)
                    }
                }
            }
        }
        return outcome.toBackendVerificationResult(startedAt)
    }
}

private fun defaultThetaTasks(): List<PortfolioTask<ThetaConfig>> {
    val backend = ThetaBackend()
    return listOf(
        PortfolioTask(backend, ThetaConfig.CegarExpl, "cegarexpl"),
        PortfolioTask(backend, ThetaConfig.CegarExplPredCombined, "cegarexplpred"),
        PortfolioTask(backend, ThetaConfig.CegarPredCart, "cegarexplcart"),
        PortfolioTask(backend, ThetaConfig.BoundedKInduction, "kinduction"),
    )
}
