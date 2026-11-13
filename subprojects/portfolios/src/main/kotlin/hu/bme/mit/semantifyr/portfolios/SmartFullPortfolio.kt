/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
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
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
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
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.portfolio.withSubPath
import kotlin.time.Clock

class SmartFullPortfolio(
    private val theta: VerificationBackend<ThetaConfig> = ThetaBackend(),
    private val uppaal: VerificationBackend<UppaalConfig> = UppaalBackend(),
    private val nuxmv: VerificationBackend<NuxmvConfig> = NuxmvBackend(),
    private val spin: VerificationBackend<SpinConfig> = SpinBackend(),
) : VerificationPortfolio() {
    override val id: String = "smart"
    override val displayName: String = "Smart"
    override val description: String = "Runs the installed backends side by side and takes the first trusted answer."

    private val tasks: List<PortfolioTask<*>> = listOf(
        PortfolioTask(nuxmv, NuxmvConfig.BmcInvar, "nuxmv-bmc"),
        PortfolioTask(theta, ThetaConfig.BoundedBmc, "theta-bmc"),
        PortfolioTask(spin, SpinConfig.SafeDfs, "spin-safe-dfs"),
        PortfolioTask(uppaal, UppaalConfig.Default, "uppaal-default"),
        PortfolioTask(nuxmv, NuxmvConfig.Ic3Invar, "nuxmv-ic3"),
        PortfolioTask(theta, ThetaConfig.CegarExplPredCombined, "theta-cegar-combined"),
        PortfolioTask(theta, ThetaConfig.BoundedKInduction, "theta-kinduction"),
        PortfolioTask(theta, ThetaConfig.Ic3, "theta-ic3"),
        PortfolioTask(uppaal, UppaalConfig.OverApproximation, "uppaal-over-approx"),
        PortfolioTask(spin, SpinConfig.BitstateHashing, "spin-bitstate"),
    )

    override val executorKeys: Set<ExecutorKey<*>> = tasks.map { it.executorKey }.toSet()
    override val legs = tasks.map { it.leg }

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        val anyAvailable = tasks.any {
            it.isAvailable(environment)
        }
        return if (anyAvailable) {
            AvailabilityReport.Available
        } else {
            AvailabilityReport.Unavailable(
                reason = "No backend is installed.",
                hints = listOf(
                    "Install at least one of Theta (theta-xsts-cli), nuXmv, Spin or Uppaal (verifyta).",
                ),
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
        val available = tasks.filter {
            it.isAvailable(environment)
        }
        if (available.isEmpty()) {
            logger.info { "$id: no available backends on this host" }
            return BackendVerificationResult(
                metadata = VerificationMetadata(
                    backendId = null,
                    startedAt = startedAt,
                ),
                verdict = VerificationVerdict.Inconclusive,
                metrics = BackendMetrics(),
                message = "No backend is installed.",
            )
        }
        logger.info {
            val subPaths = available.joinToString {
                it.subPath
            }
            "$id: racing $subPaths (timeout ${request.timeout})"
        }
        progress.reportProgress("racing ${available.size} backend(s)")

        val outcome = firstDecisive(gate, request.timeout, progress) {
            for (task in available) {
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
