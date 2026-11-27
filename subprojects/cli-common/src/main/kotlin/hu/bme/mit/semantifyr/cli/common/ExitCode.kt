/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import com.github.ajalt.clikt.core.CliktError
import hu.bme.mit.semantifyr.backend.VerificationVerdict

enum class ExitCode(
    val code: Int,
    val meaning: String,
) {
    Success(0, "Every case passed, or the command completed."),
    Internal(1, "Internal error, or Java could not start."),
    Usage(2, "Usage error: bad command, option, value, or portfolio."),
    Input(3, "Input error: a bad model or library path, or no case selected."),
    Compilation(4, "Compilation error: the model does not parse, validate, or translate."),
    Failed(5, "Failed: a property does not hold."),
    Undecided(6, "Inconclusive or not supported."),
    Timeout(7, "Timeout."),
    Errored(8, "Errored: a backend failed or is unavailable."),
    Output(10, "Output error: the artifact directory is not writable."),
    ;

    companion object {
        fun of(verdict: VerificationVerdict): ExitCode {
            return when (verdict) {
                VerificationVerdict.Passed -> Success
                VerificationVerdict.Failed -> Failed
                VerificationVerdict.Inconclusive -> Undecided
                VerificationVerdict.NotSupported -> Undecided
                VerificationVerdict.Timeout -> Timeout
                VerificationVerdict.Errored -> Errored
            }
        }
    }
}

class CommandExit(
    exitCode: ExitCode,
    message: String? = null,
) : CliktError(
    message?.let {
        "error: $it"
    },
    statusCode = exitCode.code,
)
