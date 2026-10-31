/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

class CampaignRunnerTest {

    @TempDir
    private lateinit var directory: Path

    private val model = Axis<String>("model") { it }

    private val completed = Measurement(Outcome.Completed, verdict = VerificationVerdict.Passed, wallTime = 1.seconds)

    private inner class ScriptedAction(
        private val unavailable: Set<String> = emptySet(),
        private val script: (String, Int) -> Measurement = { _, _ -> completed },
    ) : CellAction {
        val runs = mutableListOf<String>()
        val workDirectories = mutableListOf<Path>()

        override fun unavailability(cell: Cell): String? {
            return "absent".takeIf { cell[model] in unavailable }
        }

        override suspend fun run(cell: Cell, workDirectory: Path): Measurement {
            workDirectories.add(workDirectory)
            workDirectory.resolve("artifact.txt").writeText("leftover")
            runs += cell[model]
            return script(cell[model], runs.count { it == cell[model] })
        }
    }

    private fun campaignOver(vararg models: String): Campaign {
        return campaign("test") {
            axis(model, models.toList())
        }
    }

    private fun records(cellModel: String, outcomes: List<Outcome>): List<BenchmarkRecord> {
        return outcomes.mapIndexed { index, outcome ->
            BenchmarkRecord("test", mapOf("model" to cellModel), index + 1, index == 0, Measurement(outcome))
        }
    }

    @Test
    suspend fun `a cell runs one warmup and three measured repetitions`() {
        val action = ScriptedAction()

        val records = CampaignRunner(campaignOver("a"), ResultStore(directory), action).run()

        assertThat(records.map { it.repetition }).containsExactly(1, 2, 3, 4)
        assertThat(records.map { it.warmup }).containsExactly(true, false, false, false)
        assertThat(records.map { it.measurement.outcome }).containsOnly(Outcome.Completed)
        assertThat(action.runs).hasSize(4)
    }

    @Test
    suspend fun `a cell stops at its first run that does not complete`() {
        val action = ScriptedAction { _, run ->
            if (run == 2) {
                Measurement(Outcome.TimedOut, verdict = VerificationVerdict.Timeout)
            } else {
                completed
            }
        }

        val records = CampaignRunner(campaignOver("a"), ResultStore(directory), action).run()

        assertThat(records.map { it.measurement.outcome }).containsExactly(Outcome.Completed, Outcome.TimedOut)
        assertThat(action.runs).hasSize(2)
    }

    @Test
    suspend fun `a throwable from a cell is recorded as errored and the campaign goes on`() {
        val action = ScriptedAction { cellModel, _ ->
            if (cellModel == "a") {
                throw StackOverflowError("deep")
            }
            completed
        }

        val records = CampaignRunner(campaignOver("a", "b"), ResultStore(directory), action).run()

        val errored = records.single { it.cell["model"] == "a" }
        assertThat(errored.measurement.outcome).isEqualTo(Outcome.Errored)
        assertThat(errored.measurement.message).isEqualTo("StackOverflowError: deep")
        assertThat(records.filter { it.cell["model"] == "b" }).hasSize(4)
    }

    @Test
    suspend fun `an unavailable cell is recorded without running it`() {
        val action = ScriptedAction(unavailable = setOf("a"))

        val records = CampaignRunner(campaignOver("a"), ResultStore(directory), action).run()

        assertThat(records.single().measurement.outcome).isEqualTo(Outcome.Unavailable)
        assertThat(records.single().measurement.message).isEqualTo("absent")
        assertThat(action.runs).isEmpty()
    }

    @Test
    suspend fun `every run gets its own work directory, deleted afterwards even when the run throws`() {
        val action = ScriptedAction { cellModel, _ ->
            check(cellModel != "b") { "boom" }
            completed
        }

        CampaignRunner(campaignOver("a", "b"), ResultStore(directory), action).run()

        assertThat(action.workDirectories).hasSize(5).doesNotHaveDuplicates()
        assertThat(action.workDirectories).noneMatch { it.exists() }
    }

    @Test
    suspend fun `resume skips completed and timed out cells and retries errored, unavailable and incomplete ones`() {
        val store = ResultStore(directory)
        store.replace(
            records("done", List(4) { Outcome.Completed }) +
                records("timedout", listOf(Outcome.TimedOut)) +
                records("errored", listOf(Outcome.Completed, Outcome.Errored)) +
                records("unavailable", listOf(Outcome.Unavailable)) +
                records("incomplete", listOf(Outcome.Completed, Outcome.Completed)),
        )
        val action = ScriptedAction()

        val records = CampaignRunner(campaignOver("done", "timedout", "errored", "unavailable", "incomplete"), store, action).run()

        assertThat(action.runs.distinct()).containsExactly("errored", "unavailable", "incomplete")
        assertThat(records.groupBy { it.cell.getValue("model") }.mapValues { it.value.size }).containsAllEntriesOf(
            mapOf("done" to 4, "timedout" to 1, "errored" to 4, "unavailable" to 4, "incomplete" to 4),
        )
        assertThat(records.map { it.measurement.outcome }).doesNotContain(Outcome.Errored, Outcome.Unavailable)
    }

    @Test
    suspend fun `resume drops a line cut off by an interrupted write and records of cells outside the campaign`() {
        val store = ResultStore(directory)
        store.replace(records("done", List(4) { Outcome.Completed }) + records("removed", List(4) { Outcome.Completed }))
        store.resultsFile.appendText("{\"campaign\":\"test\",\"cel")
        val action = ScriptedAction()

        val records = CampaignRunner(campaignOver("done"), store, action).run()

        assertThat(action.runs).isEmpty()
        assertThat(records).hasSize(4)
        assertThat(store.resultsFile.readLines()).hasSize(4)
    }
}
