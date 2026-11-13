/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backend.witness.ReplayOutcome
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.backend.witness.linearInlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessReplay
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.nodemodel.util.NodeModelUtils
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeLines
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

class WitnessReplaySpikeTest {

    private class Selection(
        val model: String,
        val cases: List<String>,
        val backends: List<String>,
    )

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val loader = injector.getInstance(SemantifyrLoader::class.java)
    private val replay = injector.getInstance(WitnessReplay::class.java)
    private val outputDirectory = Path("build", "test-artifacts", "WitnessReplaySpikeTest")
    private val portfolios = mapOf(
        "theta" to Portfolios.ThetaCegarExpl,
        "spin" to Portfolios.SpinSafeDfs,
        "uppaal" to Portfolios.UppaalDefault,
    )
    private val lines = mutableListOf<String>()
    private val presentConstructs = mutableSetOf<String>()
    private val exercisedConstructs = mutableSetOf<String>()

    @Test
    suspend fun `replays SysML witnesses`() {
        val examples = Path("build", "sysml-examples")
        val selections = listOf(
            Selection(
                "crossroads",
                listOf(
                    "TrafficLightABehaviorNormalGreenUnreachableViolated",
                    "ControllerInterruptedUnreachableViolated",
                    "UnreachableRedGreenViolated",
                ),
                listOf(
                    "theta",
                    "uppaal",
                ),
            ),
            Selection(
                "level_crossing",
                listOf(
                    "BarrierDownWhileTrainInCrossingViolated",
                    "TwoTrainsCanBeInTheCrossing",
                ),
                listOf(
                    "theta",
                    "spin",
                ),
            ),
            Selection(
                "door_access",
                listOf(
                    "LockedAndCheckingSimultaneousViolated",
                    "OpenReachable",
                ),
                listOf(
                    "theta",
                    "spin",
                    "uppaal",
                ),
            ),
            Selection(
                "door_access",
                listOf(
                    "CheckingReachable",
                ),
                listOf(
                    "spin",
                    "uppaal",
                ),
            ),
            Selection(
                "orion_protocol",
                listOf(
                    "MasterConnectedReachable",
                    "BothMasterAndSlaveConnectedUnreachableViolated",
                ),
                listOf(
                    "theta",
                    "spin",
                    "uppaal",
                ),
            ),
            Selection(
                "compressedspacecraft",
                listOf(
                    "SpacecraftDataCannotReach60Violated",
                ),
                listOf(
                    "theta",
                    "uppaal",
                ),
            ),
        )
        for (selection in selections) {
            val resource = load(examples.resolve("library"), examples.resolve("${selection.model}.oxsts"))
            run("sysml", resource, selection)
        }
        write("sysml")
    }

    @Test
    suspend fun `replays Gamma witnesses`() {
        val everyBackend = listOf(
            "theta",
            "spin",
            "uppaal",
        )
        val selections = listOf(
            Selection(
                "Simple",
                listOf(
                    "LeaderStatechartOperationalReachable",
                    "WorkerStatechartStoppedReachable",
                ),
                everyBackend,
            ),
            Selection(
                "Crossroads",
                listOf(
                    "ControllerOperatingStoppingAReachable",
                    "ControllerInterruptedReachable",
                    "ACanBeBlinkingBlack",
                    "ANormalReachable",
                ),
                everyBackend,
            ),
            Selection(
                "LevelCrossing",
                listOf(
                    "BarrierDownWhileTrainInCrossing",
                    "TwoTrainsCanBeInTheCrossing",
                ),
                everyBackend,
            ),
            Selection(
                "Spacecraft",
                listOf(
                    "Spacecraft_CommunicationTransmittingSendDataSendingReachable",
                    "Spacecraft_BatteryRechargingReachable",
                ),
                everyBackend,
            ),
        )
        for (selection in selections) {
            val resource = load(Path("..", "models", "libraries", "default"), Path("build", "compiled-examples", "${selection.model}.oxsts"))
            run("gamma", resource, selection)
        }
        write("gamma")
    }

    @Test
    suspend fun `replays witnesses of the semantic models`() {
        val semantic = Path("..", "..", "..", "..", "oxsts-test-models", "semantic")
        val selections = listOf(
            Selection(
                "runtime_for",
                emptyList(),
                listOf(
                    "theta",
                ),
            ),
            Selection(
                "arrays",
                emptyList(),
                listOf(
                    "theta",
                    "spin",
                ),
            ),
            Selection(
                "control_flow",
                emptyList(),
                listOf(
                    "theta",
                    "spin",
                    "uppaal",
                ),
            ),
            Selection(
                "local_vars",
                emptyList(),
                listOf(
                    "theta",
                ),
            ),
        )
        for (selection in selections) {
            val resource = load(null, semantic.resolve("${selection.model}.oxsts"))
            val cases = resource.allContents.asSequence().filterIsInstance<ClassDeclaration>().filter {
                expectsWitness(it)
            }.map {
                it.name
            }.toList()
            run("semantic", resource, Selection(selection.model, cases, selection.backends))
        }
        write("semantic")
    }

    @Test
    suspend fun `reports planted tracer faults by their calls`() {
        val examples = Path("build", "sysml-examples")
        val resource = load(examples.resolve("library"), examples.resolve("crossroads.oxsts"))
        val caseClass = resource.allContents.asSequence().filterIsInstance<ClassDeclaration>().single {
            it.name == "TrafficLightABehaviorNormalGreenUnreachableViolated"
        }
        val verifier = SemantifyrVerifier.builder()
            .injector(injector)
            .portfolio(Portfolios.ThetaCegarExpl)
            .artifacts(ArtifactConfig.NONE)
            .outputDirectory(outputDirectory.resolve("faults"))
            .build()
        val trace = checkNotNull(verifier.verify(VerificationCase(caseClass.name, caseClass)).trace)
        val witness = checkNotNull(trace.inlinedWitness)
        val states = listOf(witness.initializedState) + witness.transitionStates
        val middle = states.size / 2
        for (set in listOf(true, false)) {
            val tracer = states[middle].values.first {
                TransitionTracerNames.isTracerName(it.variable.name) && (it.value as LiteralBoolean).isValue == set
            }
            val faulty = states.mapIndexed { index, state ->
                if (index == middle) {
                    WitnessState(
                        state.values.map {
                            if (it == tracer) {
                                WitnessStateValue(it.variable, OxstsFactory.createLiteralBoolean(!set))
                            } else {
                                it
                            }
                        },
                    )
                } else {
                    state
                }
            }
            val replayed = replay.replay(linearInlinedWitness(witness.inlinedOxsts, faulty), trace.transitionCallTraces)
            val failure = replayed.firstFailure
            lines += "flipped ${tracer.variable.name} to ${!set} in state $middle: ${replayed.outcome} at step ${failure?.index}: ${failure?.reason}"
        }
        write("faults")
    }

    private fun expectsWitness(classDeclaration: ClassDeclaration): Boolean {
        val text = NodeModelUtils.getNode(classDeclaration)?.text ?: return false
        val reach = text.contains("expect-pass") && text.contains("EF ")
        val violation = text.contains("expect-fail") && text.contains("AG ")
        return reach || violation
    }

    private fun load(library: Path?, model: Path): Resource {
        val context = loader.startContext()
        if (library != null) {
            context.loadLibraries(library)
        }
        return context.loadModel(model).buildAndResolve().modelResources.single()
    }

    private suspend fun run(frontend: String, resource: Resource, selection: Selection) {
        for (backend in selection.backends) {
            val portfolio = portfolios.getValue(backend)
            for (case in selection.cases) {
                val caseClass = resource.allContents.asSequence().filterIsInstance<ClassDeclaration>().single {
                    it.name == case
                }
                lines += measure(frontend, backend, portfolio, selection.model, caseClass)
            }
        }
    }

    private suspend fun measure(frontend: String, backend: String, portfolio: VerificationPortfolio, model: String, caseClass: ClassDeclaration): String {
        val prefix = "$frontend | $backend | $model::${caseClass.name}"
        val verifier = SemantifyrVerifier.builder()
            .injector(injector)
            .portfolio(portfolio)
            .artifacts(ArtifactConfig.NONE)
            .timeout(2.minutes)
            .outputDirectory(outputDirectory.resolve(frontend).resolve(backend))
            .build()
        val result = verifier.verify(VerificationCase(caseClass.name, caseClass))
        val trace = result.trace
        val witness = trace?.inlinedWitness
        if (trace == null || witness == null) {
            return "$prefix | ${result.verdict} | no witness | ${result.message ?: ""}"
        }
        for (transition in listOf(witness.inlinedOxsts.initTransition, witness.inlinedOxsts.mainTransition)) {
            transition.eAllContents().forEach {
                presentConstructs += it.eClass().name
            }
        }
        val replayed = try {
            replay.replay(witness, trace.transitionCallTraces)
        } catch (exception: IllegalStateException) {
            return "$prefix | ${result.verdict} | replay error | ${exception.message}"
        }
        exercisedConstructs += replayed.exercised
        val durations = List(4) {
            measureTime {
                replay.replay(witness, trace.transitionCallTraces)
            }
        }.drop(1).sorted()
        val failure = replayed.firstFailure
        val reason = if (failure == null) {
            ""
        } else {
            "step ${failure.index}: ${failure.reason}"
        }
        return "$prefix | ${result.verdict} | ${replayed.outcome} | ${witness.transitionStates.size + 1} states | most ${replayed.mostPathsInStep} total ${replayed.exploredPaths} paths | ${median(durations)} | $reason"
    }

    private fun median(durations: List<Duration>): Duration {
        return durations[durations.size / 2]
    }

    private fun write(label: String) {
        lines += "present: ${presentConstructs.sorted()}"
        lines += "exercised: ${exercisedConstructs.sorted()}"
        lines += "present, never exercised: ${(presentConstructs - exercisedConstructs).sorted()}"
        val counts = ReplayOutcome.entries.associateWith { outcome ->
            lines.count {
                it.contains("| $outcome |")
            }
        }
        lines += "counts: $counts"
        outputDirectory.createDirectories()
        outputDirectory.resolve("$label.txt").writeLines(lines)
    }
}
