/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import hu.bme.mit.semantifyr.frontends.gamma.GammaCompiler
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ExecutionTrace
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
import hu.bme.mit.semantifyr.frontends.gamma.trace.GammaTraceGenerator
import hu.bme.mit.semantifyr.verifier.witness.CallTrace
import hu.bme.mit.semantifyr.verifier.witness.CallTraceStep
import hu.bme.mit.semantifyr.verifier.witness.TraceArgument
import hu.bme.mit.semantifyr.verifier.witness.TraceEntry
import hu.bme.mit.semantifyr.verifier.witness.WitnessState
import hu.bme.mit.semantifyr.verifier.witness.WitnessStateStep
import hu.bme.mit.semantifyr.verifier.witness.WitnessStateValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path

@InjectWithGamma
class GammaTraceGeneratorTest {

    @Inject
    private lateinit var gammaReader: GammaReader

    @Inject
    private lateinit var gammaCompiler: GammaCompiler

    @Inject
    private lateinit var gammaTraceGenerator: GammaTraceGenerator

    @TempDir
    private lateinit var temporaryDirectory: Path

    @Test
    fun `a raise of an event with parameters is an act without its value`() {
        val trace = generate(
            "Parameters.gamma",
            "EnvironmentChoosesTheParameterWithTheRaise",
            listOf(
                raise(RAISE_INPUT_EVENT, "root.component._measure", "root.component._measure.interface._level"),
            ),
            listOf(
                WitnessStateValue("root.component._Main.activeState", "root.component._Main._Holding"),
                WitnessStateValue("root.component._measure.interface._level._value.value", "7"),
            ),
        )

        val act = trace.steps.last().acts.filterIsInstance<RaiseEventAct>().single()
        assertThat(listOf(act.port.name, act.event.name)).containsExactly("measure", "level")
        assertThat(act.arguments).isEmpty()
    }

    @Test
    fun `a history region's last state is not asserted as an active state`() {
        val trace = generate(
            "History.gamma",
            "TransitionToTheHistoryNodeRestoresTheLastState",
            emptyList(),
            listOf(
                WitnessStateValue("root.component._Main.activeState", "root.component._Main._Paused"),
                WitnessStateValue("root.component._Main._Working._Progress.activeState", "nothing"),
                WitnessStateValue("root.component._Main._Working._Progress._ProgressHistory.lastState", "root.component._Main._Working._Progress._Second"),
            ),
        )

        val states = trace.steps.last().asserts.filterIsInstance<StateReferenceExpression>().map {
            "${it.region.name}.${it.state.name}"
        }
        assertThat(states).containsExactly("Main.Paused")
    }

    @Test
    fun `a cascade's input is an act on its own port and an inner raise is asserted on the raising instance's port`() {
        val trace = generate(
            "Cascade.gamma",
            "EarlierInstanceSeesTheReplyInALaterCycle",
            listOf(
                raise(RAISE_INPUT_EVENT, "root.component._asker._control", "root.component._asker._control.interface._start"),
                raise(SET_OUTPUT_EVENT, "root.component._asker._link", "root.component._asker._link.interface._ask"),
            ),
            listOf(
                WitnessStateValue("root.component._asker._Main.activeState", "root.component._asker._Main._Sent"),
                WitnessStateValue("root.component._answerer._Main.activeState", "root.component._answerer._Main._Asked"),
            ),
        )

        val step = trace.steps.last()
        val act = step.acts.filterIsInstance<RaiseEventAct>().single()
        assertThat(listOf(act.instance, act.port.name, act.event.name)).containsExactly(null, "control", "start")
        val raise = step.asserts.filterIsInstance<RaiseEventAct>().single()
        assertThat(listOf(raise.instance.instance.name, raise.port.name, raise.event.name)).containsExactly("asker", "link", "ask")
        assertThat(step.asserts.filterIsInstance<StateReferenceExpression>()).hasSize(2)
    }

    private fun generate(
        modelFile: String,
        caseName: String,
        traces: List<TraceEntry>,
        values: List<WitnessStateValue>,
    ): ExecutionTrace {
        val gammaPackage = gammaReader.readGammaFile(SEMANTICS.resolve(modelFile).toFile())
        val nameMap = gammaCompiler.compile(gammaPackage, temporaryDirectory.resolve("model.oxsts"))
        val verificationCase = gammaPackage.verificationCases.single {
            it.name == caseName
        }
        val callTrace = CallTrace(CallTraceStep(emptyList()), listOf(CallTraceStep(traces)))
        val witnessState = WitnessState(WitnessStateStep(emptyList()), listOf(WitnessStateStep(values)))
        return gammaTraceGenerator.generate(verificationCase, callTrace, witnessState, nameMap)
    }

    private fun raise(
        calledTransition: String,
        port: String,
        event: String,
    ): TraceEntry {
        return TraceEntry(port, calledTransition, listOf(TraceArgument("event", event)), null)
    }

    companion object {
        private val SEMANTICS = Path("..", "models", "semantics")
        private const val RAISE_INPUT_EVENT = "semantifyr::gamma::ports::Port::raiseInputEvent"
        private const val SET_OUTPUT_EVENT = "semantifyr::gamma::ports::Port::setOutputEvent"
    }
}
