/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.trace

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.SemantifyrVerifier
import hu.bme.mit.semantifyr.verification.VerificationResult
import hu.bme.mit.semantifyr.verification.VerificationTrace
import hu.bme.mit.semantifyr.verification.fakeMetadata
import hu.bme.mit.semantifyr.verification.witness.OxstsClassAssumptionWitness
import hu.bme.mit.semantifyr.verification.witness.OxstsWitnessValidator
import hu.bme.mit.semantifyr.verification.witness.SerializableTraceData
import hu.bme.mit.semantifyr.verification.witness.SerializableTraceStep
import hu.bme.mit.semantifyr.verification.witness.WitnessValidationResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify

class OxstsWitnessValidatorTest {

    private fun fakeWitness(inlined: InlinedOxsts): VerificationTrace.OxstsWitness {
        return VerificationTrace.OxstsWitness(
            classWitness = mock<OxstsClassAssumptionWitness>(),
            backAnnotatedWitness = inlined,
            callTrace = SerializableTraceData(
                initialStep = SerializableTraceStep(emptyList(), emptyList()),
                steps = emptyList(),
            ),
        )
    }

    @Test
    suspend fun `validate re-runs verification on the witness inlined OXSTS`() {
        val inlined = mock<InlinedOxsts>()
        val trace = fakeWitness(inlined)
        val verification = VerificationResult(
            verdict = VerificationVerdict.Passed,
            metadata = fakeMetadata(),
        )
        val verifier = mock<SemantifyrVerifier> {
            onBlocking { verify(inlined, ProgressContext.NoOp) } doReturn verification
        }

        val result = OxstsWitnessValidator.validate(verifier, trace)

        verify(verifier).verify(same(inlined), eq(ProgressContext.NoOp))
        assertThat(result).isInstanceOf(WitnessValidationResult.Valid::class.java)
        assertThat(result.verification).isSameAs(verification)
    }

    @Test
    suspend fun `validate propagates Failed verdict as Invalid`() {
        val inlined = mock<InlinedOxsts>()
        val trace = fakeWitness(inlined)
        val verifier = mock<SemantifyrVerifier> {
            onBlocking { verify(inlined, ProgressContext.NoOp) } doReturn VerificationResult(
                verdict = VerificationVerdict.Failed,
                metadata = fakeMetadata(),
            )
        }

        val result = OxstsWitnessValidator.validate(verifier, trace)

        assertThat(result).isInstanceOf(WitnessValidationResult.Invalid::class.java)
    }

    @Test
    suspend fun `validate forwards a non-default progress context`() {
        val inlined = mock<InlinedOxsts>()
        val trace = fakeWitness(inlined)
        val progress = ProgressContext.NoOp.child("ctx")
        val verification = VerificationResult(
            verdict = VerificationVerdict.Inconclusive,
            metadata = fakeMetadata(),
        )
        val verifier = mock<SemantifyrVerifier> {
            onBlocking { verify(inlined, progress) } doReturn verification
        }

        val result = OxstsWitnessValidator.validate(verifier, trace, progress)

        verify(verifier).verify(same(inlined), same(progress))
        assertThat(result).isInstanceOf(WitnessValidationResult.Inconclusive::class.java)
    }
}
