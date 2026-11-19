/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.verifier.internal.VerificationArtifactWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class VerificationReportTest {
    private val json = VerificationArtifactWriter.json

    @Test
    fun `a report reads back as the report that was written`() {
        val trace = ReportTrace(
            root = "root",
            transitions = listOf(
                ObservedTransition(
                    transition = "example::Lamp::toggle",
                    parameters = listOf(
                        ObservedParameter("partner", ReportType(ReportTypeKind.Class, "example::Lamp")),
                    ),
                ),
            ),
            variables = listOf(
                ObservedVariable("root.lamp.on", ReportType(ReportTypeKind.Datatype, "semantifyr::bool")),
                ObservedVariable(
                    "root.counts[1]",
                    ReportType(ReportTypeKind.Datatype, "semantifyr::int"),
                    ObservedSlot(array = "root.counts", index = 1, size = 3),
                ),
            ),
            initial = ReportInitialEntry(state = 0, calls = emptyList()),
            states = listOf(
                ReportState(
                    mapOf(
                        "root.lamp.on" to ReportValue.Bool(false),
                        "root.counts[1]" to ReportValue.Integer(BigInteger.TWO),
                        "root.lamp.level" to ReportValue.Real(0.5),
                        "root.lamp.color" to ReportValue.Text("Red"),
                        "root.lamp.partner" to ReportValue.Null,
                        "root.counts" to ReportValue.Array(
                            listOf(
                                ReportValue.Integer(BigInteger.ONE),
                                ReportValue.Integer(BigInteger.TWO),
                            ),
                        ),
                        "root.lamp.item" to ReportValue.Record(
                            mapOf(
                                "speed" to ReportValue.Integer(BigInteger.TWO),
                            ),
                        ),
                    ),
                ),
                ReportState(
                    mapOf(
                        "root.lamp.on" to ReportValue.Bool(true),
                    ),
                ),
            ),
            steps = listOf(
                ReportStep(
                    source = 0,
                    target = 1,
                    elapsed = Fraction.of(3, 2),
                    calls = listOf(
                        ReportCall(
                            self = "root.lamp",
                            transition = "example::Lamp::toggle",
                            arguments = mapOf(
                                "partner" to ReportValue.Text("root.hall"),
                            ),
                            calls = emptyList(),
                        ),
                    ),
                ),
                ReportStep(
                    source = 1,
                    target = 0,
                    elapsed = Fraction.of(BigInteger.TWO.pow(53) + BigInteger.ONE),
                    calls = emptyList(),
                ),
            ),
            missingCalls = "the calls were not recorded",
        )
        val report = VerificationReport(
            version = REPORT_VERSION,
            kind = ReportKind.Run,
            verificationCase = "example::Case",
            portfolioId = "fake",
            optimization = OptimizationConfig.NONE,
            timeout = 10.seconds,
            result = VerificationResult(
                metadata = VerificationMetadata(backendId = "fake:default", startedAt = Instant.fromEpochSeconds(0)),
                verdict = VerificationVerdict.Failed,
                metrics = VerificationMetrics(),
            ),
            trace = trace,
        )

        assertThat(json.decodeFromString<VerificationReport>(json.encodeToString(report))).isEqualTo(report)
    }

    @Test
    fun `an integer too large for a JSON number reads back as text with its digits`() {
        val largest = BigInteger.TWO.pow(53) - BigInteger.ONE
        val state = ReportState(
            mapOf(
                "root.largest" to ReportValue.Integer(largest),
                "root.beyond" to ReportValue.Integer(largest + BigInteger.ONE),
                "root.below" to ReportValue.Integer(-largest - BigInteger.ONE),
            ),
        )

        val read = json.decodeFromString<ReportState>(json.encodeToString(state))

        assertThat(read.values).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "root.largest" to ReportValue.Integer(largest),
                "root.beyond" to ReportValue.Text("9007199254740992"),
                "root.below" to ReportValue.Text("-9007199254740992"),
            ),
        )
    }
}
