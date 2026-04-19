/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.trace

import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.backend.VerificationTrace
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.SemantifyrVerifier

sealed class TraceValidationResult {
    abstract val verification: VerificationResult

    data class Valid(override val verification: VerificationResult) : TraceValidationResult()
    data class Invalid(override val verification: VerificationResult) : TraceValidationResult()
    data class Inconclusive(override val verification: VerificationResult) : TraceValidationResult()
    data class Errored(override val verification: VerificationResult) : TraceValidationResult()

    val isValid: Boolean get() = this is Valid
    val isInvalid: Boolean get() = this is Invalid

    companion object {
        internal fun from(verification: VerificationResult): TraceValidationResult = when (verification.verdict) {
            VerificationVerdict.Passed -> Valid(verification)
            VerificationVerdict.Failed -> Invalid(verification)
            VerificationVerdict.Inconclusive -> Inconclusive(verification)
            VerificationVerdict.Errored -> Errored(verification)
        }
    }
}

interface TraceValidator<T : VerificationTrace> {
    suspend fun validate(
        verifier: SemantifyrVerifier,
        trace: T,
        progress: ProgressContext = ProgressContext.NoOp,
    ): TraceValidationResult
}

object OxstsWitnessValidator : TraceValidator<VerificationTrace.OxstsWitness> {
    override suspend fun validate(
        verifier: SemantifyrVerifier,
        trace: VerificationTrace.OxstsWitness,
        progress: ProgressContext,
    ): TraceValidationResult {
        val verification = verifier.verify(trace.witness.inlinedOxsts, progress)
        return TraceValidationResult.from(verification)
    }
}
