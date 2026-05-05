/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.theta.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.portfolio.withSubPath
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ThetaFullPortfolio(
    private val stageTimeout: Duration = 10.minutes,
    private val theta: VerificationBackend<ThetaConfig> = ThetaBackend(),
) : VerificationPortfolio() {
    override val id: String = "theta-full"
    override val displayName: String = "Theta full portfolio"
    override val description: String = "Races all Theta configurations in parallel."

    private val tasks: List<PortfolioTask<ThetaConfig>> = listOf(
        PortfolioTask(theta, ThetaConfig.CegarExpl, "cegarexpl"),
        PortfolioTask(theta, ThetaConfig.CegarExplPredCombined, "cegarexplpred"),
        PortfolioTask(theta, ThetaConfig.CegarPredCart, "cegarexplcart"),
        PortfolioTask(theta, ThetaConfig.BoundedKInduction, "kinduction"),
    )

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
        if (!tasks.any { it.isAvailable(environment) }) {
            logger.info { "$id: no available Theta executor on this host" }
            return BackendVerificationResult(
                metadata = VerificationMetadata(
                    backendId = null,
                    startedAt = startedAt,
                ),
                verdict = VerificationVerdict.Inconclusive,
                metrics = BackendMetrics(),
                message = "$id: no available Theta executor",
            )
        }
        progress.reportProgress("racing ${tasks.size} Theta configurations")
        val outcome = firstDecisive(gate, stageTimeout, progress) {
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
