/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.internal

import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verifier.Trace
import hu.bme.mit.semantifyr.verifier.VerificationMetrics
import hu.bme.mit.semantifyr.verifier.VerificationReport
import hu.bme.mit.semantifyr.verifier.VerifierMetrics
import hu.bme.mit.semantifyr.verifier.fakeVerificationResult
import hu.bme.mit.semantifyr.verifier.witness.CallTrace
import hu.bme.mit.semantifyr.verifier.witness.CallTraceStep
import hu.bme.mit.semantifyr.verifier.witness.WitnessState
import hu.bme.mit.semantifyr.verifier.witness.WitnessStateStep
import hu.bme.mit.semantifyr.verifier.witness.WitnessStateValue
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.serializer.ISerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes

class VerificationArtifactWriterTest {

    private val serializer: ISerializer = mock()
    private val writer = VerificationArtifactWriter(serializer)

    @Test
    fun `writeReport writes report json containing the case and portfolio identifiers`(@TempDir tmp: Path) {
        val report = sampleReport(qualifiedName = "pkg::Foo", portfolioId = "test-portfolio")

        writer.writeReport(tmp, report)

        val reportFile = tmp.resolve("report.json")
        assertThat(reportFile.exists()).isTrue
        val content = reportFile.readText()
        assertThat(content).contains("\"verificationCase\": \"pkg::Foo\"")
        assertThat(content).contains("\"portfolioId\": \"test-portfolio\"")
        assertThat(content).contains("\"verdict\": \"Passed\"")
    }

    @Test
    fun `writeReport overwrites an existing report`(@TempDir tmp: Path) {
        writer.writeReport(tmp, sampleReport(qualifiedName = "first"))
        writer.writeReport(tmp, sampleReport(qualifiedName = "second"))

        val content = tmp.resolve("report.json").readText()
        assertThat(content).contains("\"verificationCase\": \"second\"")
        assertThat(content).doesNotContain("\"verificationCase\": \"first\"")
    }

    @Test
    fun `writeWitnessArtifacts writes the call trace and the witness state alongside the witness file`(@TempDir tmp: Path) {
        val trace = sampleTraceWithoutWitnessSerialization()

        writer.writeWitnessArtifacts(trace, tmp, "pkg::Bar")

        assertThat(tmp.resolve("witness.json").exists()).isTrue
        assertThat(tmp.resolve("trace.json").exists()).isTrue
        // witness.oxsts is attempted but Xtext serialization fails on a mock InlinedOxsts;
        // the writer swallows the failure rather than aborting the other artifacts.
    }

    @Test
    fun `witness state json reflects the state values`(@TempDir tmp: Path) {
        val trace = sampleTraceWithStateValues()

        writer.writeWitnessArtifacts(trace, tmp, "pkg::Stateful")

        val witnessJson = tmp.resolve("witness.json").readText()
        assertThat(witnessJson).contains("\"variable\": \"x\"")
        assertThat(witnessJson).contains("\"value\": \"0\"")
    }

    @Test
    fun `call trace json reflects the call trace steps`(@TempDir tmp: Path) {
        val trace = sampleTraceWithStateValues()

        writer.writeWitnessArtifacts(trace, tmp, "pkg::Trace")

        val callTraceJson = tmp.resolve("trace.json").readText()
        assertThat(callTraceJson).contains("initialStep")
        assertThat(callTraceJson).contains("steps")
    }

    @Test
    fun `witness oxsts write failure is swallowed without aborting state and trace writes`(@TempDir tmp: Path) {
        val trace = sampleTraceWithoutWitnessSerialization()

        writer.writeWitnessArtifacts(trace, tmp, "pkg::Resilient")

        assertThat(tmp.resolve("witness.json").exists()).isTrue
        assertThat(tmp.resolve("trace.json").exists()).isTrue
    }

    private fun sampleReport(
        qualifiedName: String = "pkg::Sample",
        portfolioId: String = "fake",
    ): VerificationReport {
        return VerificationReport(
            verificationCase = qualifiedName,
            portfolioId = portfolioId,
            optimization = OptimizationConfig.NONE,
            timeout = 5.minutes,
            result = fakeVerificationResult(
                verdict = VerificationVerdict.Passed,
                backendId = "fake-backend",
                metrics = VerificationMetrics(
                    backend = BackendMetrics(),
                    verifier = VerifierMetrics(),
                ),
            ),
        )
    }

    private fun sampleTraceWithoutWitnessSerialization(): Trace {
        return Trace(
            backAnnotatedModel = mock<InlinedOxsts>(),
            witnessState = WitnessState(
                initialStep = WitnessStateStep(emptyList()),
                steps = emptyList(),
            ),
            callTrace = CallTrace(
                initialStep = CallTraceStep(emptyList()),
                steps = emptyList(),
            ),
        )
    }

    private fun sampleTraceWithStateValues(): Trace {
        return Trace(
            backAnnotatedModel = mock<InlinedOxsts>(),
            witnessState = WitnessState(
                initialStep = WitnessStateStep(listOf(WitnessStateValue(variable = "x", value = "0"))),
                steps = emptyList(),
            ),
            callTrace = CallTrace(
                initialStep = CallTraceStep(emptyList()),
                steps = emptyList(),
            ),
        )
    }
}
