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
import hu.bme.mit.semantifyr.verifier.VerificationOutcome
import kotlinx.coroutines.runBlocking

sealed class WitnessValidationResult {
    abstract val outcome: VerificationOutcome

    class Valid(override val outcome: VerificationOutcome) : WitnessValidationResult()
    class Invalid(override val outcome: VerificationOutcome) : WitnessValidationResult()
    class Inconclusive(override val outcome: VerificationOutcome) : WitnessValidationResult()
    class Errored(override val outcome: VerificationOutcome) : WitnessValidationResult()

    val isValid = this is Valid
    val isInvalid = this is Invalid

    companion object {
        @JvmStatic
        fun from(outcome: VerificationOutcome): WitnessValidationResult = when (outcome.report.result.verdict) {
            VerificationVerdict.Passed -> Valid(outcome)
            VerificationVerdict.Failed -> Invalid(outcome)
            VerificationVerdict.Inconclusive -> Inconclusive(outcome)
            VerificationVerdict.Timeout -> Inconclusive(outcome)
            VerificationVerdict.Errored -> Errored(outcome)
            VerificationVerdict.NotSupported -> Inconclusive(outcome)
        }
    }
}

class WitnessValidator {

    suspend fun validate(
        verifier: SemantifyrVerifier,
        backAnnotatedModel: InlinedOxsts,
        progress: ProgressContext = ProgressContext.Silent,
    ): WitnessValidationResult {
        return WitnessValidationResult.from(verifier.verify(backAnnotatedModel, progress))
    }

    @JvmOverloads
    fun validateBlocking(
        verifier: SemantifyrVerifier,
        backAnnotatedModel: InlinedOxsts,
        progress: ProgressContext = ProgressContext.Silent,
    ): WitnessValidationResult {
        return runBlocking {
            validate(verifier, backAnnotatedModel, progress)
        }
    }

}
