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
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackend
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackend
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.backends.theta.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioCase
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioStages
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioTask
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class SmartFullPortfolio(
    theta: VerificationBackend<ThetaConfig> = ThetaBackend(),
    uppaal: VerificationBackend<UppaalConfig> = UppaalBackend(),
    nuxmv: VerificationBackend<NuxmvConfig> = NuxmvBackend(),
    spin: VerificationBackend<SpinConfig> = SpinBackend(),
    private val firstWindow: Duration = 10.seconds,
    private val secondWindow: Duration = 30.seconds,
    private val windowShare: Double = 1.0 / 3.0,
    private val shortestSecondWindow: Duration = 10.seconds,
    timeSource: TimeSource = TimeSource.Monotonic,
) : VerificationPortfolio(timeSource) {
    override val id: String = "smart"
    override val displayName: String = "Smart"
    override val description: String = "Runs the installed backends in stages, the quick configurations briefly first and the strong ones longer after, and takes the first trusted answer."

    private val thetaBmc = PortfolioTask(theta, ThetaConfig.BoundedBmc, "theta-bmc")
    private val thetaKInduction = PortfolioTask(theta, ThetaConfig.BoundedKInduction, "theta-kinduction")
    private val thetaImc = PortfolioTask(theta, ThetaConfig.BoundedImc, "theta-imc")
    private val thetaCegarExpl = PortfolioTask(theta, ThetaConfig.CegarExpl, "theta-cegar-expl")
    private val thetaCegarPred = PortfolioTask(theta, ThetaConfig.CegarPredCart, "theta-cegar-pred")
    private val thetaCegarCombined = PortfolioTask(theta, ThetaConfig.CegarExplPredCombined, "theta-cegar-combined")
    private val thetaIc3 = PortfolioTask(theta, ThetaConfig.Ic3, "theta-ic3")
    private val thetaChc = PortfolioTask(theta, ThetaConfig.Chc, "theta-chc")
    private val nuxmvKInduction = PortfolioTask(nuxmv, NuxmvConfig.KInductionInvar, "nuxmv-kinduction")
    private val nuxmvIc3 = PortfolioTask(nuxmv, NuxmvConfig.Ic3Invar, "nuxmv-ic3")
    private val nuxmvBdd = PortfolioTask(nuxmv, NuxmvConfig.BddInvar, "nuxmv-bdd")
    private val spinSafeDfs = PortfolioTask(spin, SpinConfig.SafeDfs, "spin-safe-dfs")
    private val spinCollapse = PortfolioTask(spin, SpinConfig.Collapse, "spin-collapse")
    private val spinBitstate = PortfolioTask(spin, SpinConfig.BitstateHashing, "spin-bitstate")
    private val uppaalDefault = PortfolioTask(uppaal, UppaalConfig.Default, "uppaal-default")
    private val uppaalOverApproximation = PortfolioTask(uppaal, UppaalConfig.OverApproximation, "uppaal-over-approx")

    private val stages = PortfolioStages {
        stage("first", ::firstStageWindow) {
            legs(
                thetaBmc,
                spinSafeDfs,
                uppaalOverApproximation,
                nuxmvIc3,
                uppaalDefault,
                thetaKInduction,
                nuxmvKInduction,
                thetaCegarExpl,
                nuxmvBdd,
            )
        }
        stage("second", ::secondStageWindow, skippedUnder = shortestSecondWindow) {
            legs(
                thetaCegarExpl,
                thetaCegarPred,
                thetaChc,
                nuxmvIc3,
                thetaImc,
                thetaCegarCombined,
            )
        }
        stage("last") {
            legs(
                thetaCegarExpl,
                nuxmvIc3,
                thetaCegarPred,
                thetaChc,
                uppaalOverApproximation,
                thetaIc3,
            )
            // a plain search that ended on its own has finished the space, refused the model or stopped at the depth its variants share
            unlessEndedOnItsOwn(spinSafeDfs) {
                legs(
                    spinCollapse,
                    spinBitstate,
                )
            }
        }
    }

    override val executorKeys: Set<ExecutorKey<*>> = stages.tasks.map {
        it.leg.executorKey
    }.toSet()
    override val legs = stages.tasks.map {
        it.leg
    }

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        val anyAvailable = stages.tasks.any {
            it.leg.isAvailable(environment)
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
        val outcome = staged(startCase(parentInjector, request, gate, environment, progress), stages)
        return outcome.toBackendVerificationResult(startedAt)
    }

    private fun firstStageWindow(case: PortfolioCase): Duration {
        return minOf(firstWindow, case.time * windowShare)
    }

    private fun secondStageWindow(case: PortfolioCase): Duration {
        return minOf(secondWindow, case.time * windowShare - case.elapsed())
    }
}
