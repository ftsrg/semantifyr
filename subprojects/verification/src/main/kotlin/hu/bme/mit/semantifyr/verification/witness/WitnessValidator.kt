/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.witness

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.SemantifyrVerifier
import hu.bme.mit.semantifyr.verification.VerificationResult
import hu.bme.mit.semantifyr.verification.VerificationTrace

sealed class WitnessValidationResult {
    abstract val verification: VerificationResult

    data class Valid(override val verification: VerificationResult) : WitnessValidationResult()
    data class Invalid(override val verification: VerificationResult) : WitnessValidationResult()
    data class Inconclusive(override val verification: VerificationResult) : WitnessValidationResult()
    data class Errored(override val verification: VerificationResult) : WitnessValidationResult()

    val isValid: Boolean get() = this is Valid
    val isInvalid: Boolean get() = this is Invalid

    companion object {
        internal fun from(verification: VerificationResult): WitnessValidationResult = when (verification.verdict) {
            VerificationVerdict.Passed -> Valid(verification)
            VerificationVerdict.Failed -> Invalid(verification)
            VerificationVerdict.Inconclusive -> Inconclusive(verification)
            VerificationVerdict.Errored -> Errored(verification)
            // Validator-side abstention is indeterminate from the witness's perspective.
            VerificationVerdict.NotSupported -> Inconclusive(verification)
        }
    }
}

interface WitnessValidator<T : VerificationTrace> {
    suspend fun validate(
        verifier: SemantifyrVerifier,
        trace: T,
        progress: ProgressContext = ProgressContext.NoOp,
    ): WitnessValidationResult
}

object OxstsWitnessValidator : WitnessValidator<VerificationTrace.OxstsWitness> {
    override suspend fun validate(
        verifier: SemantifyrVerifier,
        trace: VerificationTrace.OxstsWitness,
        progress: ProgressContext,
    ): WitnessValidationResult {
        val verification = verifier.verify(trace.backAnnotatedWitness, progress)
        return WitnessValidationResult.from(verification)
    }
}
