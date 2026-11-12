/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import hu.bme.mit.semantifyr.frontends.gamma.GammaCompiler
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ExecutionTrace
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaRoot
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaNameStore
import hu.bme.mit.semantifyr.frontends.gamma.trace.GammaTraceGenerator
import hu.bme.mit.semantifyr.verifier.witness.CallTrace
import hu.bme.mit.semantifyr.verifier.witness.TraceEntry
import hu.bme.mit.semantifyr.verifier.witness.WitnessState
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

@InjectWithGamma
class GammaReferenceTraceTest {

    @Inject
    private lateinit var gammaReader: GammaReader

    @Inject
    private lateinit var gammaCompiler: GammaCompiler

    @Inject
    private lateinit var gammaTraceGenerator: GammaTraceGenerator

    @Inject
    private lateinit var serializer: ISerializer

    @TempDir
    private lateinit var temporaryDirectory: Path

    @Test
    fun `the hand-written reference trace validates against the crossroads model`() {
        val (_, reference) = read(CROSSROADS, REFERENCE_TRACE)

        assertThat((reference as ExecutionTrace).steps).hasSize(5)
    }

    @Test
    fun `the reference trace round-trips through parse and serialize`() {
        val (_, reference) = read(CROSSROADS, REFERENCE_TRACE)
        val serializedTrace = temporaryDirectory.resolve("trace.get")
        serializedTrace.writeText(serializer.serialize(reference, SaveOptions.newBuilder().format().options))

        val (_, original, reparsed) = read(CROSSROADS, REFERENCE_TRACE, serializedTrace)

        assertThat(EcoreUtil.equals(original, reparsed)).isTrue()
    }

    @Test
    fun `the trace generated from the reference trace json serializes to the hand-written trace`() {
        val (verificationCase, nameStore) = compileReferenceCase()

        val generated = gammaTraceGenerator.generate(verificationCase, referenceCallTrace(), referenceWitnessState(), nameStore)
        val generatedText = serializer.serialize(generated, SaveOptions.newBuilder().format().options)
        val generatedTrace = temporaryDirectory.resolve("trace.get")
        generatedTrace.writeText(generatedText)

        val (_, reference, reparsed) = read(CROSSROADS, REFERENCE_TRACE, generatedTrace)

        assertThat(EcoreUtil.equals(reference, reparsed)).isTrue()
        assertThat(generatedText).isEqualTo(REFERENCE_TRACE.readText().replace("\r\n", "\n").substringAfter("*/").trimStart())
    }

    @Test
    fun `the called transition decides whether a traced raise is an act or an assertion`() {
        val (verificationCase, nameStore) = compileReferenceCase()
        val callTrace = referenceCallTrace().withCalledTransition(RAISE_INPUT_EVENT, SET_OUTPUT_EVENT)

        val generated = gammaTraceGenerator.generate(verificationCase, callTrace, referenceWitnessState(), nameStore)

        val lastStep = generated.steps.last()
        assertThat(lastStep.acts).noneMatch {
            it is RaiseEventAct
        }
        assertThat(
            lastStep.asserts.filterIsInstance<RaiseEventAct>().map {
                listOf(it.port.name, it.event.name)
            },
        ).contains(listOf("control", "toggle"))
    }

    @Test
    fun `a traced port call that raises no event is rejected`() {
        val (verificationCase, nameStore) = compileReferenceCase()
        val callTrace = referenceCallTrace().withCalledTransition(RAISE_INPUT_EVENT, "semantifyr::gamma::ports::Port::setInputEvent")

        assertThatThrownBy {
            gammaTraceGenerator.generate(verificationCase, callTrace, referenceWitnessState(), nameStore)
        }.hasMessageContaining("is not an event raise")
    }

    private fun compileReferenceCase(): CompiledReferenceCase {
        val crossroads = read(CROSSROADS).single() as GammaModelPackage
        val nameStore = gammaCompiler.compile(crossroads, temporaryDirectory.resolve("model.oxsts"))
        val verificationCase = crossroads.verificationCases.single {
            it.name == "ACanBeGreen"
        }
        return CompiledReferenceCase(verificationCase, nameStore)
    }

    private fun referenceCallTrace(): CallTrace {
        return Json.decodeFromString<CallTrace>(REFERENCE_TRACE_JSON.readText())
    }

    private fun referenceWitnessState(): WitnessState {
        return Json.decodeFromString<WitnessState>(REFERENCE_WITNESS_JSON.readText())
    }

    private fun CallTrace.withCalledTransition(from: String, to: String): CallTrace {
        fun TraceEntry.replaced(): TraceEntry {
            return copy(
                calledTransition = calledTransition.takeUnless { it == from } ?: to,
                innerTraces = innerTraces?.map {
                    it.replaced()
                },
            )
        }
        return copy(
            steps = steps.map { step ->
                step.copy(
                    traces = step.traces.map {
                        it.replaced()
                    },
                )
            },
        )
    }

    private fun read(vararg paths: Path): List<GammaRoot> {
        return gammaReader.readGammaFiles(
            paths.map {
                it.toFile()
            },
        )
    }

    private data class CompiledReferenceCase(
        val verificationCase: VerificationCaseDeclaration,
        val nameStore: GammaNameStore,
    )

    companion object {
        private val CROSSROADS = Path("build", "test-models", "Crossroads.gamma")
        private val PAIR = Path("..", "models", "etc")
        private val REFERENCE_TRACE = PAIR.resolve("crossroads-ACanBeGreen.trace.get")
        private val REFERENCE_TRACE_JSON = PAIR.resolve("crossroads-ACanBeGreen.trace.json")
        private val REFERENCE_WITNESS_JSON = PAIR.resolve("crossroads-ACanBeGreen.witness.json")
        private const val RAISE_INPUT_EVENT = "semantifyr::gamma::ports::Port::raiseInputEvent"
        private const val SET_OUTPUT_EVENT = "semantifyr::gamma::ports::Port::setOutputEvent"
    }
}
