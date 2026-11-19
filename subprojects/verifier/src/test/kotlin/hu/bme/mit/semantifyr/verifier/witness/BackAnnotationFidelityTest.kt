/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessClockValue
import hu.bme.mit.semantifyr.backend.witness.linearInlinedWitness
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.RecordFlatteningPass
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceNames
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.verifier.ObservedParameter
import hu.bme.mit.semantifyr.verifier.ObservedSlot
import hu.bme.mit.semantifyr.verifier.ObservedTransition
import hu.bme.mit.semantifyr.verifier.ObservedVariable
import hu.bme.mit.semantifyr.verifier.ReportCall
import hu.bme.mit.semantifyr.verifier.ReportInitialEntry
import hu.bme.mit.semantifyr.verifier.ReportState
import hu.bme.mit.semantifyr.verifier.ReportStep
import hu.bme.mit.semantifyr.verifier.ReportTrace
import hu.bme.mit.semantifyr.verifier.ReportType
import hu.bme.mit.semantifyr.verifier.ReportTypeKind
import hu.bme.mit.semantifyr.verifier.ReportValue
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigInteger
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.writeText
import hu.bme.mit.semantifyr.backend.witness.WitnessState as InlinedWitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue as InlinedWitnessStateValue

@InjectWithOxsts
class BackAnnotationFidelityTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var loader: SemantifyrLoader

    @Inject
    private lateinit var discoverer: VerificationCaseDiscoverer

    @Inject
    private lateinit var expressionSerializer: ExpressionSerializer

    private val tracingCase = "semantifyr::verification::lang::tracing::TracedChoiceBranchReach"
    private val arraysCase = "semantifyr::verification::lang::arrays::ArraySlotIndependenceReach"
    private val recordsCase = "semantifyr::verification::lang::records::ContainedRecordReach"
    private val recordArrayCase = "semantifyr::verification::lang::records::RecordArrayRuntimeIndexReach"
    private val classTypedArraysCase = "semantifyr::verification::lang::arrays::ClassTypedArrayDispatchReach"

    private val kitchenOn = flattenedName("root", "kitchen", "lamp", "on")
    private val hallOn = flattenedName("root", "hall", "lamp", "on")
    private val kitchenTracer = TransitionTracerNames.tracerName(0)
    private val hallTracer = TransitionTracerNames.tracerName(1)

    @Test
    fun `a step's calls list only the calls that fired in it`(@TempDir output: Path) {
        val case = loadCase("tracing.semantifyr", tracingCase)

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                model.state(kitchenOn to false, hallOn to true, kitchenTracer to false, hallTracer to true),
                model.state(kitchenOn to true, hallOn to true, kitchenTracer to true, hallTracer to false),
                model.state(kitchenOn to true, hallOn to false, kitchenTracer to false, hallTracer to true),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        val trace = annotated.trace
        assertThat(trace.initial.calls).isEmpty()
        assertThat(
            trace.steps.map { step ->
                step.calls.map {
                    it.self
                }
            },
        ).containsExactly(
            listOf("root.hall.lamp"),
            listOf("root.kitchen.lamp"),
            listOf("root.hall.lamp"),
        )
    }

    @Test
    fun `the back-annotated witness assumes each state by feature paths from the root instance`(@TempDir output: Path) {
        val case = loadCase("tracing.semantifyr", tracingCase)

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                model.state(kitchenOn to true, hallOn to false, kitchenTracer to true, hallTracer to false),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        assertThat(annotated.trace.states.last().values).contains(
            entry("root.kitchen.lamp.on", ReportValue.Bool(true)),
            entry("root.hall.lamp.on", ReportValue.Bool(false)),
        )
        assertThat(annotated.assumptions).contains("root.kitchen.lamp.on == true")
    }

    @Test
    fun `an array is reported and back-annotated slot by slot, each slot with its array, a slot the run does not show left out`(@TempDir output: Path) {
        val case = loadCase("arrays.semantifyr", arraysCase)
        val slots = (0..2).map {
            flattenedName("root", "slots_$it")
        }

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(slots[0] to 0, slots[2] to 4),
                model.state(slots[0] to 1, slots[1] to 1, slots[2] to 4),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        val trace = annotated.trace
        assertThat(trace.states[trace.initial.state].values).containsExactly(
            entry("root.slots[0]", ReportValue.Integer(BigInteger.ZERO)),
            entry("root.slots[2]", ReportValue.Integer(BigInteger.valueOf(4))),
        )
        assertThat(trace.states.last().values).containsExactly(
            entry("root.slots[0]", ReportValue.Integer(BigInteger.ONE)),
            entry("root.slots[1]", ReportValue.Integer(BigInteger.ONE)),
            entry("root.slots[2]", ReportValue.Integer(BigInteger.valueOf(4))),
        )
        assertThat(
            trace.variables.map {
                it.slot
            },
        ).containsExactly(
            ObservedSlot(array = "root.slots", index = 0, size = 3),
            ObservedSlot(array = "root.slots", index = 2, size = 3),
            ObservedSlot(array = "root.slots", index = 1, size = 3),
        )
        assertThat(annotated.assumptions).contains("root.slots[1] == 1")
    }

    @Test
    fun `record field values reach the report and the back-annotated witness by field path`(@TempDir output: Path) {
        val case = loadCase("records.semantifyr", recordsCase)
        val positionX = flattenedName("root", "robot", "position") + "${RecordFlatteningPass.FIELD_SEPARATOR}x"

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(positionX to 0),
                model.state(positionX to 1),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        assertThat(annotated.trace.states.last().values).containsExactly(
            entry("root.robot.position", ReportValue.Record(mapOf("x" to ReportValue.Integer(BigInteger.ONE)))),
        )
        assertThat(annotated.assumptions).contains("root.robot.position.x == 1")
    }

    @Test
    fun `class-typed array slots back-annotate to the instance path they hold`(@TempDir output: Path) {
        val case = loadCase("arrays.semantifyr", classTypedArraysCase)
        val slots = (0..1).map {
            flattenedName("root", "queue_$it")
        }

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val secondId = (
            model.variables.single {
                it.name == slots[1]
            }.expression as LiteralInteger
            ).value
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(slots[0] to secondId, slots[1] to secondId),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        val trace = annotated.trace
        assertThat(trace.states[trace.initial.state].values).contains(
            entry("root.queue[0]", ReportValue.Text("root.second")),
            entry("root.queue[1]", ReportValue.Text("root.second")),
        )
        assertThat(annotated.assumptions).contains("root.queue[0] == root.second")
    }

    @Test
    fun `the witness starts at the initialized step with the call of init`(@TempDir output: Path) {
        val case = loadCase("tracing.semantifyr", tracingCase)

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                model.state(kitchenOn to true, hallOn to false, kitchenTracer to true, hallTracer to false),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        val trace = annotated.trace
        assertThat(trace.states[trace.initial.state].values).contains(
            entry("root.kitchen.lamp.on", ReportValue.Bool(false)),
            entry("root.hall.lamp.on", ReportValue.Bool(false)),
        )
        val init = annotated.witness.initTransition
        assertThat(
            annotated.witness.variables.single {
                it.name == "step"
            }.expression,
        ).isInstanceOfSatisfying(LiteralInteger::class.java) {
            assertThat(it.value).isZero()
        }
        assertThat(assumptions(init)).containsSubsequence(
            "step == 0",
            "root.kitchen.lamp.on == false",
        )
        assertThat(expressionSerializer.serialize(init.eAllOfType<InlineCall>().single().callExpression)).isEqualTo("root.init()")
        assertThat(expressionSerializer.serialize(init.eAllOfType<AssignmentOperation>().single().expression)).isEqualTo("1")
    }

    @Test
    fun `the trace of a run names the root and holds the states, the steps and their calls`(@TempDir output: Path) {
        val case = loadCase("tracing.semantifyr", tracingCase)

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                model.state(kitchenOn to true, hallOn to false, kitchenTracer to true, hallTracer to false),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        val trace = annotated.trace
        assertThat(trace.root).isEqualTo("root")
        assertThat(trace.initial).isEqualTo(ReportInitialEntry(state = 0, calls = emptyList()))
        assertThat(trace.states).hasSize(2)
        assertThat(trace.states[1].values).containsEntry("root.kitchen.lamp.on", ReportValue.Bool(true))
        assertThat(trace.steps).containsExactly(
            ReportStep(
                source = 0,
                target = 1,
                elapsed = Fraction.ZERO,
                calls = listOf(
                    ReportCall(
                        self = "root.kitchen.lamp",
                        transition = "semantifyr::verification::lang::tracing::Lamp::toggle",
                        arguments = mapOf(
                            "partner" to ReportValue.Text("root.hall.lamp"),
                        ),
                        calls = emptyList(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `the report lists the observed transitions and the type of every variable a state holds`(@TempDir output: Path) {
        val case = loadCase("tracing.semantifyr", tracingCase)

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        val trace = annotated.trace
        assertThat(trace.transitions).containsExactly(
            ObservedTransition(
                transition = "semantifyr::verification::lang::tracing::Lamp::toggle",
                parameters = listOf(
                    ObservedParameter("partner", ReportType(ReportTypeKind.Class, "semantifyr::verification::lang::tracing::Lamp")),
                ),
            ),
        )
        assertThat(trace.variables).contains(
            ObservedVariable("root.kitchen.lamp.on", ReportType(ReportTypeKind.Datatype, "semantifyr::bool")),
        )
    }

    @Test
    fun `a slot of an array of records is held under the slot's key as an object of its kept fields and back-annotates field by field`(@TempDir output: Path) {
        val case = loadCase("records.semantifyr", recordArrayCase)
        val readings = flattenedName("root", "store", "readings") + RecordFlatteningPass.FIELD_SEPARATOR

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state("${readings}value_2" to 2, "${readings}valid_2" to true),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        assertThat(annotated.trace.states.single().values).containsExactly(
            entry(
                "root.store.readings[2]",
                ReportValue.Record(
                    mapOf(
                        "value" to ReportValue.Integer(BigInteger.TWO),
                        "valid" to ReportValue.Bool(true),
                    ),
                ),
            ),
        )
        assertThat(annotated.trace.variables).containsExactly(
            ObservedVariable(
                "root.store.readings[2]",
                ReportType(ReportTypeKind.Record, "semantifyr::verification::lang::records::Reading"),
                ObservedSlot(array = "root.store.readings", index = 2, size = 3),
            ),
        )
        assertThat(annotated.assumptions).contains(
            "root.store.readings[2].value == 2",
            "root.store.readings[2].valid == true",
        )
    }

    @Test
    fun `a call with a run-time argument reports the value of its last run in the step and not in the states`(@TempDir models: Path, @TempDir output: Path) {
        val case = loadText(models, "run_time_argument.semantifyr", RUN_TIME_ARGUMENT_MODEL, "semantifyr::verification::report::run_time_argument::RunTimeArgumentReach")
        val item = flattenedName("root", "port", "item")
        val tracer = TransitionTracerNames.tracerName(0)
        val argument = TransitionTracerNames.argumentName(0, "value")

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(item to 0, tracer to false, argument to 0),
                model.state(item to 2, tracer to true, argument to 2),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        assertThat(annotated.trace.steps.single().calls.single().arguments).containsExactly(
            entry("value", ReportValue.Integer(BigInteger.TWO)),
        )
        assertThat(annotated.trace.variables).noneSatisfy {
            assertThat(it.variable).contains(TransitionTracerNames.ARGUMENT_PREFIX)
        }
        assertThat(annotated.trace.states).allSatisfy { state ->
            assertThat(state.values.keys).noneSatisfy {
                assertThat(it).contains(TransitionTracerNames.ARGUMENT_PREFIX)
            }
        }
    }

    @Test
    fun `the witness model of a result with clocks assumes the scaled time and clock values after each step`(@TempDir models: Path, @TempDir output: Path) {
        val case = loadText(models, "timed.semantifyr", TIMED_MODEL, TIMED_CASE)

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val witness = timedWitness(model)
        val annotated = backAnnotate(compilation, witness)

        assertThat(annotated.witness.timeScale).isEqualTo(6)
        assertThat(
            annotated.witness.variables.map {
                it.name
            },
        ).contains("time")
        assertThat(assumptions(annotated.witness.mainTransition)).containsSubsequence(
            "step == 1",
            "time == 15",
            "root.timer.fired == true",
            "root.timer.elapsed == 15",
            "step == 2",
            "time == 17",
            "root.timer.elapsed == 17",
        )
    }

    @Test
    fun `the report names a variable and a parameter typed by a feature by the feature's qualified name`(@TempDir models: Path, @TempDir output: Path) {
        val case = loadText(models, "feature_typed.semantifyr", FEATURE_TYPED_MODEL, "semantifyr::verification::report::feature_typed::FeatureTypedPick")
        val current = flattenedName("root", "scheduler", "current")
        val tracer = TransitionTracerNames.tracerName(0)

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val firstId = (
            model.variables.single {
                it.name == current
            }.expression as LiteralInteger
            ).value
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(current to firstId, tracer to false),
                model.state(current to firstId, tracer to true),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        val trace = annotated.trace
        val jobs = ReportType(ReportTypeKind.Feature, "semantifyr::verification::report::feature_typed::Scheduler::jobs")
        assertThat(trace.variables).contains(
            ObservedVariable("root.scheduler.current", jobs),
        )
        assertThat(trace.transitions.single().parameters).containsExactly(
            ObservedParameter("job", jobs),
        )
        assertThat(trace.states.last().values).containsEntry("root.scheduler.current", ReportValue.Text("root.scheduler.first"))
        assertThat(trace.steps.single().calls.single().arguments).containsExactly(
            entry("job", ReportValue.Text("root.scheduler.second")),
        )
    }

    @Test
    fun `a model with a package-level global reports its root and derives its witness model`(@TempDir output: Path) {
        val case = loadCase("globals.semantifyr", "semantifyr::verification::lang::globals::GlobalTickReach")

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val tick = model.variables.single {
            it.name.endsWith("tick")
        }.name
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(tick to 0),
                model.state(tick to 1),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        val trace = annotated.trace
        assertThat(trace.root).isEqualTo("root")
        assertThat(trace.states.last().values.values).containsExactly(ReportValue.Integer(BigInteger.ONE))
        assertThat(annotated.assumptions).anySatisfy {
            assertThat(it).matches(".*counter\\.tick == 1")
        }
    }

    @Test
    fun `a class without features reports its root and derives its witness model`(@TempDir output: Path) {
        val case = loadCase("control_flow.semantifyr", "semantifyr::verification::lang::control_flow::ChoiceBranch1Reach")

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val x = model.variables.single {
            it.name.endsWith("x")
        }.name
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.state(x to 0),
                model.state(x to 1),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        val trace = annotated.trace
        assertThat(trace.root).isEqualTo("root")
        assertThat(trace.states.last().values).containsEntry("root.x", ReportValue.Integer(BigInteger.ONE))
        assertThat(annotated.assumptions).contains("root.x == 1")
    }

    @Test
    fun `the calls of a step are nested as they called each other and in run order`(@TempDir models: Path, @TempDir output: Path) {
        val case = loadText(models, "nested_calls.semantifyr", NESTED_CALLS_MODEL, NESTED_CALLS_CASE)

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val tracers = compilation.transitionCallTraces.tracerVariables
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.tracerState(tracers, false),
                model.tracerState(tracers, true),
            ),
        )
        val annotated = backAnnotate(compilation, witness)

        val press = annotated.trace.steps.single().calls.single()
        assertThat(press.self).isEqualTo("root.panel")
        assertThat(
            press.calls.map {
                it.self
            },
        ).containsExactly(
            "root.panel.second",
            "root.panel.first",
        )
    }

    @Test
    fun `a call that ran without the call enclosing it fails the trace`(@TempDir models: Path, @TempDir output: Path) {
        val case = loadText(models, "nested_calls.semantifyr", NESTED_CALLS_MODEL, NESTED_CALLS_CASE)

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val tracers = compilation.transitionCallTraces.tracerVariables
        val toggles = tracers.filter {
            compilation.transitionCallTraces.getTransitionCallTrace(it).transitionDeclaration.name == "toggle"
        }
        val witness = linearInlinedWitness(
            model,
            listOf(
                model.tracerState(tracers, false),
                model.tracerState(toggles, true),
            ),
        )

        assertThatThrownBy {
            backAnnotate(compilation, witness)
        }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("ran without the call that encloses it")
    }

    @Test
    fun `the witness model of a failed verdict holds the negated property and of a passed one the property itself`(@TempDir output: Path) {
        val case = loadCase("tracing.semantifyr", tracingCase)

        val compilation = compile(case, output)
        val model = compilation.inlinedOxsts
        val classWitness = injector.getInstance(ClassWitnessTransformer::class.java).transform(linearInlinedWitness(model, listOf(model.state())), compilation)
        val trace = injector.getInstance(ReportTraceTransformer::class.java).transform(classWitness, compilation)
        val backAnnotator = injector.getInstance(ClassWitnessBackAnnotator::class.java)

        val failed = backAnnotator.createWitnessInlinedOxsts(trace, compilation, VerificationVerdict.Failed)
        val passed = backAnnotator.createWitnessInlinedOxsts(trace, compilation, VerificationVerdict.Passed)

        assertThat((failed.property.expression as NegationOperator).body).isInstanceOf(CallSuffixExpression::class.java)
        assertThat(passed.property.expression).isInstanceOf(CallSuffixExpression::class.java)
    }

    @Test
    fun `a time scale beyond what a witness model can hold fails the witness model`(@TempDir models: Path, @TempDir output: Path) {
        val case = loadText(models, "timed.semantifyr", TIMED_MODEL, TIMED_CASE)
        val tiny = Fraction.of(BigInteger.ONE, BigInteger.TWO.pow(40))
        val trace = ReportTrace(
            root = "root",
            transitions = emptyList(),
            variables = listOf(
                ObservedVariable("root.timer.elapsed", ReportType(ReportTypeKind.Datatype, "semantifyr::clock")),
            ),
            initial = ReportInitialEntry(state = 0, calls = emptyList()),
            states = listOf(
                ReportState(
                    mapOf(
                        "root.timer.elapsed" to ReportValue.Integer(BigInteger.ZERO),
                    ),
                ),
                ReportState(
                    mapOf(
                        "root.timer.elapsed" to ReportValue.of(tiny),
                    ),
                ),
            ),
            steps = listOf(
                ReportStep(source = 0, target = 1, elapsed = tiny, calls = emptyList()),
            ),
        )

        val compilation = compile(case, output)

        assertThatThrownBy {
            injector.getInstance(ClassWitnessBackAnnotator::class.java).createWitnessInlinedOxsts(trace, compilation, VerificationVerdict.Passed)
        }.isInstanceOf(ArithmeticException::class.java)
    }

    private fun timedWitness(model: InlinedOxsts): InlinedWitness {
        val elapsed = model.variables.single {
            it.name == flattenedName("root", "timer", "elapsed")
        }
        val fired = flattenedName("root", "timer", "fired")
        fun timed(
            state: InlinedWitnessState,
            clock: Fraction,
            step: Fraction,
        ): InlinedWitnessState {
            return InlinedWitnessState(state.values, listOf(WitnessClockValue(elapsed, clock)), step)
        }
        return linearInlinedWitness(
            model,
            listOf(
                timed(model.state(fired to false), Fraction.ZERO, Fraction.ZERO),
                timed(model.state(fired to true), Fraction.of(5, 2), Fraction.of(5, 2)),
                timed(model.state(fired to true), Fraction.of(17, 6), Fraction.of(1, 3)),
            ),
        )
    }

    private fun loadText(
        directory: Path,
        fileName: String,
        text: String,
        qualifiedName: String,
    ): VerificationCase {
        val path = directory.resolve(fileName)
        path.writeText(text)
        val context = loader.loadStandaloneModel(path)
        return discoverer.findByQualifiedName(context, qualifiedName)
    }

    private fun flattenedName(vararg path: String): String {
        return path.joinToString("") {
            "${InstanceNames.INSTANCE_NAME_SEPARATOR}$it"
        }
    }

    private fun InlinedOxsts.state(vararg values: Pair<String, Any>): InlinedWitnessState {
        return InlinedWitnessState(
            values.map { (name, value) ->
                val variable = variables.singleOrNull {
                    it.name == name
                }
                    ?: error("No variable '$name' in the flattened model")
                val literal = when (value) {
                    is Boolean -> OxstsFactory.createLiteralBoolean(value)
                    is Int -> OxstsFactory.createLiteralInteger(value)
                    else -> error("Unsupported witness value $value")
                }
                InlinedWitnessStateValue(variable, literal)
            },
        )
    }

    private fun InlinedOxsts.tracerState(tracers: Collection<VariableDeclaration>, value: Boolean): InlinedWitnessState {
        return InlinedWitnessState(
            tracers.map {
                InlinedWitnessStateValue(it, OxstsFactory.createLiteralBoolean(value))
            },
        )
    }

    private fun loadCase(fileName: String, qualifiedName: String): VerificationCase {
        val context = loader.loadStandaloneModel(Path("../../oxsts-test-models/semantic/$fileName"))
        return discoverer.findByQualifiedName(context, qualifiedName)
    }

    private fun compile(case: VerificationCase, output: Path): FlattenedCompilationContext {
        return SemantifyrCompiler(injector, ArtifactConfig.NONE, OptimizationConfig.NONE).compile(case.classDeclaration, output)
    }

    private fun backAnnotate(
        compilation: FlattenedCompilationContext,
        witness: InlinedWitness,
    ): BackAnnotated {
        val classWitness = injector.getInstance(ClassWitnessTransformer::class.java).transform(witness, compilation)
        val trace = injector.getInstance(ReportTraceTransformer::class.java).transform(classWitness, compilation)
        val model = injector.getInstance(ClassWitnessBackAnnotator::class.java).createWitnessInlinedOxsts(trace, compilation, VerificationVerdict.Passed)
        return BackAnnotated(trace, model)
    }

    private fun assumptions(transition: TransitionDeclaration): List<String> {
        return transition.eAllOfType<AssumptionOperation>().map {
            expressionSerializer.serialize(it.expression)
        }.toList()
    }

    private inner class BackAnnotated(
        val trace: ReportTrace,
        val witness: InlinedOxsts,
    ) {
        val assumptions: List<String>
            get() = assumptions(witness.initTransition) + assumptions(witness.mainTransition)
    }

    companion object {
        private const val TIMED_CASE = "semantifyr::verification::report::timed::TimerFires"
        private const val NESTED_CALLS_CASE = "semantifyr::verification::report::nested_calls::PanelPress"

        private val NESTED_CALLS_MODEL = """
            package semantifyr::verification::report::nested_calls

            class Lamp {
                var on: bool := false

                @Trace
                tran toggle() {
                    on := !on
                }
            }

            class Panel {
                contains first: Lamp
                contains second: Lamp

                @Trace
                tran press() {
                    inline second.toggle()
                    inline first.toggle()
                }
            }

            @VerificationCase
            class PanelPress {
                contains panel: Panel

                redefine tran {
                    inline panel.press()
                }

                prop {
                    return EF (panel.first.on)
                }
            }
        """.trimIndent()

        private val RUN_TIME_ARGUMENT_MODEL = """
            package semantifyr::verification::report::run_time_argument

            class Port {
                var item: int := 0

                @Trace
                tran send(value: int) {
                    item := value
                }
            }

            @VerificationCase
            class RunTimeArgumentReach {
                contains port: Port
                var count: int := 0

                redefine tran {
                    choice {
                        count := 1
                    } or {
                        count := 2
                    }
                    inline port.send(count)
                }

                prop {
                    return EF (port.item == 2)
                }
            }
        """.trimIndent()

        private val FEATURE_TYPED_MODEL = """
            package semantifyr::verification::report::feature_typed

            class Job {
                var done: bool := false
            }

            class Scheduler {
                contains jobs: Job[0..*]
                contains first: Job subsets jobs
                contains second: Job subsets jobs
                var current: jobs := first

                @Trace
                tran pick(job: jobs) {
                    current := job
                }
            }

            @VerificationCase
            class FeatureTypedPick {
                contains scheduler: Scheduler

                redefine tran {
                    inline scheduler.pick(scheduler.second)
                }

                prop {
                    return EF (scheduler.current == scheduler.second)
                }
            }
        """.trimIndent()

        private val TIMED_MODEL = """
            package semantifyr::verification::report::timed

            class Timer {
                var elapsed: clock := 0
                var fired: bool := false

                tran fire() {
                    assume(elapsed >= 2)
                    fired := true
                }
            }

            @VerificationCase
            class TimerFires {
                contains timer: Timer

                redefine tran {
                    inline timer.fire()
                }

                prop {
                    return EF (timer.fired)
                }
            }
        """.trimIndent()
    }
}
