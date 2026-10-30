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
import hu.bme.mit.semantifyr.verifier.fakeVerificationResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify

class WitnessValidatorTest {

    private fun fakeTrace(inlined: InlinedOxsts): Trace {
        return Trace(
            backAnnotatedModel = inlined,
            witnessState = WitnessState(
                initialStep = WitnessStateStep(emptyList()),
                steps = emptyList(),
            ),
            callTrace = CallTrace(
                initialStep = CallTraceStep(emptyList()),
                steps = emptyList(),
            ),
        )
    }

    @Test
    suspend fun `validate re-runs verification on the back-annotated model`() {
        val inlined = mock<InlinedOxsts>()
        val trace = fakeTrace(inlined)
        val verification = fakeVerificationResult(verdict = VerificationVerdict.Passed)
        val verifier = mock<SemantifyrVerifier> {
            onBlocking { verify(inlined, ProgressContext.NoOp) } doReturn verification
        }

        val result = WitnessValidator().validate(verifier, trace)

        verify(verifier).verify(same(inlined), eq(ProgressContext.NoOp))
        assertThat(result).isInstanceOf(WitnessValidationResult.Valid::class.java)
        assertThat(result.verification).isSameAs(verification)
    }

    @Test
    suspend fun `validate propagates Failed verdict as Invalid`() {
        val inlined = mock<InlinedOxsts>()
        val trace = fakeTrace(inlined)
        val verifier = mock<SemantifyrVerifier> {
            onBlocking { verify(inlined, ProgressContext.NoOp) } doReturn fakeVerificationResult(verdict = VerificationVerdict.Failed)
        }

        val result = WitnessValidator().validate(verifier, trace)

        assertThat(result).isInstanceOf(WitnessValidationResult.Invalid::class.java)
    }

    @Test
    suspend fun `validate forwards a non-default progress context`() {
        val inlined = mock<InlinedOxsts>()
        val trace = fakeTrace(inlined)
        val progress = ProgressContext.NoOp.child("ctx")
        val verification = fakeVerificationResult(verdict = VerificationVerdict.Inconclusive)
        val verifier = mock<SemantifyrVerifier> {
            onBlocking { verify(inlined, progress) } doReturn verification
        }

        val result = WitnessValidator().validate(verifier, trace, progress)

        verify(verifier).verify(same(inlined), same(progress))
        assertThat(result).isInstanceOf(WitnessValidationResult.Inconclusive::class.java)
    }
}
