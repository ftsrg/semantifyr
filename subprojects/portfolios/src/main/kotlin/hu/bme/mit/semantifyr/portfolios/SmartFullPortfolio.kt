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
import hu.bme.mit.semantifyr.verifier.portfolio.LegRun
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioCase
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioOutcome
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.portfolio.withSubPath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
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
    private val nuxmvBmc = PortfolioTask(nuxmv, NuxmvConfig.BmcInvar, "nuxmv-bmc")
    private val nuxmvIc3 = PortfolioTask(nuxmv, NuxmvConfig.Ic3Invar, "nuxmv-ic3")
    private val nuxmvBdd = PortfolioTask(nuxmv, NuxmvConfig.BddInvar, "nuxmv-bdd")
    private val spinSafeDfs = PortfolioTask(spin, SpinConfig.SafeDfs, "spin-safe-dfs")
    private val spinCollapse = PortfolioTask(spin, SpinConfig.Collapse, "spin-collapse")
    private val spinBitstate = PortfolioTask(spin, SpinConfig.BitstateHashing, "spin-bitstate")
    private val uppaalDefault = PortfolioTask(uppaal, UppaalConfig.Default, "uppaal-default")
    private val uppaalOverApproximation = PortfolioTask(uppaal, UppaalConfig.OverApproximation, "uppaal-over-approx")

    private val firstStage = SmartStage(
        name = "first",
        tasks = listOf(
            thetaBmc,
            spinSafeDfs,
            uppaalOverApproximation,
            nuxmvIc3,
            uppaalDefault,
            thetaKInduction,
            nuxmvBmc,
            thetaCegarExpl,
            nuxmvBdd,
        ),
    ) { case ->
        minOf(firstWindow, case.time * windowShare)
    }

    private val secondStage = SmartStage(
        name = "second",
        tasks = listOf(
            thetaCegarExpl,
            thetaCegarPred,
            nuxmvIc3,
            thetaImc,
            thetaCegarCombined,
        ),
    ) { case ->
        minOf(secondWindow, case.time * windowShare - case.elapsed())
    }

    private val lastStage = SmartStage(
        name = "last",
        tasks = listOf(
            thetaCegarExpl,
            nuxmvIc3,
            thetaCegarPred,
            uppaalOverApproximation,
            thetaIc3,
            spinCollapse,
            spinBitstate,
        ),
    ) {
        Duration.INFINITE
    }

    private val stages = listOf(
        firstStage,
        secondStage,
        lastStage,
    )

    private val tasks = stages.flatMap {
        it.tasks
    }.distinct()

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
        val case = startCase(request, gate, progress)
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

        if (available.size <= case.slots) {
            logger.info { "$id: racing ${available.size} configurations on ${case.slots} slots (timeout ${request.timeout})" }
            progress.reportProgress("racing ${available.size} backend configurations")
            val outcome = firstDecisive(case) {
                for (task in available) {
                    leg(task.leg.id) {
                        task.run(parentInjector, request.withSubPath(task.subPath), environment)
                    }
                }
            }
            return outcome.toBackendVerificationResult(startedAt)
        }

        return runStages(case, available, parentInjector, environment).toBackendVerificationResult(startedAt)
    }

    // a configuration listed in consecutive stages runs once, from the first of them, in a gate of its own beside the stages
    private suspend fun runStages(
        case: PortfolioCase,
        available: List<PortfolioTask<*>>,
        parentInjector: Injector,
        environment: ExecutionEnvironment,
    ): PortfolioOutcome {
        val runs = CopyOnWriteArrayList<LegRun>()
        val decided = CompletableDeferred<PortfolioOutcome?>()
        return coroutineScope {
            val work = launch {
                coroutineScope {
                    val pendingThrough = mutableListOf<PortfolioTask<*>>()
                    for ((index, stage) in stages.withIndex()) {
                        pendingThrough += stage.tasks.filter {
                            it in available && startsRunningThrough(it, index)
                        }
                        val window = stage.window(case)
                        if (stage == secondStage && minOf(window, case.timeLeft()) < shortestSecondWindow) {
                            logger.info { "$id: skipping the ${stage.name} stage, its window of $window is shorter than $shortestSecondWindow" }
                            continue
                        }
                        if (pendingThrough.isNotEmpty()) {
                            val throughTasks = pendingThrough.toList()
                            pendingThrough.clear()
                            launch {
                                val outcome = runGate(stage, "running from the ${stage.name} stage", throughTasks, Duration.INFINITE, case, parentInjector, environment)
                                runs += outcome.runs
                                if (outcome is PortfolioOutcome.Decided) {
                                    decided.complete(outcome)
                                }
                            }
                        }
                        val stageTasks = stage.tasks.filter {
                            it in available && !runsThrough(it, index) && !endedOnItsOwn(it, runs) && runsInStage(it, stage, runs)
                        }
                        if (stageTasks.isEmpty()) {
                            logger.info { "$id: the ${stage.name} stage has no configuration of its own to start" }
                            continue
                        }
                        val outcome = runGate(stage, "${stage.name} stage", stageTasks, window, case, parentInjector, environment)
                        runs += outcome.runs
                        if (outcome is PortfolioOutcome.Decided) {
                            decided.complete(outcome)
                            return@coroutineScope
                        }
                    }
                }
                decided.complete(null)
            }
            val outcome = decided.await()
            work.cancelAndJoin()
            outcome ?: undecided(runs.toList())
        }
    }

    private suspend fun runGate(
        stage: SmartStage,
        label: String,
        gateTasks: List<PortfolioTask<*>>,
        window: Duration,
        case: PortfolioCase,
        parentInjector: Injector,
        environment: ExecutionEnvironment,
    ): PortfolioOutcome {
        val within = if (window.isInfinite()) {
            "until the case's time runs out"
        } else {
            "within $window"
        }
        logger.info { "$id: $label, racing ${gateTasks.size} configurations on ${case.slots} slots $within" }
        case.progress.reportProgress("$label: racing ${gateTasks.size} backend configurations $within")
        return firstDecisive(case, window, label) {
            for (task in gateTasks) {
                leg(task.leg.id) {
                    task.run(parentInjector, case.request.withSubPath("${stage.name}/${task.subPath}"), environment)
                }
            }
        }
    }

    private fun startsRunningThrough(task: PortfolioTask<*>, stageIndex: Int): Boolean {
        return listedIn(task, stageIndex + 1) && !listedIn(task, stageIndex - 1)
    }

    private fun runsThrough(task: PortfolioTask<*>, stageIndex: Int): Boolean {
        return listedIn(task, stageIndex + 1) || listedIn(task, stageIndex - 1)
    }

    private fun listedIn(task: PortfolioTask<*>, stageIndex: Int): Boolean {
        return stageIndex in stages.indices && task in stages[stageIndex].tasks
    }

    private fun endedOnItsOwn(task: PortfolioTask<*>, runs: List<LegRun>): Boolean {
        return runs.any {
            it.leg == task.leg.id && it.end.endedOnItsOwn
        }
    }

    // a plain search that ended on its own has finished the space, refused the model or stopped at the depth its variants share
    private fun runsInStage(
        task: PortfolioTask<*>,
        stage: SmartStage,
        runs: List<LegRun>,
    ): Boolean {
        if (stage != lastStage || (task != spinCollapse && task != spinBitstate)) {
            return true
        }
        val depthFirstRun = runs.firstOrNull {
            it.stage == "${firstStage.name} stage" && it.leg == spinSafeDfs.leg.id
        }
        return depthFirstRun?.end?.endedOnItsOwn != true
    }

    private class SmartStage(
        val name: String,
        val tasks: List<PortfolioTask<*>>,
        val window: (PortfolioCase) -> Duration,
    )
}
