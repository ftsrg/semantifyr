/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.verifier.VerificationResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

enum class VerifyOutput {
    Text,
    Quiet,
    Json,
}

fun SuspendingCliktCommand.verifyOutputOption() = mutuallyExclusiveOptions(
    option().switch("-q" to VerifyOutput.Quiet, "--quiet" to VerifyOutput.Quiet)
        .help("Print only the verdict of each case, one per line."),
    option().switch("--json" to VerifyOutput.Json)
        .help("Print one JSON object per case, one per line, with the path of its report.json."),
).single().default(VerifyOutput.Text)

class CaseOutcome(
    val result: VerificationResult,
    val report: Path,
)

suspend fun SuspendingCliktCommand.verifyCase(
    output: VerifyOutput,
    caseName: String,
    verify: suspend () -> CaseOutcome,
): ExitCode {
    if (output == VerifyOutput.Text) {
        echo("Verifying $caseName ...")
    }
    val outcome = try {
        verify()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        val message = exception.message ?: exception::class.simpleName
        when (output) {
            VerifyOutput.Text -> {
                echo("  COMPILATION_ERROR")
            }
            VerifyOutput.Quiet -> {
                echo("COMPILATION_ERROR")
            }
            VerifyOutput.Json -> {
                echo(
                    buildJsonObject {
                        put("case", caseName)
                        put("exitCode", ExitCode.Compilation.code)
                        put("error", message)
                    },
                )
            }
        }
        echo("Compiling $caseName failed: $message", err = true)
        return ExitCode.Compilation
    }
    val result = outcome.result
    val exitCode = ExitCode.of(result.verdict)
    val report = outcome.report.toAbsolutePath().normalize()
    when (output) {
        VerifyOutput.Text -> {
            val message = result.message?.let { " - $it" } ?: ""
            echo("  ${verdictTag(result.verdict)} (${result.metrics.totalDuration})$message")
            echo("  report: $report")
        }
        VerifyOutput.Quiet -> {
            echo(verdictTag(result.verdict))
        }
        VerifyOutput.Json -> {
            echo(
                buildJsonObject {
                    put("case", caseName)
                    put("exitCode", exitCode.code)
                    put("report", report.toString())
                },
            )
        }
    }
    return exitCode
}

private fun verdictTag(verdict: VerificationVerdict): String {
    return when (verdict) {
        VerificationVerdict.Passed -> "PASSED"
        VerificationVerdict.Failed -> "FAILED"
        VerificationVerdict.Inconclusive -> "INCONCLUSIVE"
        VerificationVerdict.Timeout -> "TIMEOUT"
        VerificationVerdict.Errored -> "ERRORED"
        VerificationVerdict.NotSupported -> "NOT_SUPPORTED"
    }
}
