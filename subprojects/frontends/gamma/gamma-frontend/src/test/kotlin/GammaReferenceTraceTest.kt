/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import hu.bme.mit.semantifyr.frontends.gamma.GammaCompiler
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
import hu.bme.mit.semantifyr.frontends.gamma.trace.GammaTraceGenerator
import hu.bme.mit.semantifyr.frontends.gamma.trace.GammaTraceSerializer
import hu.bme.mit.semantifyr.verifier.witness.CallTrace
import hu.bme.mit.semantifyr.verifier.witness.WitnessState
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
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
    private lateinit var gammaTraceSerializer: GammaTraceSerializer

    @Inject
    private lateinit var serializer: ISerializer

    @TempDir
    private lateinit var temporaryDirectory: Path

    @Test
    fun `the hand-written reference trace validates against the crossroads model`() {
        val (_, reference) = read(CROSSROADS, REFERENCE_TRACE)

        assertThat(reference.traces.single().steps).hasSize(6)
    }

    @Test
    fun `the reference trace round-trips through parse and serialize`() {
        val (_, reference) = read(CROSSROADS, REFERENCE_TRACE)
        val serializedTrace = temporaryDirectory.resolve("trace.gamma")
        serializedTrace.writeText(serializer.serialize(reference, SaveOptions.newBuilder().format().options))

        val (_, original, reparsed) = read(CROSSROADS, REFERENCE_TRACE, serializedTrace)

        assertThat(EcoreUtil.equals(original, reparsed)).isTrue()
    }

    @Test
    fun `the trace generated from the reference trace json equals the hand-written trace`() {
        val crossroads = read(CROSSROADS).single()
        val nameStore = gammaCompiler.compile(crossroads, temporaryDirectory.resolve("model.oxsts"))
        val callTrace = Json.decodeFromString<CallTrace>(REFERENCE_TRACE_JSON.readText())
        val witnessState = Json.decodeFromString<WitnessState>(REFERENCE_WITNESS_JSON.readText())
        val verificationCase = crossroads.verificationCases.single {
            it.name == "ACanBeGreen"
        }

        val generatedText = gammaTraceSerializer.serialize(gammaTraceGenerator.generate(verificationCase, callTrace, witnessState, nameStore))
        val generatedTrace = temporaryDirectory.resolve("trace.gamma")
        generatedTrace.writeText(generatedText)

        val (_, reference, generated) = read(CROSSROADS, REFERENCE_TRACE, generatedTrace)

        assertThat(EcoreUtil.equals(reference, generated)).isTrue()
        assertThat(serializer.serialize(generated, SaveOptions.newBuilder().format().options)).isEqualTo(generatedText)
    }

    private fun read(vararg paths: Path): List<GammaModelPackage> {
        return gammaReader.readGammaFiles(
            paths.map {
                it.toFile()
            },
        )
    }

    companion object {
        private val CROSSROADS = Path("build", "test-models", "Crossroads.gamma")
        private val PAIR = Path("..", "models", "etc")
        private val REFERENCE_TRACE = PAIR.resolve("crossroads-ACanBeGreen.trace.gamma")
        private val REFERENCE_TRACE_JSON = PAIR.resolve("crossroads-ACanBeGreen.trace.json")
        private val REFERENCE_WITNESS_JSON = PAIR.resolve("crossroads-ACanBeGreen.witness.json")
    }
}
