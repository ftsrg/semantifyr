/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import hu.bme.mit.semantifyr.frontends.gamma.GammaCompiler
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComparisonOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ExecutionTrace
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FiredTransitionExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralBoolean
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralInteger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ResetAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ScheduleAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Step
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeElapseAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableReferenceExpression
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

    @Test
    fun `the trace has a step per witness step after an initialization step that resets`() {
        val trace = generateSteps(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            listOf(WitnessStateValue("root.component._Main.activeState", "root.component._Main._Idle")),
            listOf(
                emptyList<TraceEntry>() to listOf(WitnessStateValue("root.component._Main.activeState", "root.component._Main._Idle")),
                emptyList<TraceEntry>() to listOf(WitnessStateValue("root.component._Main.activeState", "root.component._Main._Idle")),
            ),
        )

        assertThat(trace.steps).hasSize(3)
        assertThat(actTypes(trace.steps.first())).containsExactly(ResetAct::class.java)
        assertThat(trace.steps.drop(1).map { actTypes(it) }).containsOnly(listOf(ScheduleAct::class.java))
    }

    @Test
    fun `an active state of a step is asserted by its region and state`() {
        val trace = generateSteps(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            listOf(WitnessStateValue("root.component._Main.activeState", "root.component._Main._Idle")),
            emptyList(),
        )

        val states = trace.steps.single().asserts.filterIsInstance<StateReferenceExpression>().map {
            "${it.region.name}.${it.state.name}"
        }
        assertThat(states).containsExactly("Main.Idle")
    }

    @Test
    fun `the time advance of the traced component is an elapse of a second before the schedule`() {
        val trace = generate(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            listOf(
                raise(RAISE_INPUT_EVENT, "root.component._control", "root.component._control.interface._start"),
                TraceEntry("root", PASS_TIME, emptyList(), null),
            ),
            listOf(WitnessStateValue("root.component._Main.activeState", "root.component._Main._Idle")),
        )

        val step = trace.steps.last()
        assertThat(actTypes(step)).containsExactly(RaiseEventAct::class.java, TimeElapseAct::class.java, ScheduleAct::class.java)
        assertThat((step.acts[1] as TimeElapseAct).time).isEqualTo(1000)
    }

    @Test
    fun `a step without the time advance has no elapse`() {
        val trace = generate(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            listOf(raise(RAISE_INPUT_EVENT, "root.component._control", "root.component._control.interface._start")),
            listOf(WitnessStateValue("root.component._Main.activeState", "root.component._Main._Idle")),
        )

        assertThat(actTypes(trace.steps.last())).containsExactly(RaiseEventAct::class.java, ScheduleAct::class.java)
    }

    @Test
    fun `a generated trace asserts no unraised event`() {
        val trace = generate(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            listOf(TraceEntry("root.component._Main.transition_Idle_to_Sent", FIRE, emptyList(), null)),
            listOf(WitnessStateValue("root.component._Main.activeState", "root.component._Main._Sent")),
        )

        assertThat(trace.steps.last().asserts.filterIsInstance<RaiseEventAct>()).isEmpty()
    }

    @Test
    fun `a fired transition is asserted by its source and target`() {
        val trace = generate(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            listOf(TraceEntry("root.component._Main.transition_Idle_to_Sent", FIRE, emptyList(), null)),
            listOf(WitnessStateValue("root.component._Main.activeState", "root.component._Main._Sent")),
        )

        val fired = trace.steps.last().asserts.filterIsInstance<FiredTransitionExpression>().single()
        assertThat(listOf(fired.from.name, fired.to.name)).containsExactly("Idle", "Sent")
        assertThat(fired.trigger).isNull()
    }

    @Test
    fun `a fired transition among transitions of the same source and target names its trigger`() {
        val trace = generate(
            "Triggers.gamma",
            "TransitionOnTheSecondTriggerFires",
            listOf(TraceEntry("root.component._Main.transition_Idle_to_Active_on_control_second", FIRE, emptyList(), null)),
            listOf(WitnessStateValue("root.component._Main.activeState", "root.component._Main._Active")),
        )

        val fired = trace.steps.last().asserts.filterIsInstance<FiredTransitionExpression>().single()
        val trigger = fired.trigger as EventTrigger
        assertThat(listOf(trigger.port.name, trigger.event.name)).containsExactly("control", "second")
    }

    @Test
    fun `an integer variable is asserted equal to its value`() {
        val trace = generate(
            "Triggers.gamma",
            "TransitionOnTheSecondTriggerFires",
            emptyList(),
            listOf(WitnessStateValue("root.component._entered.value", "2")),
        )

        val comparison = trace.steps.last().asserts.filterIsInstance<ComparisonOperator>().single()
        assertThat((comparison.left as VariableReferenceExpression).variable.name).isEqualTo("entered")
        assertThat((comparison.right as LiteralInteger).value).isEqualTo(2)
    }

    @Test
    fun `a negative integer value is the minus of its magnitude`() {
        val trace = generate(
            "Triggers.gamma",
            "TransitionOnTheSecondTriggerFires",
            emptyList(),
            listOf(WitnessStateValue("root.component._entered.value", "-3")),
        )

        val comparison = trace.steps.last().asserts.filterIsInstance<ComparisonOperator>().single()
        val negated = comparison.right as ArithmeticUnaryOperator
        assertThat((negated.body as LiteralInteger).value).isEqualTo(3)
    }

    @Test
    fun `a boolean variable is asserted equal to its value`() {
        val trace = generate(
            "GuardEvaluation.gamma",
            "BeginningOfStepGuardMissesTheWriteOfItsStep",
            emptyList(),
            listOf(WitnessStateValue("root.component._written.value", "true")),
        )

        val comparison = trace.steps.last().asserts.filterIsInstance<ComparisonOperator>().single()
        assertThat((comparison.right as LiteralBoolean).isValue).isTrue()
    }

    private fun actTypes(step: Step): List<Class<*>> {
        return step.acts.map {
            it.eClass().instanceClass
        }
    }

    private fun generate(
        modelFile: String,
        caseName: String,
        traces: List<TraceEntry>,
        values: List<WitnessStateValue>,
    ): ExecutionTrace {
        return generateSteps(modelFile, caseName, emptyList(), listOf(traces to values))
    }

    private fun generateSteps(
        modelFile: String,
        caseName: String,
        initialValues: List<WitnessStateValue>,
        steps: List<Pair<List<TraceEntry>, List<WitnessStateValue>>>,
    ): ExecutionTrace {
        val gammaPackage = gammaReader.readGammaFile(SEMANTICS.resolve(modelFile).toFile())
        val nameMap = gammaCompiler.compile(gammaPackage, temporaryDirectory.resolve("model.oxsts"))
        val verificationCase = gammaPackage.verificationCases.single {
            it.name == caseName
        }
        val callTrace = CallTrace(
            CallTraceStep(emptyList()),
            steps.map {
                CallTraceStep(it.first)
            },
        )
        val witnessState = WitnessState(
            WitnessStateStep(initialValues),
            steps.map {
                WitnessStateStep(it.second)
            },
        )
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
        private const val PASS_TIME = "semantifyr::gamma::verification::GammaVerificationCase::passTime"
        private const val FIRE = "semantifyr::gamma::statecharts::Transition::fire"
    }
}
