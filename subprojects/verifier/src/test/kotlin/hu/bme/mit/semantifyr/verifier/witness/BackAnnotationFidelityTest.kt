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
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.linearInlinedWitness
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.RecordFlatteningPass
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceNames
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioLeg
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.qualifiedNameToDirectoryName
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText
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
            thetaShapedWitness(
                model,
                model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                model.state(kitchenOn to false, hallOn to true, kitchenTracer to false, hallTracer to true),
                model.state(kitchenOn to true, hallOn to true, kitchenTracer to true, hallTracer to false),
                model.state(kitchenOn to true, hallOn to false, kitchenTracer to false, hallTracer to true),
            )
        }

        val callTrace = readCallTrace(output, case)
        assertThat(callTrace.initialStep.traces).isEmpty()
        assertThat(callTrace.initializedStep?.traces).isEmpty()
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
            thetaShapedWitness(
                model,
                model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                model.state(kitchenOn to false, hallOn to false, kitchenTracer to false, hallTracer to false),
                model.state(kitchenOn to true, hallOn to false, kitchenTracer to true, hallTracer to false),
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
            thetaShapedWitness(
                model,
                model.state(slots[0] to 0, slots[1] to 0, slots[2] to 0),
                model.state(slots[0] to 0, slots[1] to 0, slots[2] to 0),
                model.state(slots[0] to 1, slots[1] to 0, slots[2] to 0),
                model.state(slots[0] to 1, slots[1] to 1, slots[2] to 0),
                model.state(slots[0] to 1, slots[1] to 1, slots[2] to 1),
            )
        }

        val witnessState = readWitnessState(output, case)
        assertThat(witnessState.initialStep.values).containsExactly(
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
            thetaShapedWitness(
                model,
                model.state(positionX to 0),
                model.state(positionX to 0),
                model.state(positionX to 1),
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
            thetaShapedWitness(
                model,
                model.state(slots[0] to secondId, slots[1] to secondId),
                model.state(slots[0] to secondId, slots[1] to secondId),
            )
        }

        assertThat(readWitnessState(output, case).initialStep.values).contains(
            WitnessStateValue("root.queue[0]", "root.second"),
            WitnessStateValue("root.queue[1]", "root.second"),
        )
        assertThat(readWitnessModel(output, case)).contains("root.queue[0] == root.second")
    }

    @Test
    suspend fun `state after init is the initialized step for a witness without a pre-init state`(@TempDir output: Path) {
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

        val witnessState = readWitnessState(output, case)
        assertThat(witnessState.initialStep.values).isEmpty()
        assertThat(witnessState.initializedStep?.values).contains(
            WitnessStateValue("root.kitchen.lamp.on", "false"),
            WitnessStateValue("root.hall.lamp.on", "false"),
        )
        val callTrace = readCallTrace(output, case)
        assertThat(callTrace.initializedStep?.traces).isEmpty()
        assertThat(callTrace.steps.single().traces.map { it.self }).containsExactly("root.kitchen.lamp")
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

    private fun thetaShapedWitness(
        model: InlinedOxsts,
        initialState: InlinedWitnessState,
        initializedState: InlinedWitnessState,
        vararg transitionStates: InlinedWitnessState,
    ): InlinedWitness {
        val sequence = listOf(initialState, initializedState) + transitionStates
        return InlinedWitness(
            initialState = initialState,
            initializedState = initializedState,
            transitionStates = transitionStates.toList(),
            nextStateMap = sequence.zipWithNext().associate { (from, to) ->
                from to listOf(to)
            },
            inlinedOxsts = model,
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
        return output.resolve(qualifiedNameToDirectoryName(case.qualifiedName))
    }

    private fun readCallTrace(output: Path, case: VerificationCase): CallTrace {
        return Json.decodeFromString(caseDirectory(output, case).resolve("trace.json").readText())
    }

    private fun readWitnessState(output: Path, case: VerificationCase): WitnessState {
        return Json.decodeFromString(caseDirectory(output, case).resolve("witness.json").readText())
    }

    private fun readWitnessModel(output: Path, case: VerificationCase): String {
        return caseDirectory(output, case).resolve("witness.oxsts").readText()
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
