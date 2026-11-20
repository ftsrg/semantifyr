/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.witness.Fraction
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
import hu.bme.mit.semantifyr.verifier.ObservedVariable
import hu.bme.mit.semantifyr.verifier.ReportCall
import hu.bme.mit.semantifyr.verifier.ReportInitialEntry
import hu.bme.mit.semantifyr.verifier.ReportState
import hu.bme.mit.semantifyr.verifier.ReportStep
import hu.bme.mit.semantifyr.verifier.ReportTrace
import hu.bme.mit.semantifyr.verifier.ReportType
import hu.bme.mit.semantifyr.verifier.ReportTypeKind
import hu.bme.mit.semantifyr.verifier.ReportValue
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
            mapOf(
                "root.component._Main.activeState" to instance("root.component._Main._Holding"),
                "root.component._measure.interface._level._value.value" to ReportValue.Integer(7),
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
            mapOf(
                "root.component._Main.activeState" to instance("root.component._Main._Paused"),
                "root.component._Main._Working._Progress.activeState" to ReportValue.Null,
                "root.component._Main._Working._Progress._ProgressHistory.lastState" to instance("root.component._Main._Working._Progress._Second"),
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
            mapOf(
                "root.component._asker._Main.activeState" to instance("root.component._asker._Main._Sent"),
                "root.component._answerer._Main.activeState" to instance("root.component._answerer._Main._Asked"),
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
    fun `the trace has a step per report step after an initialization step that resets`() {
        val trace = generateSteps(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            mapOf("root.component._Main.activeState" to instance("root.component._Main._Idle")),
            listOf(
                emptyList<ReportCall>() to mapOf("root.component._Main.activeState" to instance("root.component._Main._Idle")),
                emptyList<ReportCall>() to mapOf("root.component._Main.activeState" to instance("root.component._Main._Idle")),
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
            mapOf("root.component._Main.activeState" to instance("root.component._Main._Idle")),
            emptyList(),
        )

        val states = trace.steps.single().asserts.filterIsInstance<StateReferenceExpression>().map {
            "${it.region.name}.${it.state.name}"
        }
        assertThat(states).containsExactly("Main.Idle")
    }

    @Test
    fun `a step's elapsed time is an elapse in milliseconds of the model's unit before the schedule`() {
        val trace = generate(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            listOf(
                raise(RAISE_INPUT_EVENT, "root.component._control", "root.component._control.interface._start"),
            ),
            mapOf("root.component._Main.activeState" to instance("root.component._Main._Idle")),
            elapsed = Fraction.of(3),
            timeUnitMilliseconds = 500,
        )

        val step = trace.steps.last()
        assertThat(actTypes(step)).containsExactly(RaiseEventAct::class.java, TimeElapseAct::class.java, ScheduleAct::class.java)
        val elapse = step.acts[1] as TimeElapseAct
        assertThat(listOf(elapse.time, elapse.denominator)).containsExactly(1500, 1)
    }

    @Test
    fun `a fractional elapsed time is an elapse of exact milliseconds with a denominator`() {
        val trace = generate(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            emptyList(),
            mapOf("root.component._Main.activeState" to instance("root.component._Main._Idle")),
            elapsed = Fraction.of(1, 3),
            timeUnitMilliseconds = 1000,
        )

        val elapse = trace.steps.last().acts.filterIsInstance<TimeElapseAct>().single()
        assertThat(listOf(elapse.time, elapse.denominator)).containsExactly(1000, 3)
    }

    @Test
    fun `a step that passes no time has no elapse`() {
        val trace = generate(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            listOf(raise(RAISE_INPUT_EVENT, "root.component._control", "root.component._control.interface._start")),
            mapOf("root.component._Main.activeState" to instance("root.component._Main._Idle")),
        )

        assertThat(actTypes(trace.steps.last())).containsExactly(RaiseEventAct::class.java, ScheduleAct::class.java)
    }

    @Test
    fun `a generated trace asserts no unraised event`() {
        val trace = generate(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            listOf(ReportCall("root.component._Main.transition_Idle_to_Sent", FIRE, emptyMap(), emptyList())),
            mapOf("root.component._Main.activeState" to instance("root.component._Main._Sent")),
        )

        assertThat(trace.steps.last().asserts.filterIsInstance<RaiseEventAct>()).isEmpty()
    }

    @Test
    fun `a fired transition is asserted by its source and target`() {
        val trace = generate(
            "Events.gamma",
            "BothEventsOfAPortAreRaisedInOneStep",
            listOf(ReportCall("root.component._Main.transition_Idle_to_Sent", FIRE, emptyMap(), emptyList())),
            mapOf("root.component._Main.activeState" to instance("root.component._Main._Sent")),
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
            listOf(ReportCall("root.component._Main.transition_Idle_to_Active_on_control_second", FIRE, emptyMap(), emptyList())),
            mapOf("root.component._Main.activeState" to instance("root.component._Main._Active")),
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
            mapOf("root.component._entered.value" to ReportValue.Integer(2)),
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
            mapOf("root.component._entered.value" to ReportValue.Integer(-3)),
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
            mapOf("root.component._written.value" to ReportValue.Bool(true)),
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
        calls: List<ReportCall>,
        values: Map<String, ReportValue>,
        elapsed: Fraction = Fraction.ZERO,
        timeUnitMilliseconds: Int = 1000,
    ): ExecutionTrace {
        return generateSteps(modelFile, caseName, emptyMap(), listOf(calls to values), elapsed, timeUnitMilliseconds)
    }

    private fun generateSteps(
        modelFile: String,
        caseName: String,
        initialValues: Map<String, ReportValue>,
        steps: List<Pair<List<ReportCall>, Map<String, ReportValue>>>,
        elapsed: Fraction = Fraction.ZERO,
        timeUnitMilliseconds: Int = 1000,
    ): ExecutionTrace {
        val gammaPackage = gammaReader.readGammaFile(SEMANTICS.resolve(modelFile).toFile())
        val nameMap = gammaCompiler.compile(gammaPackage, temporaryDirectory.resolve("model.semantifyr"))
        val verificationCase = gammaPackage.verificationCases.single {
            it.name == caseName
        }
        val states = listOf(ReportState(initialValues)) + steps.map {
            ReportState(it.second)
        }
        val trace = ReportTrace(
            transitions = emptyList(),
            variables = states.flatMap {
                it.values.entries
            }.distinctBy {
                it.key
            }.map {
                ObservedVariable(it.key, typeOf(it.value))
            },
            initial = ReportInitialEntry(0, emptyList()),
            states = states,
            steps = steps.mapIndexed { index, step ->
                ReportStep(index, index + 1, elapsed, step.first)
            },
        )
        return gammaTraceGenerator.generate(verificationCase, trace, nameMap, timeUnitMilliseconds)
    }

    private fun typeOf(value: ReportValue): ReportType {
        return when (value) {
            is ReportValue.Bool -> ReportType(ReportTypeKind.Datatype, "semantifyr::bool")
            is ReportValue.Integer -> ReportType(ReportTypeKind.Datatype, "semantifyr::int")
            else -> ReportType(ReportTypeKind.Class, "semantifyr::Anything")
        }
    }

    private fun instance(path: String): ReportValue {
        return ReportValue.Text(path)
    }

    private fun raise(
        transition: String,
        port: String,
        event: String,
    ): ReportCall {
        return ReportCall(port, transition, mapOf("event" to instance(event)), emptyList())
    }

    companion object {
        private val SEMANTICS = Path("..", "models", "semantics")
        private const val RAISE_INPUT_EVENT = "semantifyr::gamma::ports::Port::raiseInputEvent"
        private const val SET_OUTPUT_EVENT = "semantifyr::gamma::ports::Port::setOutputEvent"
        private const val FIRE = "semantifyr::gamma::statecharts::Transition::fire"
    }
}
