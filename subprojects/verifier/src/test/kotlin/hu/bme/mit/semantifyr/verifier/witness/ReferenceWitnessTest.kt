/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.verifier.VerificationReport
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.internal.VerificationArtifactWriter
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText

@InjectWithOxsts
class ReferenceWitnessTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var loader: SemantifyrLoader

    @Inject
    private lateinit var discoverer: VerificationCaseDiscoverer

    @Inject
    private lateinit var backAnnotator: ClassWitnessBackAnnotator

    @Inject
    private lateinit var artifactWriter: VerificationArtifactWriter

    @Test
    fun `the timed reference witness is the witness the verifier derives from its stored report`(@TempDir output: Path) {
        val context = loader.loadStandaloneModel(REFERENCE.resolve("blinker.semantifyr"))
        val case = discoverer.findByQualifiedName(context, CASE)
        val report = Json.decodeFromString<VerificationReport>(REFERENCE.resolve("blinker.report.json").readText())
        val compilation = SemantifyrCompiler(injector, ArtifactConfig.NONE, report.optimization).compile(case.classDeclaration, output)

        val witness = backAnnotator.createWitnessInlinedOxsts(requireNotNull(report.trace), compilation, report.result.verdict)
        artifactWriter.writeWitness(witness, output, CASE)

        assertThat(output.resolve("witness.semantifyr").readText()).isEqualTo(REFERENCE.resolve("blinker.witness.semantifyr").readText().replace("\r\n", "\n"))
    }

    companion object {
        private val REFERENCE = Path("..", "..", "oxsts-test-models", "witness")
        private const val CASE = "example::blinker::BlinkerSwitchesBetweenTwoAndThree"
    }
}
