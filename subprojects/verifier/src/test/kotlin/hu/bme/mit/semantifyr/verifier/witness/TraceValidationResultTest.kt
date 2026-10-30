/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.verifier.fakeVerificationResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TraceValidationResultTest {

    @Test
    fun `Passed maps to Valid`() {
        val mapped = WitnessValidationResult.from(fakeVerificationResult(verdict = VerificationVerdict.Passed))
        assertThat(mapped).isInstanceOf(WitnessValidationResult.Valid::class.java)
        assertThat(mapped.isValid).isTrue
        assertThat(mapped.isInvalid).isFalse
    }

    @Test
    fun `Failed maps to Invalid`() {
        val mapped = WitnessValidationResult.from(fakeVerificationResult(verdict = VerificationVerdict.Failed))
        assertThat(mapped).isInstanceOf(WitnessValidationResult.Invalid::class.java)
        assertThat(mapped.isInvalid).isTrue
        assertThat(mapped.isValid).isFalse
    }

    @Test
    fun `Inconclusive maps to Inconclusive`() {
        val mapped = WitnessValidationResult.from(fakeVerificationResult(verdict = VerificationVerdict.Inconclusive))
        assertThat(mapped).isInstanceOf(WitnessValidationResult.Inconclusive::class.java)
        assertThat(mapped.isValid).isFalse
        assertThat(mapped.isInvalid).isFalse
    }

    @Test
    fun `Errored maps to Errored`() {
        val mapped = WitnessValidationResult.from(fakeVerificationResult(verdict = VerificationVerdict.Errored))
        assertThat(mapped).isInstanceOf(WitnessValidationResult.Errored::class.java)
        assertThat(mapped.isValid).isFalse
        assertThat(mapped.isInvalid).isFalse
    }

    @Test
    fun `from preserves the underlying verification result`() {
        val verification = fakeVerificationResult(verdict = VerificationVerdict.Passed)
        val mapped = WitnessValidationResult.from(verification)
        assertThat(mapped.verification).isSameAs(verification)
    }
}
