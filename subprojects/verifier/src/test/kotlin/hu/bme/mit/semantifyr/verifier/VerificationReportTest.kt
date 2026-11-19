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
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class VerificationReportTest {
    private val json = Json

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
                ObservedVariable("root.lamp.level", ReportType(ReportTypeKind.Datatype, "semantifyr::real")),
                ObservedVariable("root.lamp.color", ReportType(ReportTypeKind.Enum, "example::Color")),
                ObservedVariable("root.lamp.partner", ReportType(ReportTypeKind.Class, "example::Lamp")),
                ObservedVariable("root.lamp.item", ReportType(ReportTypeKind.Record, "example::Item")),
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
                                "counts" to ReportValue.Array(
                                    listOf(
                                        ReportValue.Integer(BigInteger.ONE),
                                        ReportValue.Integer(BigInteger.TWO),
                                    ),
                                ),
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

    @Test
    fun `a report of another version is refused`() {
        assertThatThrownBy {
            VerificationReport(
                version = REPORT_VERSION + 1,
                kind = ReportKind.Run,
                verificationCase = "example::Case",
                portfolioId = "fake",
                optimization = OptimizationConfig.NONE,
                timeout = 10.seconds,
                result = VerificationResult(
                    metadata = VerificationMetadata(backendId = "fake:default", startedAt = Instant.fromEpochSeconds(0)),
                    verdict = VerificationVerdict.Passed,
                    metrics = VerificationMetrics(),
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a trace whose initial entry or step names a state it does not hold is refused`() {
        assertThatThrownBy {
            trace(initial = 1, steps = emptyList())
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            trace(initial = 0, steps = listOf(ReportStep(source = 0, target = 1, elapsed = Fraction.ZERO, calls = emptyList())))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a trace whose state holds an array whole is refused`() {
        assertThatThrownBy {
            trace(
                initial = 0,
                steps = emptyList(),
                values = mapOf(
                    "root.counts" to ReportValue.Array(
                        listOf(
                            ReportValue.Integer(BigInteger.ONE),
                        ),
                    ),
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun trace(
        initial: Int,
        steps: List<ReportStep>,
        values: Map<String, ReportValue> = emptyMap(),
    ): ReportTrace {
        return ReportTrace(
            root = "root",
            transitions = emptyList(),
            variables = emptyList(),
            initial = ReportInitialEntry(state = initial, calls = emptyList()),
            states = listOf(ReportState(values)),
            steps = steps,
        )
    }
}
