/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.benchmark.Outcome
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ReferenceTest {

    private fun summary(
        case: String,
        verdicts: List<VerificationVerdict> = listOf(VerificationVerdict.Failed),
        flattenedVariables: Int = 12,
        wallMedian: Duration = 10.seconds,
    ): CellSummary {
        return CellSummary(
            cell = mapOf("model" to "crossroads.sysml", "case" to case),
            outcome = Outcome.of(verdicts.last()),
            verdicts = verdicts,
            expected = VerificationVerdict.Failed,
            asExpected = true,
            falsification = true,
            decidedBy = setOf("theta"),
            repetitions = verdicts.size,
            wallTime = Statistic(wallMedian, wallMedian, wallMedian),
            cpuTime = null,
            backendCpuTime = null,
            runexecCpuTime = null,
            peakMemory = null,
            compilationTime = null,
            optimizationTime = null,
            backendTime = null,
            inputSize = ModelSize(40, 30, 200),
            inlinedSize = ModelSize(20, 25, 150),
            flattenedSize = ModelSize(flattenedVariables, 20, 100),
            extensions = emptyMap(),
        )
    }

    @Test
    fun `the reference keeps decisive verdicts and sizes, sorted by cell`() {
        val reference = Reference.of(listOf(summary("B"), summary("A", listOf(VerificationVerdict.Timeout))))

        assertThat(reference.map { it.key }).containsExactly("model=crossroads.sysml/case=A", "model=crossroads.sysml/case=B")
        assertThat(reference.map { it.verdict }).containsExactly(null, VerificationVerdict.Failed)
        assertThat(reference.first().flattenedSize).isEqualTo(ModelSize(12, 20, 100))
    }

    @Test
    fun `a reference survives writing and reading`(@TempDir directory: Path) {
        val reference = Reference.of(listOf(summary("A"), summary("B", listOf(VerificationVerdict.Timeout))))
        val file = directory.resolve("reference").resolve("crossroads.jsonl")

        Reference.write(file, reference)

        assertThat(Reference.read(file)).isEqualTo(reference)
    }

    @Test
    fun `identical results have no differences and timing is not compared`() {
        val reference = Reference.of(listOf(summary("A", wallMedian = 1.seconds)))

        assertThat(Reference.compare(reference, Reference.of(listOf(summary("A", wallMedian = 100.seconds))))).isEmpty()
    }

    @Test
    fun `verdicts and sizes are compared strictly`() {
        val reference = Reference.of(listOf(summary("A"), summary("B"), summary("C")))
        val current = Reference.of(
            listOf(
                summary("A", listOf(VerificationVerdict.Passed)),
                summary("B", flattenedVariables = 13),
                summary("D"),
            ),
        )

        assertThat(Reference.compare(reference, current)).containsExactly(
            "model=crossroads.sysml/case=A: verdict is Passed, the reference has Failed",
            "model=crossroads.sysml/case=B: flattened size is ModelSize(variables=13, operations=20, expressions=100), the reference has ModelSize(variables=12, operations=20, expressions=100)",
            "model=crossroads.sysml/case=C: missing from the results",
            "model=crossroads.sysml/case=D: not in the reference",
        )
    }

    @Test
    fun `times are compared against a baseline within a tolerance`() {
        val baseline = listOf(summary("A", wallMedian = 10.seconds), summary("B", wallMedian = 10.seconds))
        val current = listOf(summary("A", wallMedian = 11.seconds), summary("B", wallMedian = 13.seconds), summary("C", wallMedian = 50.seconds))

        val regressions = Reference.compareTimes(baseline, current, tolerance = 0.2)

        assertThat(regressions).hasSize(1)
        assertThat(regressions.single()).startsWith("model=crossroads.sysml/case=B: wall time median 13s exceeds the baseline 10s")
    }
}
