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
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.verifier.VerificationReport
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
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
    private lateinit var expressionSerializer: ExpressionSerializer

    @Test
    fun `the stored report of a timed run reads back into a witness that assumes its scaled time and clock values`(@TempDir output: Path) {
        val context = loader.loadStandaloneModel(REFERENCE.resolve("blinker.semantifyr"))
        val case = discoverer.findByQualifiedName(context, CASE)
        val report = Json.decodeFromString<VerificationReport>(REFERENCE.resolve("blinker.report.json").readText())
        val compilation = SemantifyrCompiler(injector, ArtifactConfig.NONE, report.optimization).compile(case.classDeclaration, output)

        val witness = backAnnotator.createWitnessInlinedOxsts(requireNotNull(report.trace), compilation, report.result.verdict)

        assertThat(witness.timeScale).isEqualTo(2)
        assertThat(
            witness.mainTransition.eAllOfType<AssumptionOperation>().map {
                expressionSerializer.serialize(it.expression)
            }.toList(),
        ).containsExactly(
            "step == 1",
            "time == 5",
            "root.switchedBetween == true",
            "root.running == 5",
            "root.blinker.since == 0",
        )
    }

    companion object {
        private val REFERENCE = Path("..", "..", "oxsts-test-models", "witness")
        private const val CASE = "example::blinker::BlinkerSwitchesBetweenTwoAndThree"
    }
}
