/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessClockValue
import hu.bme.mit.semantifyr.backend.witness.linearInlinedWitness
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.RecordFlatteningPass
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceNames
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.verifier.ObservedParameter
import hu.bme.mit.semantifyr.verifier.ObservedSlot
import hu.bme.mit.semantifyr.verifier.ObservedTransition
import hu.bme.mit.semantifyr.verifier.ObservedVariable
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.REPORT_VERSION
import hu.bme.mit.semantifyr.verifier.ReportCall
import hu.bme.mit.semantifyr.verifier.ReportInitialEntry
import hu.bme.mit.semantifyr.verifier.ReportKind
import hu.bme.mit.semantifyr.verifier.ReportStep
import hu.bme.mit.semantifyr.verifier.ReportType
import hu.bme.mit.semantifyr.verifier.ReportTypeKind
import hu.bme.mit.semantifyr.verifier.ReportValue
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.VerificationReport
import hu.bme.mit.semantifyr.verifier.caseDirectoryName
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioLeg
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigInteger
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
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
    private lateinit var serializer: ExpressionSerializer

    private val tracingCase = "semantifyr::verification::lang::tracing::TracedChoiceBranchReach"
    private val arraysCase = "semantifyr::verification::lang::arrays::ArraySlotIndependenceReach"
    private val recordsCase = "semantifyr::verification::lang::records::ContainedRecordReach"
    private val classTypedArraysCase = "semantifyr::verification::lang::arrays::ClassTypedArrayDispatchReach"

    private val kitchenOn = flattenedName("root", "kitchen", "lamp", "on")
    private val hallOn = flattenedName("root", "hall", "lamp", "on")
    private val kitchenTracer = TransitionTracerNames.tracerName(0)
    private val hallTracer = TransitionTracerNames.tracerName(1)

    @Test
    suspend fun `call trace lists only the calls that fired in each step`(@TempDir output: Path) {
        val case = loadCase("tracing.oxsts", tracingCase)

        verifyWithWitness(case, output) { model ->
            linearInlinedWitness(
                model,
                listOf(
                    model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                    model.state(kitchenOn to false, hallOn to true, kitchenTracer to false, hallTracer to true),
                    model.state(kitchenOn to true, hallOn to true, kitchenTracer to true, hallTracer to false),
                    model.state(kitchenOn to true, hallOn to false, kitchenTracer to false, hallTracer to true),
                ),
            )
        }

        val callTrace = readCallTrace(output, case)
        assertThat(callTrace.initializedStep.traces).isEmpty()
        assertThat(
            callTrace.steps.map { step ->
                step.traces.map { it.self }
            },
        ).containsExactly(
            listOf("root.hall.lamp"),
            listOf("root.kitchen.lamp"),
            listOf("root.hall.lamp"),
        )
    }

    @Test
    suspend fun `trace receivers and arguments are feature paths from the root instance`(@TempDir output: Path) {
        val case = loadCase("tracing.oxsts", tracingCase)

        verifyWithWitness(case, output) { model ->
            linearInlinedWitness(
                model,
                listOf(
                    model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                    model.state(kitchenOn to true, hallOn to false, kitchenTracer to true, hallTracer to false),
                ),
            )
        }

        assertThat(readCallTrace(output, case).steps.single().traces).containsExactly(
            TraceEntry(
                self = "root.kitchen.lamp",
                calledTransition = "semantifyr::verification::lang::tracing::Lamp::toggle",
                arguments = listOf(TraceArgument("partner", "root.hall.lamp")),
                innerTraces = null,
            ),
        )
        assertThat(readWitnessState(output, case).steps.single().values).contains(
            WitnessStateValue("root.kitchen.lamp.on", "true"),
            WitnessStateValue("root.hall.lamp.on", "false"),
        )
        assertThat(readWitnessModel(output, case)).contains("root.kitchen.lamp.on == true")
    }

    @Test
    suspend fun `array slot values reach the witness state and the back-annotated witness`(@TempDir output: Path) {
        val case = loadCase("arrays.oxsts", arraysCase)
        val slots = (0..2).map {
            flattenedName("root", "slots_$it")
        }

        verifyWithWitness(case, output) { model ->
            linearInlinedWitness(
                model,
                listOf(
                    model.state(slots[0] to 0, slots[1] to 0, slots[2] to 0),
                    model.state(slots[0] to 1, slots[1] to 0, slots[2] to 0),
                    model.state(slots[0] to 1, slots[1] to 1, slots[2] to 0),
                    model.state(slots[0] to 1, slots[1] to 1, slots[2] to 1),
                ),
            )
        }

        val witnessState = readWitnessState(output, case)
        assertThat(witnessState.initializedStep.values).containsExactly(
            WitnessStateValue("root.slots[0]", "0"),
            WitnessStateValue("root.slots[1]", "0"),
            WitnessStateValue("root.slots[2]", "0"),
        )
        assertThat(witnessState.steps.last().values).containsExactly(
            WitnessStateValue("root.slots[0]", "1"),
            WitnessStateValue("root.slots[1]", "1"),
            WitnessStateValue("root.slots[2]", "1"),
        )
        assertThat(readWitnessModel(output, case)).contains("root.slots[2] == 1")
    }

    @Test
    suspend fun `record field values reach the witness state and the back-annotated witness by field path`(@TempDir output: Path) {
        val case = loadCase("records.oxsts", recordsCase)
        val positionX = flattenedName("root", "robot", "position") + "${RecordFlatteningPass.FIELD_SEPARATOR}x"

        verifyWithWitness(case, output) { model ->
            linearInlinedWitness(
                model,
                listOf(
                    model.state(positionX to 0),
                    model.state(positionX to 1),
                ),
            )
        }

        assertThat(readWitnessState(output, case).steps.single().values).containsExactly(
            WitnessStateValue("root.robot.position.x", "1"),
        )
        assertThat(readWitnessModel(output, case)).contains("root.robot.position.x == 1")
    }

    @Test
    suspend fun `class-typed array slots back-annotate to the instance path they hold`(@TempDir output: Path) {
        val case = loadCase("arrays.oxsts", classTypedArraysCase)
        val slots = (0..1).map {
            flattenedName("root", "queue_$it")
        }

        verifyWithWitness(case, output) { model ->
            val secondId = (model.variables.single { it.name == slots[1] }.expression as LiteralInteger).value
            linearInlinedWitness(
                model,
                listOf(
                    model.state(slots[0] to secondId, slots[1] to secondId),
                ),
            )
        }

        assertThat(readWitnessState(output, case).initializedStep.values).contains(
            WitnessStateValue("root.queue[0]", "root.second"),
            WitnessStateValue("root.queue[1]", "root.second"),
        )
        assertThat(readWitnessModel(output, case)).contains("root.queue[0] == root.second")
    }

    @Test
    suspend fun `the witness starts at the initialized step with the call of init`(@TempDir output: Path) {
        val case = loadCase("tracing.oxsts", tracingCase)

        verifyWithWitness(case, output) { model ->
            linearInlinedWitness(
                model,
                listOf(
                    model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                    model.state(kitchenOn to true, hallOn to false, kitchenTracer to true, hallTracer to false),
                ),
            )
        }

        assertThat(readWitnessState(output, case).initializedStep.values).contains(
            WitnessStateValue("root.kitchen.lamp.on", "false"),
            WitnessStateValue("root.hall.lamp.on", "false"),
        )
        assertThat(readWitnessModel(output, case))
            .contains("var step: int := 0")
            .doesNotContain("step == -1")
            .containsSubsequence(
                "init {",
                "assume(step == 0)",
                "inline root.init()",
                "assume(root.kitchen.lamp.on == false)",
                "step := 1",
                "}",
            )
    }

    @Test
    suspend fun `the report of a run names the root and holds the states, the steps and their calls`(@TempDir output: Path) {
        val case = loadCase("tracing.oxsts", tracingCase)

        verifyWithWitness(case, output) { model ->
            linearInlinedWitness(
                model,
                listOf(
                    model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                    model.state(kitchenOn to true, hallOn to false, kitchenTracer to true, hallTracer to false),
                ),
            )
        }

        val report = readReport(output, case)
        val trace = report.trace!!
        assertThat(report.version).isEqualTo(REPORT_VERSION)
        assertThat(report.kind).isEqualTo(ReportKind.Run)
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
    suspend fun `the report lists the observed transitions and the type of every variable a state holds`(@TempDir output: Path) {
        val case = loadCase("tracing.oxsts", tracingCase)

        verifyWithWitness(case, output) { model ->
            linearInlinedWitness(
                model,
                listOf(
                    model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                ),
            )
        }

        val trace = readReport(output, case).trace!!
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
    suspend fun `the report holds an array slot by slot with the array it belongs to`(@TempDir output: Path) {
        val case = loadCase("arrays.oxsts", arraysCase)
        val slots = (0..2).map {
            flattenedName("root", "slots_$it")
        }

        verifyWithWitness(case, output) { model ->
            linearInlinedWitness(
                model,
                listOf(
                    model.state(slots[0] to 0, slots[2] to 4),
                ),
            )
        }

        val trace = readReport(output, case).trace!!
        assertThat(trace.states.single().values).containsExactly(
            entry("root.slots[0]", ReportValue.Integer(BigInteger.ZERO)),
            entry("root.slots[2]", ReportValue.Integer(BigInteger.valueOf(4))),
        )
        assertThat(trace.variables.last().slot).isEqualTo(ObservedSlot(array = "root.slots", index = 2, size = 3))
    }

    @Test
    suspend fun `the report holds a record as an object of its kept fields`(@TempDir output: Path) {
        val case = loadCase("records.oxsts", recordsCase)
        val positionX = flattenedName("root", "robot", "position") + "${RecordFlatteningPass.FIELD_SEPARATOR}x"

        verifyWithWitness(case, output) { model ->
            linearInlinedWitness(
                model,
                listOf(
                    model.state(positionX to 3),
                ),
            )
        }

        val trace = readReport(output, case).trace!!
        assertThat(trace.states.single().values["root.robot.position"]).isEqualTo(ReportValue.Record(mapOf("x" to ReportValue.Integer(BigInteger.valueOf(3)))))
        assertThat(trace.variables.single().type.kind).isEqualTo(ReportTypeKind.Record)
    }

    @Test
    suspend fun `an instance reference in the report is a path from the root`(@TempDir output: Path) {
        val case = loadCase("arrays.oxsts", classTypedArraysCase)
        val slots = (0..1).map {
            flattenedName("root", "queue_$it")
        }

        verifyWithWitness(case, output) { model ->
            val secondId = (model.variables.single { it.name == slots[1] }.expression as LiteralInteger).value
            linearInlinedWitness(
                model,
                listOf(
                    model.state(slots[0] to secondId),
                ),
            )
        }

        assertThat(readReport(output, case).trace!!.states.single().values).containsEntry("root.queue[0]", ReportValue.Text("root.second"))
    }

    @Test
    suspend fun `a call with a run-time argument reports the value of its last run in the step`(@TempDir models: Path, @TempDir output: Path) {
        val case = loadText(models, "run_time_argument.oxsts", RUN_TIME_ARGUMENT_MODEL, "semantifyr::verification::report::run_time_argument::RunTimeArgumentReach")
        val item = flattenedName("root", "port", "item")
        val tracer = TransitionTracerNames.tracerName(0)
        val argument = TransitionTracerNames.argumentName(0, "value")

        verifyWithWitness(case, output) { model ->
            linearInlinedWitness(
                model,
                listOf(
                    model.state(item to 0, tracer to false, argument to 0),
                    model.state(item to 2, tracer to true, argument to 2),
                ),
            )
        }

        assertThat(readReport(output, case).trace!!.steps.single().calls.single().arguments).containsExactly(
            entry("value", ReportValue.Integer(BigInteger.TWO)),
        )
    }

    @Test
    suspend fun `the witness model of a result with clocks assumes the scaled time and clock values after each step`(@TempDir models: Path, @TempDir output: Path) {
        val case = loadText(models, "timed.oxsts", TIMED_MODEL, TIMED_CASE)

        verifyWithWitness(case, output) { model ->
            timedWitness(model)
        }

        assertThat(readWitnessModel(output, case))
            .containsPattern("witness of .*TimerFires timescale 6")
            .contains("var time: clock := 0")
            .containsSubsequence(
                "assume(step == 1)",
                "inline root.main()",
                "assume(time == 15)",
                "assume(root.timer.fired == true)",
                "assume(root.timer.elapsed == 15)",
                "step := 2",
                "assume(step == 2)",
                "inline root.main()",
                "assume(time == 17)",
                "assume(root.timer.elapsed == 17)",
            )
    }

    @Test
    suspend fun `the scaled witness model reparses, validates and compiles with the model's clock bounds counting the scale`(@TempDir models: Path, @TempDir output: Path) {
        val modelPath = models.resolve("timed.oxsts")
        val case = loadText(models, "timed.oxsts", TIMED_MODEL, TIMED_CASE)

        verifyWithWitness(case, output) { model ->
            timedWitness(model)
        }

        val reparsed = loader.startContext()
            .loadModel(modelPath)
            .loadModel(caseDirectory(output, case).resolve("witness.oxsts"))
            .buildAndResolve()
        val witness = reparsed.modelResources.last().contents.filterIsInstance<InlinedOxsts>().single()
        val compiled = SemantifyrCompiler(injector, ArtifactConfig.NONE, OptimizationConfig.NONE).compile(witness, models.resolve("recheck"))
        val clockTests = compiled.inlinedOxsts.eAllOfType<ComparisonOperator>().map {
            serializer.serialize(it).trim()
        }.toList()

        assertThat(clockTests).anySatisfy {
            assertThat(it).endsWith(">= 2 * 6")
        }
        assertThat(clockTests).anySatisfy {
            assertThat(it).isEqualTo("time == 15")
        }
    }

    @Test
    suspend fun `the report names a variable and a parameter typed by a feature by the feature's qualified name`(@TempDir models: Path, @TempDir output: Path) {
        val case = loadText(models, "feature_typed.oxsts", FEATURE_TYPED_MODEL, "semantifyr::verification::report::feature_typed::FeatureTypedPick")
        val current = flattenedName("root", "scheduler", "current")
        val tracer = TransitionTracerNames.tracerName(0)

        verifyWithWitness(case, output) { model ->
            val firstId = (model.variables.single { it.name == current }.expression as LiteralInteger).value
            linearInlinedWitness(
                model,
                listOf(
                    model.state(current to firstId, tracer to false),
                    model.state(current to firstId, tracer to true),
                ),
            )
        }

        val trace = readReport(output, case).trace!!
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
    suspend fun `a model with a package-level global reports its root and derives its witness model`(@TempDir output: Path) {
        val case = loadCase("globals.oxsts", "semantifyr::verification::lang::globals::GlobalTickReach")

        verifyWithWitness(case, output) { model ->
            val tick = model.variables.single {
                it.name.endsWith("tick")
            }.name
            linearInlinedWitness(
                model,
                listOf(
                    model.state(tick to 0),
                    model.state(tick to 1),
                ),
            )
        }

        val trace = readReport(output, case).trace!!
        assertThat(trace.root).isEqualTo("root")
        assertThat(trace.states.last().values.values).containsExactly(ReportValue.Integer(BigInteger.ONE))
        assertThat(readWitnessModel(output, case)).containsPattern("assume\\(.*counter\\.tick == 1\\)")
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
                val variable = variables.singleOrNull { it.name == name }
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

    private fun loadCase(fileName: String, qualifiedName: String): VerificationCase {
        val context = loader.loadStandaloneModel(Path("../../oxsts-test-models/semantic/$fileName"))
        return discoverer.findByQualifiedName(context, qualifiedName)
    }

    private suspend fun verifyWithWitness(
        case: VerificationCase,
        output: Path,
        witness: (InlinedOxsts) -> InlinedWitness,
    ) {
        val result = SemantifyrVerifier.builder()
            .injector(injector)
            .portfolio(WitnessPortfolio(witness))
            .artifacts(ArtifactConfig.NONE)
            .outputDirectory(output)
            .timeout(30.seconds)
            .optimization(OptimizationConfig.NONE)
            .build()
            .verify(case)

        assertThat(result.trace)
            .describedAs("back-annotation of ${case.qualifiedName} failed: ${result.message ?: "no message"}")
            .isNotNull()
    }

    private fun caseDirectory(output: Path, case: VerificationCase): Path {
        return output.resolve(caseDirectoryName(case))
    }

    private fun readCallTrace(output: Path, case: VerificationCase): CallTrace {
        return Json.decodeFromString(caseDirectory(output, case).resolve("trace.json").readText())
    }

    private fun readWitnessState(output: Path, case: VerificationCase): WitnessState {
        return Json.decodeFromString(caseDirectory(output, case).resolve("witness.json").readText())
    }

    private fun readReport(output: Path, case: VerificationCase): VerificationReport {
        return Json.decodeFromString(caseDirectory(output, case).resolve("report.json").readText())
    }

    private fun readWitnessModel(output: Path, case: VerificationCase): String {
        return caseDirectory(output, case).resolve("witness.oxsts").readText()
    }

    companion object {
        private const val TIMED_CASE = "semantifyr::verification::report::timed::TimerFires"

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

    private class WitnessPortfolio(
        private val witness: (InlinedOxsts) -> InlinedWitness,
    ) : VerificationPortfolio() {
        override val id = "witness"
        override val displayName = "witness"
        override val description = "test-only"
        override val executorKeys: Set<ExecutorKey<*>> = emptySet()
        override val legs: List<PortfolioLeg> = emptyList()

        override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
            return AvailabilityReport.Available
        }

        override suspend fun verify(
            parentInjector: Injector,
            request: BackendVerificationRequest,
            gate: ConcurrencyGate,
            environment: ExecutionEnvironment,
            progress: ProgressContext,
        ): BackendVerificationResult {
            return BackendVerificationResult(
                verdict = VerificationVerdict.Passed,
                metadata = VerificationMetadata(backendId = id, startedAt = Clock.System.now()),
                inlinedWitness = witness(request.inlinedOxsts),
            )
        }
    }
}
