/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.backend.witness.linearInlinedWitness
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.assumePortfolioAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.witness.WitnessReplay
import hu.bme.mit.semantifyr.verifier.witness.WitnessReplayResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeLines
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class WitnessReplaySpikeTest {

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val loader = injector.getInstance(SemantifyrLoader::class.java)
    private val replay = injector.getInstance(WitnessReplay::class.java)
    private val outputDirectory = Path("build", "test-artifacts", "WitnessReplaySpikeTest")

    @Test
    suspend fun `replays the SysML crossroads witness`() {
        val caseClass = caseClass(
            Path("build", "sysml-crossroads", "library"),
            Path("build", "sysml-crossroads", "crossroads.oxsts"),
            "TrafficLightABehaviorNormalGreenUnreachableViolated",
        )
        measure("sysml-crossroads", caseClass, Portfolios.ThetaCegarExpl)
    }

    @Test
    suspend fun `replays the Gamma crossroads witness`() {
        val caseClass = caseClass(
            Path("..", "models", "libraries", "default"),
            Path("build", "compiled-examples", "Crossroads.oxsts"),
            "ControllerOperatingStoppingAReachable",
        )
        measure("gamma-crossroads", caseClass, Portfolios.ThetaBoundedKInduction)
    }

    private fun caseClass(library: Path, model: Path, name: String): ClassDeclaration {
        val context = loader.startContext()
            .loadLibraries(library)
            .loadModel(model)
            .buildAndResolve()
        return context.modelResources.single().allContents.asSequence().filterIsInstance<ClassDeclaration>().single {
            it.name == name
        }
    }

    private suspend fun measure(label: String, caseClass: ClassDeclaration, portfolio: VerificationPortfolio) {
        assumePortfolioAvailable(portfolio)
        val lines = mutableListOf<String>()
        val verifier = SemantifyrVerifier.builder()
            .injector(injector)
            .portfolio(portfolio)
            .artifacts(ArtifactConfig.NONE)
            .outputDirectory(outputDirectory.resolve(label))
            .build()
        val (result, runDuration) = measureTimedValue {
            verifier.verify(VerificationCase(caseClass.name, caseClass))
        }
        lines += "run: ${result.verdict} in $runDuration (portfolio ${result.metrics.backend?.verificationDuration} backend)"
        val trace = checkNotNull(result.trace) {
            "no trace: ${result.verdict} ${result.message}"
        }
        val witness = checkNotNull(trace.inlinedWitness)
        val traces = checkNotNull(trace.transitionCallTraces)
        val inlinedOxsts = witness.inlinedOxsts
        val tracers = inlinedOxsts.variables.count {
            TransitionTracerNames.isTracerName(it.name)
        }
        lines += "flattened: ${inlinedOxsts.variables.size} variables, $tracers tracers, ${witness.transitionStates.size + 1} witness states"
        val missing = inlinedOxsts.variables.size - witness.initializedState.values.size
        lines += "witness values per state: ${witness.initializedState.values.size}, variables without a value: $missing"
        lines += "constructs: ${constructs(witness)}"

        val replayed = replay.replay(witness, traces)
        lines += "replay: ${describe(replayed)}"
        for (step in replayed.steps) {
            lines += "  step ${step.index}: ${step.exploredPaths} paths, ${step.choicePoints} choice points"
        }
        val replayDurations = List(6) {
            measureTime {
                replay.replay(witness, traces)
            }
        }.drop(1)
        val replayTimes = replayDurations.joinToString {
            it.toString()
        }
        lines += "replay times (warm): $replayTimes"

        repeat(3) {
            val (recheck, recheckDuration) = measureTimedValue {
                verifier.verify(trace.backAnnotatedModel)
            }
            lines += "re-check ${it + 1}: ${recheck.verdict} in $recheckDuration (compile ${recheck.metrics.verifier?.compilationDuration}, backend ${recheck.metrics.backend?.verificationDuration})"
        }

        val states = listOf(witness.initializedState) + witness.transitionStates
        val middle = states.size / 2
        val dataValue = states[middle].values.first {
            !TransitionTracerNames.isTracerName(it.variable.name) && changed(it.value) != null
        }
        lines += plant("changed ${dataValue.variable.name} in state $middle", witness, traces, states, middle, dataValue.variable, changed(dataValue.value)!!)
        val clearedTracer = tracerValue(states, middle, false)
        lines += plant("set ${clearedTracer.variable.name} in state $middle", witness, traces, states, middle, clearedTracer.variable, OxstsFactory.createLiteralBoolean(true))
        val setTracer = tracerValue(states, middle, true)
        lines += plant("cleared ${setTracer.variable.name} in state $middle", witness, traces, states, middle, setTracer.variable, OxstsFactory.createLiteralBoolean(false))

        outputDirectory.createDirectories()
        outputDirectory.resolve("$label.txt").writeLines(lines)
        println(lines.joinToString("\n"))
        assertThat(replayed.firstFailure).isNull()
    }

    private fun plant(
        fault: String,
        witness: InlinedWitness,
        traces: TransitionCallTraceMap,
        states: List<WitnessState>,
        index: Int,
        variable: VariableDeclaration,
        value: Expression,
    ): String {
        val faulty = states.mapIndexed { stateIndex, state ->
            if (stateIndex == index) {
                WitnessState(
                    state.values.map {
                        if (it.variable == variable) {
                            WitnessStateValue(variable, value)
                        } else {
                            it
                        }
                    },
                )
            } else {
                state
            }
        }
        val (replayed, duration) = measureTimedValue {
            replay.replay(linearInlinedWitness(witness.inlinedOxsts, faulty), traces)
        }
        return "fault ($fault): ${describe(replayed)} in $duration"
    }

    private fun describe(replayed: WitnessReplayResult): String {
        val failure = replayed.firstFailure
        val outcome = if (failure == null) {
            "accepted"
        } else {
            "rejected at step ${failure.index}: ${failure.failure}"
        }
        return "$outcome, ${replayed.exploredPaths} paths over ${replayed.steps.size} steps"
    }

    private fun tracerValue(states: List<WitnessState>, middle: Int, set: Boolean): WitnessStateValue {
        return states[middle].values.first {
            TransitionTracerNames.isTracerName(it.variable.name) && (it.value as LiteralBoolean).isValue == set
        }
    }

    private fun changed(value: Expression): Expression? {
        return when (value) {
            is LiteralBoolean -> OxstsFactory.createLiteralBoolean(!value.isValue)
            is LiteralInteger -> OxstsFactory.createLiteralInteger(value.value + 1)
            is ElementReference -> {
                val literal = value.element as? EnumLiteral ?: return null
                val other = (literal.eContainer() as EnumDeclaration).literals.firstOrNull {
                    it != literal
                } ?: return null
                OxstsFactory.createElementReference(other)
            }
            else -> null
        }
    }

    private fun constructs(witness: InlinedWitness): String {
        val transitions = listOf(
            witness.inlinedOxsts.initTransition,
            witness.inlinedOxsts.mainTransition,
        )
        return transitions.flatMap {
            it.eAllContents().asSequence().toList()
        }.groupingBy {
            it.eClass().name
        }.eachCount().entries.sortedByDescending {
            it.value
        }.joinToString {
            "${it.key} ${it.value}"
        }
    }
}
