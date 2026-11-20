/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.corpuscheck

import hu.bme.mit.semantifyr.cli.common.CaseFailureLine
import hu.bme.mit.semantifyr.cli.common.CasePhase
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.cli.common.VerifyOutcomeLine
import hu.bme.mit.semantifyr.cli.common.jsonLineFormat
import hu.bme.mit.semantifyr.verifier.CaseManifest
import hu.bme.mit.semantifyr.verifier.ManifestCase
import hu.bme.mit.semantifyr.verifier.ManifestModel
import hu.bme.mit.semantifyr.verifier.ManifestTier
import hu.bme.mit.semantifyr.verifier.ManifestVerdict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

class CorpusCheckerTest {

    @TempDir
    lateinit var corpus: Path

    private val runs = mutableListOf<List<String>>()

    private fun writeManifest(vararg models: ManifestModel) {
        corpus.resolve("manifest.json").writeText(jsonLineFormat.encodeToString(CaseManifest(models.toList())))
    }

    private fun answering(vararg lines: String, exitCode: Int = 0): Launcher {
        return Launcher { _, caseNames ->
            runs += caseNames
            LauncherRun(exitCode, lines.joinToString("\n"))
        }
    }

    private fun outcome(case: String, exitCode: ExitCode): String {
        return jsonLineFormat.encodeToString(VerifyOutcomeLine(case, exitCode.code, "report.json", 1.seconds, 2.seconds))
    }

    private fun statuses(checks: List<CaseCheck>): Map<String, CaseStatus> {
        return checks.associate {
            it.case to it.status
        }
    }

    @Test
    fun `a case passes only when the launcher answers its expected verdict`() {
        writeManifest(
            ManifestModel(
                "Model.gamma",
                cases = listOf(
                    ManifestCase("Holds", ManifestVerdict.Passed),
                    ManifestCase("Violated", ManifestVerdict.Failed),
                    ManifestCase("Slow", ManifestVerdict.Passed),
                    ManifestCase("Wrong", ManifestVerdict.Passed),
                ),
            ),
        )
        val checker = CorpusChecker(
            answering(
                outcome("Model.Holds", ExitCode.Success),
                outcome("Model.Violated", ExitCode.Failed),
                outcome("Model.Slow", ExitCode.Timeout),
                outcome("Model.Wrong", ExitCode.Failed),
            ),
            caseNamespace = null,
        )

        val checks = checker.check(checker.select(corpus).single())

        assertThat(statuses(checks)).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "Holds" to CaseStatus.Reached,
                "Violated" to CaseStatus.Reached,
                "Slow" to CaseStatus.Missed,
                "Wrong" to CaseStatus.Missed,
            ),
        )
        assertThat(checks).filteredOn {
            !it.passing
        }.hasSize(2)
    }

    @Test
    fun `a case without an answer and a case the manifest does not list fail`() {
        writeManifest(
            ManifestModel(
                "model.sysml",
                cases = listOf(
                    ManifestCase("Compiles", ManifestVerdict.Passed),
                    ManifestCase("Silent", ManifestVerdict.Passed),
                ),
            ),
        )
        val failure = jsonLineFormat.encodeToString(CaseFailureLine("semantifyr::sysml::model::Compiles", ExitCode.Compilation.code, CasePhase.Compilation, "no such state"))
        val checker = CorpusChecker(
            answering(
                failure,
                outcome("semantifyr::sysml::model::Unlisted", ExitCode.Success),
                exitCode = ExitCode.Compilation.code,
            ),
            caseNamespace = null,
        )

        val checks = checker.check(checker.select(corpus).single())

        assertThat(statuses(checks)).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "Compiles" to CaseStatus.Missed,
                "Silent" to CaseStatus.Unanswered,
                "semantifyr::sysml::model::Unlisted" to CaseStatus.Unlisted,
            ),
        )
        assertThat(checks).noneMatch {
            it.passing
        }
    }

    @Test
    fun `nightly cases are left out of the run and a model of nightly cases alone is not run`() {
        writeManifest(
            ManifestModel(
                "mixed.sysml",
                cases = listOf(
                    ManifestCase("Fast", ManifestVerdict.Passed, ManifestTier.Integration),
                    ManifestCase("Slow", ManifestVerdict.Unknown, ManifestTier.Nightly),
                ),
            ),
            ManifestModel(
                "slow.sysml",
                cases = listOf(
                    ManifestCase("Slower", ManifestVerdict.Passed, ManifestTier.Nightly),
                ),
            ),
        )
        val checker = CorpusChecker(answering(outcome("semantifyr::sysml::model::Fast", ExitCode.Success)), caseNamespace = "semantifyr::sysml::model::")

        val checks = checker.select(corpus).flatMap {
            checker.check(it)
        }

        assertThat(runs).containsExactly(listOf("semantifyr::sysml::model::Fast"))
        assertThat(statuses(checks)).containsExactlyEntriesOf(mapOf("Fast" to CaseStatus.Reached))
    }

    @Test
    fun `a model whose cases the manifest leaves partly out is not run without a namespace`() {
        writeManifest(
            ManifestModel(
                "mixed.sysml",
                cases = listOf(
                    ManifestCase("Fast", ManifestVerdict.Passed, ManifestTier.Integration),
                    ManifestCase("Slow", ManifestVerdict.Passed, ManifestTier.Nightly),
                ),
            ),
        )
        val checker = CorpusChecker(answering(), caseNamespace = null)

        val checks = checker.check(checker.select(corpus).single())

        assertThat(runs).isEmpty()
        assertThat(statuses(checks)).containsExactlyEntriesOf(mapOf("Fast" to CaseStatus.Unanswered))
    }

    @Test
    fun `a model on a library variant is left out without failing`() {
        writeManifest(
            ManifestModel(
                "fifo.sysml",
                variant = "Fifo",
                cases = listOf(
                    ManifestCase("Queued", ManifestVerdict.Passed, ManifestTier.Integration),
                ),
            ),
        )
        val checker = CorpusChecker(answering(), caseNamespace = null)

        val checks = checker.check(checker.select(corpus).single())

        assertThat(runs).isEmpty()
        assertThat(checks).singleElement().matches {
            it.status == CaseStatus.LeftOut && it.passing
        }
    }

    @Test
    fun `a case outside the nightly tier without a verdict fails naming the case and the other cases run`() {
        writeManifest(
            ManifestModel(
                "model.sysml",
                cases = listOf(
                    ManifestCase("Known", ManifestVerdict.Passed, ManifestTier.Integration),
                    ManifestCase("Unjudged", ManifestVerdict.Unknown, ManifestTier.Integration),
                ),
            ),
        )
        val checker = CorpusChecker(answering(outcome("semantifyr::sysml::model::Known", ExitCode.Success)), caseNamespace = "semantifyr::sysml::model::")

        val checks = checker.check(checker.select(corpus).single())

        assertThat(runs).containsExactly(listOf("semantifyr::sysml::model::Known"))
        assertThat(statuses(checks)).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "Known" to CaseStatus.Reached,
                "Unjudged" to CaseStatus.NoVerdict,
            ),
        )
        assertThat(
            checks.single {
                it.status == CaseStatus.NoVerdict
            }.passing,
        ).isFalse
    }

    @Test
    fun `an output line that is neither an outcome nor a failure fails naming the line`() {
        writeManifest(
            ManifestModel(
                "Model.gamma",
                cases = listOf(
                    ManifestCase("Holds", ManifestVerdict.Passed),
                ),
            ),
        )
        val checker = CorpusChecker(answering(outcome("Model.Holds", ExitCode.Success), "Compiling Model.Holds"), caseNamespace = null)

        val checks = checker.check(checker.select(corpus).single())

        assertThat(statuses(checks)).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "Holds" to CaseStatus.Reached,
                "Compiling Model.Holds" to CaseStatus.Unreadable,
            ),
        )
        val unreadable = checks.single {
            it.status == CaseStatus.Unreadable
        }
        assertThat(unreadable.passing).isFalse
        assertThat(unreadable.toString()).contains("Compiling Model.Holds")
    }
}
