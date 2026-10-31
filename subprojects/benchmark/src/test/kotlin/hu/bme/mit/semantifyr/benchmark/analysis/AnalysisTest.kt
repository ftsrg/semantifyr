/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.benchmark.BenchmarkRecord
import hu.bme.mit.semantifyr.benchmark.Measurement
import hu.bme.mit.semantifyr.benchmark.Outcome
import hu.bme.mit.semantifyr.compiler.pipeline.context.CompilationMetrics
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.verifier.VerificationMetrics
import hu.bme.mit.semantifyr.verifier.VerifierMetrics
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class AnalysisTest {

    private fun record(
        cell: Map<String, String>,
        repetition: Int,
        wallTime: Duration,
        verdict: VerificationVerdict = VerificationVerdict.Passed,
        expected: VerificationVerdict? = VerificationVerdict.Passed,
        decidedBy: String? = "theta",
        outcome: Outcome = Outcome.Completed,
    ): BenchmarkRecord {
        return BenchmarkRecord(
            campaign = "test",
            cell = cell,
            repetition = repetition,
            warmup = repetition == 1,
            measurement = Measurement(
                outcome = outcome,
                verdict = verdict,
                expected = expected,
                decidedBy = decidedBy,
                wallTime = wallTime,
                metrics = VerificationMetrics(
                    verifier = VerifierMetrics(
                        compilation = CompilationMetrics(flattenedSize = ModelSize(variables = 7, operations = 5, expressions = 30)),
                    ),
                ),
                extensions = mapOf(
                    "sysml" to buildJsonObject {
                        put("transformationDuration", JsonPrimitive("PT35.5S"))
                        put("lines", JsonPrimitive(120))
                    },
                ),
            ),
        )
    }

    private val crossroadsTheta = mapOf("model" to "crossroads.sysml", "case" to "Twin", "engine" to "theta")
    private val crossroadsSpin = mapOf("model" to "crossroads.sysml", "case" to "Twin", "engine" to "spin")

    private fun repetitions(
        cell: Map<String, String>,
        vararg seconds: Int,
        verdict: VerificationVerdict = VerificationVerdict.Passed,
    ): List<BenchmarkRecord> {
        return seconds.mapIndexed { index, wall ->
            record(cell, index + 1, wall.seconds, verdict = verdict, decidedBy = "${cell.getValue("engine")}:default")
        }
    }

    @Test
    fun `the warmup is discarded and the repetitions give median and spread`() {
        val summary = summarize(repetitions(crossroadsTheta, 10, 1, 3, 2)).single()

        assertThat(summary.repetitions).isEqualTo(3)
        assertThat(summary.wallTime).isEqualTo(Statistic(median = 2.seconds, minimum = 1.seconds, maximum = 3.seconds))
        assertThat(summary.wallTime!!.spread).isEqualTo(2.seconds)
        assertThat(summary.flattenedSize).isEqualTo(ModelSize(variables = 7, operations = 5, expressions = 30))
        assertThat(summary.extensions).containsEntry("sysml.transformationDuration", "PT35.5S").containsEntry("sysml.lines", "120")
    }

    @Test
    fun `a cell that timed out in its warmup keeps its outcome and sizes but has no times`() {
        val record = record(crossroadsTheta, 1, 300.seconds, verdict = VerificationVerdict.Timeout, decidedBy = null, outcome = Outcome.TimedOut)

        val summary = summarize(listOf(record)).single()

        assertThat(summary.outcome).isEqualTo(Outcome.TimedOut)
        assertThat(summary.repetitions).isZero()
        assertThat(summary.wallTime).isNull()
        assertThat(summary.flattenedSize).isEqualTo(ModelSize(variables = 7, operations = 5, expressions = 30))
    }

    @Test
    fun `an even number of repetitions takes the mean of the middle two`() {
        assertThat(Statistic.of(listOf(4.seconds, 1.seconds, 2.seconds, 3.seconds))!!.median).isEqualTo(2.5.seconds)
    }

    @Test
    fun `one wrong repetition makes the cell wrong and disagreeing verdicts have no single verdict`() {
        val records = listOf(
            record(crossroadsTheta, 1, 1.seconds),
            record(crossroadsTheta, 2, 1.seconds),
            record(crossroadsTheta, 3, 1.seconds, verdict = VerificationVerdict.Failed),
        )

        val summary = summarize(records).single()

        assertThat(summary.asExpected).isFalse()
        assertThat(summary.verdict).isNull()
        assertThat(summary.verdicts).containsExactly(VerificationVerdict.Passed, VerificationVerdict.Failed)
    }

    @Test
    fun `the csv has one row per cell with the axes first and durations in seconds`() {
        val csv = ResultsTable.csv(summarize(repetitions(crossroadsTheta, 10, 1, 3, 2))).lines()

        assertThat(csv[0]).startsWith("model,case,engine,outcome,verdict,expected,as_expected,falsification,decided_by,repetitions,wall_median_s,wall_min_s,wall_max_s,")
        assertThat(csv[0]).endsWith(",sysml.transformationDuration,sysml.lines")
        assertThat(csv[1]).startsWith("crossroads.sysml,Twin,theta,Completed,Passed,Passed,true,false,theta:default,3,2.000,1.000,3.000,")
        assertThat(csv[1]).endsWith(",35.500,120")
    }

    @Test
    fun `the markdown summary aggregates every axis`() {
        val summaries = summarize(repetitions(crossroadsTheta, 10, 1, 3, 2) + repetitions(crossroadsSpin, 10, 4, 6, 5, verdict = VerificationVerdict.Failed))

        val markdown = ResultsTable.markdown("test", summaries)

        assertThat(markdown).contains("## Campaign test")
        assertThat(markdown).contains("| 2 | 2 | 0 | 0 | 0 | 1 | 1 | 7.000 | 3.500 | 2.000 |")
        assertThat(markdown).contains("### By engine")
        assertThat(markdown).contains("| theta | 1 | 1 | 0 | 0 | 0 | 1 | 0 | 2.000 | 2.000 | 2.000 |")
        assertThat(markdown).contains("| spin | 1 | 1 | 0 | 0 | 0 | 0 | 1 | 5.000 | 5.000 | 2.000 |")
    }

    @Test
    fun `engines that decided a case correctly are derived from the results`() {
        val crossroadsUppaal = mapOf("model" to "crossroads.sysml", "case" to "Twin", "engine" to "uppaal")
        val otherTheta = mapOf("model" to "crossroads.sysml", "case" to "Other", "engine" to "theta")
        val records = repetitions(crossroadsTheta, 1, 1, 1, 1) +
            repetitions(crossroadsSpin, 1, 1, 1, 1, verdict = VerificationVerdict.Failed) +
            listOf(record(crossroadsUppaal, 1, 1.seconds, outcome = Outcome.TimedOut, verdict = VerificationVerdict.Timeout, decidedBy = null)) +
            listOf(record(otherTheta, 1, 1.seconds, outcome = Outcome.TimedOut, verdict = VerificationVerdict.Timeout, decidedBy = null))

        val engines = EnginesReport.derive(summarize(records))

        assertThat(engines).isEqualTo(
            DecidingEngines(
                listOf(
                    ModelEngines(
                        "crossroads.sysml",
                        listOf(CaseEngines("Twin", listOf("theta")), CaseEngines("Other", emptyList())),
                    ),
                ),
            ),
        )
    }
}
