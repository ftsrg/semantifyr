/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import hu.bme.mit.semantifyr.frontends.gamma.CompiledGammaModel
import hu.bme.mit.semantifyr.frontends.gamma.GammaCompiler
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ExecutionTrace
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaRoot
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
import hu.bme.mit.semantifyr.frontends.gamma.trace.GammaTraceGenerator
import hu.bme.mit.semantifyr.verifier.ReportCall
import hu.bme.mit.semantifyr.verifier.ReportTrace
import hu.bme.mit.semantifyr.verifier.VerificationReport
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
    fun `the reference trace validates against the crossroads model`() {
        val (_, reference) = read(CROSSROADS_CASES, REFERENCE_TRACE)

        assertThat((reference as ExecutionTrace).steps).hasSize(4)
    }

    @Test
    fun `the reference trace round-trips through parse and serialize`() {
        val (_, reference) = read(CROSSROADS_CASES, REFERENCE_TRACE)
        val serializedTrace = temporaryDirectory.resolve("trace.get")
        serializedTrace.writeText(serializer.serialize(reference, SaveOptions.newBuilder().format().options))

        val (_, original, reparsed) = read(CROSSROADS_CASES, REFERENCE_TRACE, serializedTrace)

        assertThat(EcoreUtil.equals(original, reparsed)).isTrue()
    }

    @Test
    fun `the trace generated from the reference report serializes to the reference trace`() {
        val (verificationCase, compiledModel) = compileReferenceCase()

        val generated = generate(verificationCase, referenceTrace(), compiledModel)
        val generatedText = serializer.serialize(generated, SaveOptions.newBuilder().format().options)
        val generatedTrace = temporaryDirectory.resolve("trace.get")
        generatedTrace.writeText(generatedText)

        val (_, reference, reparsed) = read(CROSSROADS_CASES, REFERENCE_TRACE, generatedTrace)

        assertThat(EcoreUtil.equals(reference, reparsed)).isTrue()
        assertThat(generatedText).isEqualTo(REFERENCE_TRACE.readText().replace("\r\n", "\n").substringAfter("*/").trimStart())
    }

    @Test
    fun `the called transition decides whether a traced raise is an act or an assertion`() {
        val (verificationCase, compiledModel) = compileReferenceCase()
        val trace = referenceTrace().withCalledTransition(RAISE_INPUT_EVENT, SET_OUTPUT_EVENT)

        val generated = generate(verificationCase, trace, compiledModel)

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
    fun `an output an inner instance raises is asserted on that instance's port`() {
        val (verificationCase, compiledModel) = compileReferenceCase()

        val generated = generate(verificationCase, referenceTrace(), compiledModel)

        val raises = generated.steps.flatMap {
            it.asserts
        }.filterIsInstance<RaiseEventAct>()
        assertThat(
            raises.map {
                listOf(it.instance?.instance?.name, it.port.name, it.event.name)
            },
        ).contains(listOf("controller", "controlA", "toggle"))
    }

    @Test
    fun `a traced port call that raises no event is rejected`() {
        val (verificationCase, compiledModel) = compileReferenceCase()
        val trace = referenceTrace().withCalledTransition(RAISE_INPUT_EVENT, "semantifyr::gamma::ports::Port::setInputEvent")

        assertThatThrownBy {
            generate(verificationCase, trace, compiledModel)
        }.hasMessageContaining("is not an event raise")
    }

    private fun compileReferenceCase(): CompiledReferenceCase {
        val crossroadsCases = read(CROSSROADS_CASES).single() as GammaModelPackage
        val compiledModel = gammaCompiler.compileWithImports(crossroadsCases, temporaryDirectory)
        val verificationCase = crossroadsCases.verificationCases.single {
            it.name == "ACanBeGreen"
        }
        return CompiledReferenceCase(verificationCase, compiledModel)
    }

    private fun generate(
        verificationCase: VerificationCaseDeclaration,
        trace: ReportTrace,
        compiledModel: CompiledGammaModel,
    ): ExecutionTrace {
        return gammaTraceGenerator.generate(verificationCase, trace, compiledModel.nameMap, compiledModel.timeUnitMilliseconds)
    }

    private fun referenceTrace(): ReportTrace {
        return requireNotNull(Json.decodeFromString<VerificationReport>(REFERENCE_REPORT.readText()).trace) {
            "The reference report has no trace"
        }
    }

    private fun ReportTrace.withCalledTransition(from: String, to: String): ReportTrace {
        fun ReportCall.replaced(): ReportCall {
            return copy(
                transition = transition.takeUnless {
                    it == from
                } ?: to,
                calls = calls.map {
                    it.replaced()
                },
            )
        }
        return copy(
            initial = initial.copy(
                calls = initial.calls.map {
                    it.replaced()
                },
            ),
            steps = steps.map { step ->
                step.copy(
                    calls = step.calls.map {
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
        val compiledModel: CompiledGammaModel,
    )

    companion object {
        private val CROSSROADS_CASES = Path("build", "test-models", "CrossroadsFurtherCases.gamma")
        private val PAIR = Path("..", "models", "etc")
        private val REFERENCE_TRACE = PAIR.resolve("crossroads-ACanBeGreen.trace.get")
        private val REFERENCE_REPORT = PAIR.resolve("crossroads-ACanBeGreen.report.json")
        private const val RAISE_INPUT_EVENT = "semantifyr::gamma::ports::Port::raiseInputEvent"
        private const val SET_OUTPUT_EVENT = "semantifyr::gamma::ports::Port::setOutputEvent"
    }
}
