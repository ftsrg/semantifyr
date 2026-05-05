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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AllAgreeFullPortfolio(
    private val timeout: Duration = 25.minutes,
    private val theta: VerificationBackend<ThetaConfig> = ThetaBackend(),
    private val nuxmv: VerificationBackend<NuxmvConfig> = NuxmvBackend(),
    private val uppaal: VerificationBackend<UppaalConfig> = UppaalBackend(),
    private val spin: VerificationBackend<SpinConfig> = SpinBackend(),
) : VerificationPortfolio() {
    override val id: String = "all-agree-full"
    override val displayName: String = "All backends agree portfolio"
    override val description: String = "Runs one representative config per backend in parallel."

    private val tasks: List<PortfolioTask<*>> = listOf(
        PortfolioTask(theta, ThetaConfig.CegarExplPredCombined, "theta-cegar-combined"),
        PortfolioTask(nuxmv, NuxmvConfig.Ic3Invar, "nuxmv-ic3"),
        PortfolioTask(uppaal, UppaalConfig.Default, "uppaal-default"),
        PortfolioTask(spin, SpinConfig.SafeDfs, "spin-safe-dfs"),
    )

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        val anyAvailable = tasks.any {
            it.isAvailable(environment)
        }
        return if (anyAvailable) {
            AvailabilityReport.Available
        } else {
            AvailabilityReport.Unavailable(
                reason = "No verification backends are available on this system.",
                hints = listOf(
                    "Install at least one of: theta-xsts-cli, Uppaal, nuXmv, or spin.",
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
                message = "$id: no available backends",
            )
        }
        logger.info { "$id: requiring agreement across ${available.joinToString { it.subPath }} (timeout $timeout)" }
        progress.reportProgress("running ${available.size} backend(s)")

        val outcome = all(gate, timeout, progress) {
            available.forEach {
                async {
                    gate.withPermit {
                        it.run(parentInjector, request.withSubPath(it.subPath), environment)
                    }
                }
            }
        }

        return outcome.toBackendVerificationResult(startedAt)
    }
}
