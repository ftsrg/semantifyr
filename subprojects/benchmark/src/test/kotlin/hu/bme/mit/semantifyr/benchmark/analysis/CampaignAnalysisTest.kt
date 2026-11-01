/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.benchmark.BenchmarkRecord
import hu.bme.mit.semantifyr.benchmark.Measurement
import hu.bme.mit.semantifyr.benchmark.Outcome
import hu.bme.mit.semantifyr.benchmark.RunEnvironment
import hu.bme.mit.semantifyr.compiler.pipeline.context.CompilationMetrics
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationStats
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassStats
import hu.bme.mit.semantifyr.verifier.VerificationMetrics
import hu.bme.mit.semantifyr.verifier.VerifierMetrics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CampaignAnalysisTest {

    private fun cell(
        model: String,
        case: String,
        backend: String = "theta-cegar-expl",
        optimization: String = "all",
    ): Map<String, String> {
        val engine = backend.substringBefore('-')
        return mapOf("model" to model, "case" to case, "engine" to engine, "backend" to backend, "optimization" to optimization)
    }

    private fun record(
        cell: Map<String, String>,
        repetition: Int,
        verdict: VerificationVerdict = VerificationVerdict.Passed,
        expected: VerificationVerdict? = VerificationVerdict.Passed,
        unfolding: Duration = 1.seconds,
        checking: Duration = 2.seconds,
        flattened: ModelSize = ModelSize(10, 5, 100),
        passes: List<PassStats> = emptyList(),
        loadAverage: Double? = null,
        outcome: Outcome = Outcome.of(verdict),
    ): BenchmarkRecord {
        val compilation = CompilationMetrics(
            createDuration = unfolding,
            flatteningOptimization = OptimizationStats(passes = passes),
            inputSize = ModelSize(1, 2, 3),
            inlinedSize = ModelSize(4, 5, 6),
            flattenedSize = flattened,
        )
        return BenchmarkRecord(
            campaign = "test",
            cell = cell,
            repetition = repetition,
            warmup = repetition == 1,
            measurement = Measurement(
                outcome = outcome,
                verdict = verdict,
                expected = expected,
                metrics = VerificationMetrics(
                    backend = BackendMetrics(verificationDuration = checking),
                    verifier = VerifierMetrics(compilationDuration = unfolding, compilation = compilation),
                ),
                loadAverage = loadAverage,
            ),
        )
    }

    private fun runs(
        cell: Map<String, String>,
        verdict: VerificationVerdict = VerificationVerdict.Passed,
        expected: VerificationVerdict? = VerificationVerdict.Passed,
        checking: List<Duration> = listOf(2.seconds, 2.seconds, 2.seconds),
        unfolding: Duration = 1.seconds,
        flattened: ModelSize = ModelSize(10, 5, 100),
        passes: List<PassStats> = emptyList(),
    ): List<BenchmarkRecord> {
        return (listOf(9.seconds) + checking).mapIndexed { index, time ->
            record(cell, index + 1, verdict, expected, unfolding, time, flattened, passes)
        }
    }

    private fun timedOut(cell: Map<String, String>): BenchmarkRecord {
        return record(cell, 1, VerificationVerdict.Timeout)
    }

    private fun section(analysis: CampaignAnalysis, title: String): AnalysisSection {
        return analysis.sections.single {
            it.title.startsWith(title)
        }
    }

    private fun file(section: AnalysisSection, name: String): String {
        return section.files.single { it.name == name }.content
    }

    @Test
    fun `the correctness table puts every cell in one column and lists the wrong ones`() {
        val records = runs(cell("a.oxsts", "Right")) +
            runs(cell("a.oxsts", "Wrong"), verdict = VerificationVerdict.Failed) +
            runs(cell("a.oxsts", "Free"), expected = null) +
            runs(cell("a.oxsts", "Undecided"), verdict = VerificationVerdict.Inconclusive) +
            listOf(timedOut(cell("b.sysml", "Slow"))) +
            listOf(record(cell("b.sysml", "Broken"), 1, VerificationVerdict.Errored)) +
            listOf(record(cell("b.sysml", "Absent"), 1, verdict = VerificationVerdict.Passed, outcome = Outcome.Unavailable))

        val correctness = section(CampaignAnalysis(records, null), "Correctness")

        val table = correctness.tables[0]
        assertThat(table.rows).containsExactly(
            listOf("a.oxsts", "4", "3", "0", "1", "1", "1", "1", "0", "1", "0", "0", "0"),
            listOf("b.sysml", "3", "3", "0", "0", "0", "0", "0", "1", "0", "0", "1", "1"),
        )
        assertThat(table.totals.single()).containsExactly("Total", "7", "6", "0", "1", "1", "1", "1", "1", "1", "0", "1", "1")
        val matrix = correctness.tables[1]
        assertThat(matrix.rows[0]).containsExactly("Passed", "1", "1", "1", "0", "0", "1", "1", "1", "6")
        assertThat(matrix.rows[2]).containsExactly("None", "1", "0", "0", "0", "0", "0", "0", "0", "1")
        assertThat(correctness.notes.single()).contains("case=Wrong").contains("expected Passed")
    }

    @Test
    fun `a wrong repetition outweighs a later timeout`() {
        val records = listOf(
            record(cell("a.oxsts", "Flaky"), 1),
            record(cell("a.oxsts", "Flaky"), 2, VerificationVerdict.Failed),
            record(cell("a.oxsts", "Flaky"), 3, VerificationVerdict.Timeout),
        )

        assertThat(Judgement.of(summarize(records).single())).isEqualTo(Judgement.Wrong)
    }

    @Test
    fun `the unfolding data has one row per cell with times in seconds and timeouts without times`() {
        val records = runs(cell("a.oxsts", "Fast"), checking = listOf(3.seconds, 1.seconds, 2.seconds), unfolding = 500.milliseconds) +
            listOf(timedOut(cell("a.oxsts", "Slow"))) +
            listOf(record(cell("a.oxsts", "Broken"), 1, VerificationVerdict.Errored))

        val unfolding = section(CampaignAnalysis(records, null), "Unfolding")

        assertThat(file(unfolding, UnfoldingAnalysis.DATA_FILE).lines()).containsExactly(
            "model case unfolding_median_s unfolding_min_s unfolding_max_s checking_median_s checking_min_s checking_max_s verdict",
            "a.oxsts Fast 0.500 0.500 0.500 2.000 1.000 3.000 passed",
            "a.oxsts Slow nan nan nan nan nan nan timeout",
            "",
        )
        assertThat(unfolding.tables.single().rows.single()).containsExactly("a.oxsts", "3", "1", "1", "", "0.500", "2.000", "0.200")
    }

    @Test
    fun `sizes are taken once per case whatever the backend, with medians per model`() {
        val records = runs(cell("a.oxsts", "One", backend = "theta-cegar-expl"), flattened = ModelSize(1, 1, 10)) +
            runs(cell("a.oxsts", "One", backend = "nuxmv-ic3-invar"), flattened = ModelSize(1, 1, 10)) +
            runs(cell("a.oxsts", "Two", backend = "theta-cegar-expl"), flattened = ModelSize(2, 2, 20))

        val sizes = section(CampaignAnalysis(records, null), "Model sizes")

        assertThat(file(sizes, SizeAnalysis.DATA_FILE).lines()).containsExactly(
            "model case input_variables input_operations input_expressions inlined_variables inlined_operations inlined_expressions flattened_variables flattened_operations flattened_expressions",
            "a.oxsts One 1 2 3 4 5 6 1 1 10",
            "a.oxsts Two 1 2 3 4 5 6 2 2 20",
            "",
        )
        assertThat(sizes.tables.single().rows.single()).containsExactly("a.oxsts", "2", "1", "2", "3", "4", "5", "6", "1.5", "1.5", "15")
    }

    @Test
    fun `each pass level is compared with all over the cases every level completed`() {
        // every gated pass runs under all, most of them without firing
        fun passes(vararg firing: PassStats): List<PassStats> {
            return OptimizationPass.entries.map { pass ->
                firing.firstOrNull { it.name == "${pass.name}Pass" } ?: PassStats("${pass.name}Pass", duration = 10.milliseconds, runs = 2)
            }
        }
        val copyPropagation = PassStats("CopyPropagationPass", duration = 300.milliseconds, runs = 2, firings = 4)
        val deadCode = PassStats("DeadCodeRemovalPass", duration = 100.milliseconds, runs = 2, firings = 1)
        val records = runs(cell("a.oxsts", "Steady"), checking = listOf(2.seconds), passes = passes(copyPropagation, deadCode)) +
            runs(cell("a.oxsts", "Steady", optimization = "without-copy-propagation"), checking = listOf(3.seconds), flattened = ModelSize(12, 5, 150)) +
            runs(cell("a.oxsts", "Steady", optimization = "none"), checking = listOf(4.seconds), flattened = ModelSize(20, 9, 300)) +
            runs(cell("a.oxsts", "Hard"), checking = listOf(5.seconds), passes = passes(copyPropagation)) +
            runs(cell("a.oxsts", "Hard", optimization = "without-copy-propagation"), checking = listOf(6.seconds)) +
            listOf(timedOut(cell("a.oxsts", "Hard", optimization = "none")))

        val passes = section(CampaignAnalysis(records, null), "Optimizer passes")

        val data = file(passes, OptimizerPassAnalysis.DATA_FILE).lines()
        assertThat(data[0]).isEqualTo(
            "model level variables operations expressions variables_ratio expressions_ratio unfolding_s unfolding_ratio checking_s checking_ratio pass_s firings timeouts errors wrong",
        )
        assertThat(data.drop(1)).containsExactly(
            "a.oxsts all 20 10 200 1.000 1.000 1.000 1.000 2.000 1.000 0.000 0 0 0 0",
            "a.oxsts without-copy-propagation 22 10 250 1.100 1.250 1.000 1.000 3.000 1.500 0.600 8 0 0 0",
            "a.oxsts none 30 14 400 1.500 2.000 1.000 1.000 4.000 2.000 0.850 9 1 0 0",
            "total all 20 10 200 1.000 1.000 1.000 1.000 2.000 1.000 0.000 0 0 0 0",
            "total without-copy-propagation 22 10 250 1.100 1.250 1.000 1.000 3.000 1.500 0.600 8 0 0 0",
            "total none 30 14 400 1.500 2.000 1.000 1.000 4.000 2.000 0.850 9 1 0 0",
            "",
        )
        assertThat(passes.tables.single().rows.map { it.first() }).containsExactly("all", "without-copy-propagation", "none")
    }

    @Test
    fun `a campaign with one level has no pass section and one with a backend per model no backend section`() {
        val records = runs(cell("a.oxsts", "One", backend = "theta-cegar-expl-pred-combined")) + runs(cell("b.sysml", "Two", backend = "theta-cegar-expl"))

        val titles = CampaignAnalysis(records, null).sections.map { it.title }

        assertThat(titles).containsExactly("Correctness (RQ1)", "Unfolding and model checking (RQ2)", "Model sizes (RQ2)")
    }

    @Test
    fun `backends are compared per case with agreement and a cactus of their times`() {
        val records = runs(cell("a.sysml", "Both", backend = "theta-cegar-expl"), checking = listOf(2.seconds)) +
            runs(cell("a.sysml", "Both", backend = "nuxmv-ic3-invar"), checking = listOf(1.seconds)) +
            runs(cell("a.sysml", "Split", backend = "theta-cegar-expl"), checking = listOf(4.seconds)) +
            runs(cell("a.sysml", "Split", backend = "nuxmv-ic3-invar"), verdict = VerificationVerdict.Failed, checking = listOf(3.seconds)) +
            runs(cell("a.sysml", "Lonely", backend = "theta-cegar-expl"), checking = listOf(1.seconds)) +
            listOf(timedOut(cell("a.sysml", "Lonely", backend = "nuxmv-ic3-invar")))

        val backends = section(CampaignAnalysis(records, null), "Backends")

        assertThat(file(backends, BackendAnalysis.DATA_FILE).lines()).containsExactly(
            "model case nuxmv-ic3-invar_s theta-cegar-expl_s agree",
            "a.sysml Both 1.000 2.000 1",
            "a.sysml Lonely nan 1.000 nan",
            "a.sysml Split 3.000 4.000 0",
            "",
        )
        assertThat(file(backends, BackendAnalysis.CACTUS_FILE).lines()).containsExactly(
            "solved nuxmv-ic3-invar_s theta-cegar-expl_s",
            "1 1.000 1.000",
            "2 nan 2.000",
            "3 nan 4.000",
            "",
        )
        assertThat(backends.tables.single().rows).containsExactly(
            listOf("nuxmv-ic3-invar", "3", "2", "1", "1", "0", "1", "0", "0", "0", "0", "4.000", "1"),
            listOf("theta-cegar-expl", "3", "3", "3", "0", "0", "0", "0", "0", "0", "0", "6.000", "1"),
        )
        assertThat(backends.notes.single()).contains("case=Split").contains("nuxmv-ic3-invar failed").contains("theta-cegar-expl passed")
    }

    @Test
    fun `the markdown names the host load and warns about a loaded host`() {
        val environment = RunEnvironment("test", "0.1.0", 3, 1, 5.minutes, "2026-11-01T00:00:00Z", 8, "Linux", "25", false)
        val records = listOf(1.0, 6.5, 2.0).mapIndexed { index, load ->
            record(cell("a.oxsts", "One"), index + 1, loadAverage = load)
        }

        val markdown = CampaignAnalysis(records, environment).markdown()

        assertThat(markdown).startsWith("# Campaign test\n")
        assertThat(markdown).contains("Semantifyr 0.1.0, 3 repetitions after 1 warmups, timeout 5m, 8 processors")
        assertThat(markdown).contains("Host load average at cell start 1.000 to 6.500, median 2.000 on 8 processors. The host was loaded")
    }

    @Test
    fun `writing removes the outputs an earlier analysis left`(@TempDir directory: Path) {
        directory.resolve(BackendAnalysis.DATA_FILE).writeText("stale")

        CampaignAnalysis(runs(cell("a.oxsts", "One")), null).write(directory)

        assertThat(directory.resolve(BackendAnalysis.DATA_FILE).exists()).isFalse()
        assertThat(directory.resolve(CampaignAnalysis.MARKDOWN_FILE).readText()).contains("## Correctness (RQ1)")
        assertThat(directory.resolve(CorrectnessAnalysis.TABLE_FILE).readText()).startsWith("\\begin{tabular}{lrrrrrrrrrrrr}\n\\toprule\n")
    }

    @Test
    fun `tables render pgfplots data, escaped latex and markdown`() {
        val table = Table(listOf("model", "time_s"), listOf(listOf("a_b.oxsts", ""), listOf("x|y z", "1.5")), listOf(listOf("Total", "1.5")))

        assertThat(table.dat()).isEqualTo("model time_s\na_b.oxsts nan\nx|y_z 1.5\n")
        assertThat(table.latex()).isEqualTo(
            "\\begin{tabular}{lr}\n\\toprule\nmodel & time\\_s \\\\\n\\midrule\na\\_b.oxsts &  \\\\\nx|y z & 1.5 \\\\\n\\midrule\nTotal & 1.5 \\\\\n\\bottomrule\n\\end{tabular}\n",
        )
        assertThat(table.markdown()).isEqualTo("| model | time_s |\n| --- | --- |\n| a_b.oxsts |  |\n| x\\|y z | 1.5 |\n| Total | 1.5 |\n")
    }
}
