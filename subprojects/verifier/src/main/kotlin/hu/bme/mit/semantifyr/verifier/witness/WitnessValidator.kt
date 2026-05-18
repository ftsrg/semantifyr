/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.Trace
import hu.bme.mit.semantifyr.verifier.VerificationResult
import kotlinx.coroutines.runBlocking

sealed class WitnessValidationResult {
    abstract val verification: VerificationResult

    data class Valid(override val verification: VerificationResult) : WitnessValidationResult()
    data class Invalid(override val verification: VerificationResult) : WitnessValidationResult()
    data class Inconclusive(override val verification: VerificationResult) : WitnessValidationResult()
    data class Errored(override val verification: VerificationResult) : WitnessValidationResult()

    val isValid = this is Valid
    val isInvalid = this is Invalid

    companion object {
        @JvmStatic
        fun from(verification: VerificationResult): WitnessValidationResult = when (verification.verdict) {
            VerificationVerdict.Passed -> Valid(verification)
            VerificationVerdict.Failed -> Invalid(verification)
            VerificationVerdict.Inconclusive -> Inconclusive(verification)
            VerificationVerdict.Errored -> Errored(verification)
            VerificationVerdict.NotSupported -> Inconclusive(verification)
        }
    }
}

class WitnessValidator {

    suspend fun validate(
        verifier: SemantifyrVerifier,
        trace: Trace,
        progress: ProgressContext = ProgressContext.NoOp,
    ): WitnessValidationResult {
        return validate(verifier, trace.backAnnotatedModel, progress)
    }

    suspend fun validate(
        verifier: SemantifyrVerifier,
        backAnnotatedModel: InlinedOxsts,
        progress: ProgressContext = ProgressContext.NoOp,
    ): WitnessValidationResult {
        val verification = verifier.verify(backAnnotatedModel, progress)
        return WitnessValidationResult.from(verification)
    }

    @JvmOverloads
    fun validateBlocking(
        verifier: SemantifyrVerifier,
        trace: Trace,
        progress: ProgressContext = ProgressContext.NoOp,
    ): WitnessValidationResult {
        return runBlocking {
            validate(verifier, trace, progress)
        }
    }

    @JvmOverloads
    fun validateBlocking(
        verifier: SemantifyrVerifier,
        backAnnotatedModel: InlinedOxsts,
        progress: ProgressContext = ProgressContext.NoOp,
    ): WitnessValidationResult {
        return runBlocking {
            validate(verifier, backAnnotatedModel, progress)
        }
    }

}
