/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationMetrics
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackend
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackend
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.backends.theta.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.portfolio.BackendExecutor
import hu.bme.mit.semantifyr.verification.portfolio.PortfolioOutcome
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verification.portfolio.withSubPath
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource.Monotonic.markNow

class SmartFullPortfolio(
    private val timeout: Duration = 25.minutes,
) : VerificationPortfolio() {
    override val id: String = "smart-full"
    override val displayName: String = "Smart parallel race across all backends"
    override val description: String =
        "Runs one representative config from each algorithm family of every installed backend " +
            "in parallel; first decisive verdict wins."
    override val familyId: String = "smart"

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        val anyAvailable = roster.any { it.isAvailable(environment) }
        return if (anyAvailable) {
            AvailabilityReport.Available
        } else {
            AvailabilityReport.Unavailable(
                reason = "No verification backends are available on this system.",
                hints = listOf(
                    "Install at least one of: theta-xsts-cli, verifyta (Uppaal), nuXmv, or spin.",
                    "See each backend's README for setup details.",
                ),
            )
        }
    }

    override suspend fun verify(
        request: VerificationRequest,
        executor: BackendExecutor,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult {
        val totalMark = markNow()
        val metadata = VerificationRunMetadata(
            backendId = id,
            startedAt = Clock.System.now(),
            caseQualifiedName = request.case.qualifiedName,
        )
        val available = roster.filter { it.isAvailable(environment) }
        if (available.isEmpty()) {
            logger.info { "$id: no available backends on this host" }
            return BackendVerificationResult.inconclusive(
                metadata = metadata,
                metrics = VerificationMetrics(totalDuration = totalMark.elapsedNow()),
                message = "$id: no available backends",
            )
        }
        logger.info { "$id: racing ${available.joinToString { it.subPath }} (timeout $timeout)" }
        progress.reportProgress("racing ${available.size} backend(s)")

        val outcome = firstDecisive(executor, timeout, progress) {
            available.forEach { task ->
                async {
                    executor.withPermit {
                        task.run(request.withSubPath(task.subPath), environment)
                    }
                }
            }
        }

        return when (outcome) {
            is PortfolioOutcome.Decided -> {
                outcome.result
            }
            is PortfolioOutcome.AllErrored -> {
                val firstMessage = outcome.exceptions
                    .firstOrNull()
                    ?.let { "${it::class.simpleName}: ${it.message ?: ""}" }
                    ?: "(no message)"
                val extra = (outcome.exceptions.size - 1).coerceAtLeast(0)
                val summary = if (extra > 0) "$firstMessage (+$extra more)" else firstMessage
                BackendVerificationResult.errored(
                    metadata = metadata,
                    metrics = VerificationMetrics(totalDuration = totalMark.elapsedNow()),
                    message = "$id: every racer errored - $summary",
                )
            }
            is PortfolioOutcome.NoDecision -> {
                BackendVerificationResult.inconclusive(
                    metadata = metadata,
                    metrics = VerificationMetrics(totalDuration = totalMark.elapsedNow()),
                    message = outcome.reason,
                )
            }
            is PortfolioOutcome.AllNotSupported -> {
                BackendVerificationResult.notSupported(
                    metadata = metadata,
                    metrics = VerificationMetrics(totalDuration = totalMark.elapsedNow()),
                    message = outcome.reason,
                )
            }
        }
    }

    /**
     * Curated roster: one representative per algorithm family per backend. Order is only a hint -
     * coroutines race for permits in a defined sequence, so listing the fast / frequently-decisive
     * configs first biases early winners without affecting correctness.
     */
    private val roster: List<BackendTask<*>> = listOf(
        // Fast bug-finders up front.
        BackendTask(NuxmvBackend, NuxmvConfig.BmcInvar, "nuxmv-bmc"),
        BackendTask(ThetaBackend, ThetaConfig.BoundedBmc, "theta-bmc"),
        BackendTask(SpinBackend, SpinConfig.SafeDfs, "spin-safe-dfs"),
        BackendTask(UppaalBackend, UppaalConfig.Default, "uppaal-default"),
        // Complete-safety provers.
        BackendTask(NuxmvBackend, NuxmvConfig.Ic3Invar, "nuxmv-ic3"),
        BackendTask(ThetaBackend, ThetaConfig.CegarExplPredCombined, "theta-cegar-combined"),
        BackendTask(ThetaBackend, ThetaConfig.BoundedKInduction, "theta-kinduction"),
        BackendTask(ThetaBackend, ThetaConfig.Ic3, "theta-ic3"),
        // Approximations: unsound but cheap, help on big finite state spaces.
        BackendTask(UppaalBackend, UppaalConfig.OverApproximation, "uppaal-over-approx"),
        BackendTask(SpinBackend, SpinConfig.BitstateHashing, "spin-bitstate"),
    )
}

private class BackendTask<T : Any>(
    private val backend: VerificationBackend<T>,
    private val config: T,
    val subPath: String,
) {
    fun isAvailable(environment: ExecutionEnvironment): Boolean = backend.probeAvailability(config, environment).isUsable

    suspend fun run(
        request: VerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult = backend.verify(config, request, environment)
}
