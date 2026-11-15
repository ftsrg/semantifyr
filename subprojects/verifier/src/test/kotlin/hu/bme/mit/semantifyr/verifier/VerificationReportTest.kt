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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class VerificationReportTest {

    private val json = Json {
        encodeDefaults = true
    }

    private val trace = ReportTrace(
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
            ObservedVariable("root.lamp.color", ReportType(ReportTypeKind.Enum, "example::Color")),
            ObservedVariable("root.lamp.partner", ReportType(ReportTypeKind.Class, "example::Lamp")),
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
                    "root.lamp.color" to ReportValue.EnumLiteral("Red"),
                    "root.lamp.partner" to ReportValue.NoInstance,
                    "root.counts[1]" to ReportValue.Integer(BigInteger.TWO.pow(53)),
                ),
            ),
            ReportState(
                mapOf(
                    "root.lamp.on" to ReportValue.Bool(true),
                    "root.lamp.partner" to ReportValue.Instance("root.hall"),
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
                            "partner" to ReportValue.Instance("root.hall"),
                        ),
                        calls = emptyList(),
                    ),
                ),
            ),
            ReportStep(
                source = 1,
                target = 0,
                elapsed = Fraction.ZERO,
                calls = emptyList(),
            ),
        ),
    )

    private fun report(kind: ReportKind, trace: ReportTrace?): VerificationReport {
        return VerificationReport(
            version = REPORT_VERSION,
            kind = kind,
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
    }

    private fun encoded(report: VerificationReport): JsonObject {
        return json.parseToJsonElement(json.encodeToString(report)).jsonObject
    }

    @Test
    fun `the report carries its version and whether it is a run's or a re-check's`() {
        assertThat(encoded(report(ReportKind.Run, null))["version"].toString()).isEqualTo("$REPORT_VERSION")
        assertThat(encoded(report(ReportKind.Run, null))["kind"].toString()).isEqualTo("\"run\"")
        assertThat(encoded(report(ReportKind.Recheck, null))["kind"].toString()).isEqualTo("\"re-check\"")
    }

    @Test
    fun `the trace names the root instance and lists the observed transitions and variable types once`() {
        val trace = encoded(report(ReportKind.Run, trace))["trace"]!!.jsonObject

        assertThat(trace["root"].toString()).isEqualTo("\"root\"")
        assertThat(trace["transitions"]!!.jsonArray.single().jsonObject["parameters"].toString())
            .isEqualTo("""[{"parameter":"partner","type":{"kind":"class","name":"example::Lamp","element":null,"size":null}}]""")
        assertThat(trace["variables"]!!.jsonArray.last().toString())
            .isEqualTo("""{"variable":"root.counts[1]","type":{"kind":"datatype","name":"semantifyr::int","element":null,"size":null},"slot":{"array":"root.counts","index":1,"size":3}}""")
    }

    @Test
    fun `values are JSON values and an absent variable is left out of a state`() {
        val states = encoded(report(ReportKind.Run, trace))["trace"]!!.jsonObject["states"]!!.jsonArray

        assertThat(states[0].toString())
            .isEqualTo("""{"values":{"root.lamp.on":false,"root.lamp.color":"Red","root.lamp.partner":null,"root.counts[1]":"9007199254740992"}}""")
        assertThat(states[1].jsonObject["values"]!!.jsonObject.keys).containsExactly("root.lamp.on", "root.lamp.partner")
    }

    @Test
    fun `the initial entry names the first state and a step names its states, its elapsed time and its calls`() {
        val trace = encoded(report(ReportKind.Run, trace))["trace"]!!.jsonObject

        assertThat(trace["initial"].toString()).isEqualTo("""{"state":0,"calls":[]}""")
        assertThat(trace["steps"]!!.jsonArray[0].toString())
            .isEqualTo("""{"source":0,"target":1,"elapsed":"3/2","calls":[{"self":"root.lamp","transition":"example::Lamp::toggle","arguments":{"partner":"root.hall"},"calls":[]}]}""")
        assertThat(trace["steps"]!!.jsonArray[1].toString()).isEqualTo("""{"source":1,"target":0,"elapsed":0,"calls":[]}""")
    }

    @Test
    fun `missing calls are stated with their reason`() {
        val withoutCalls = trace.copy(missingCalls = "the calls were not recorded")

        assertThat(encoded(report(ReportKind.Run, trace))["trace"]!!.jsonObject["missingCalls"].toString()).isEqualTo("null")
        assertThat(encoded(report(ReportKind.Run, withoutCalls))["trace"]!!.jsonObject["missingCalls"].toString())
            .isEqualTo("\"the calls were not recorded\"")
    }

    @Test
    fun `the report reads back to the JSON it was written as`() {
        val written = json.encodeToString(report(ReportKind.Recheck, trace))

        assertThat(json.encodeToString(json.decodeFromString<VerificationReport>(written))).isEqualTo(written)
    }

    @Test
    fun `a string value reads back as text, which the type list resolves`() {
        val read = json.decodeFromString<VerificationReport>(json.encodeToString(report(ReportKind.Run, trace)))

        assertThat(read.trace!!.states[0].values).containsEntry("root.lamp.color", ReportValue.Text("Red"))
        assertThat(read.trace!!.states[0].values).containsEntry("root.lamp.on", ReportValue.Bool(false))
        assertThat(read.trace!!.states[0].values).containsEntry("root.lamp.partner", ReportValue.NoInstance)
    }
}
