/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import com.github.ajalt.clikt.core.CliktError
import hu.bme.mit.semantifyr.backend.VerificationVerdict

// the website's command-line page is generated from these codes and meanings; scripts rely on them
enum class ExitCode(
    val code: Int,
    val meaning: String,
) {
    Success(0, "Every selected case passed, or the command completed."),
    Internal(1, "Internal error: an unexpected exception, or the launcher could not start Java."),
    Usage(2, "Usage error: an unknown command or option, a missing or invalid value, conflicting options, an unknown portfolio."),
    Input(3, "Input error: a model or library path does not exist, is not readable, or is not a model file of the launcher (`.oxsts`, `.sysml`, `.gamma` or `.gcd`) or, where a directory is allowed, a directory holding one, or the case selection names or matches no case."),
    Compilation(4, "Compilation error: the model does not parse, validate or translate, or a case does not compile."),
    Failed(5, "Failed: a property does not hold."),
    Undecided(6, "Inconclusive or NotSupported: a backend could not decide a case."),
    Timeout(7, "Timeout."),
    Errored(8, "Errored: a backend failed, or the portfolio has no available backend."),
    BackAnnotation(9, "Back-annotation error: a backend returned a witness that could not be back-annotated."),
    Output(10, "Output error: the artifact directory cannot be created or written."),
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
