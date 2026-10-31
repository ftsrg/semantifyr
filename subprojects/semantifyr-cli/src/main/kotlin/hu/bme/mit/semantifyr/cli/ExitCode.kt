/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import com.github.ajalt.clikt.core.CliktError
import hu.bme.mit.semantifyr.backend.VerificationVerdict

// the website's CLI page documents these numbers; scripts rely on them
enum class ExitCode(val code: Int) {
    Success(0),
    Usage(2),
    Input(3),
    Compilation(4),
    Failed(5),
    Undecided(6),
    Timeout(7),
    Errored(8),
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
) : CliktError(message, statusCode = exitCode.code)
