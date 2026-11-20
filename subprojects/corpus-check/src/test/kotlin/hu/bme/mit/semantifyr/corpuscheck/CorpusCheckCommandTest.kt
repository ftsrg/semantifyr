/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.corpuscheck

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.cli.common.VerifyOutcomeLine
import hu.bme.mit.semantifyr.cli.common.jsonLineFormat
import hu.bme.mit.semantifyr.verifier.CaseManifest
import hu.bme.mit.semantifyr.verifier.ManifestCase
import hu.bme.mit.semantifyr.verifier.ManifestModel
import hu.bme.mit.semantifyr.verifier.ManifestVerdict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

class CorpusCheckCommandTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `a run with a failed case exits with 1`() {
        val result = check(ExitCode.Failed)

        assertThat(result.statusCode).describedAs(result.output).isEqualTo(1)
        assertThat(result.stdout).contains("1 of 1 cases failed")
    }

    @Test
    fun `a run whose cases all pass exits with 0`() {
        val result = check(ExitCode.Success)

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(result.stdout).contains("0 of 1 cases failed")
    }

    private fun check(answer: ExitCode): CliktCommandTestResult {
        val corpus = directory.resolve("corpus").createDirectories()
        val manifest = CaseManifest(
            listOf(
                ManifestModel(
                    "Model.gamma",
                    cases = listOf(
                        ManifestCase("Holds", ManifestVerdict.Passed),
                    ),
                ),
            ),
        )
        corpus.resolve("manifest.json").writeText(jsonLineFormat.encodeToString(manifest))
        val outcome = VerifyOutcomeLine("Model.Holds", answer.code, "report.json", 1.seconds, 2.seconds)
        val command = CorpusCheckCommand { _, _, _ ->
            Launcher { _, _ ->
                LauncherRun(answer.code, jsonLineFormat.encodeToString(outcome))
            }
        }
        return command.test(
            listOf(
                "--launcher",
                directory.resolve("launcher").createFile().toString(),
                "--corpus",
                corpus.toString(),
                "-d",
                directory.resolve("artifacts").toString(),
            ),
        )
    }
}
