/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.trace

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.verification.VerificationResult
import hu.bme.mit.semantifyr.verification.fakeMetadata
import hu.bme.mit.semantifyr.verification.witness.WitnessValidationResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TraceValidationResultTest {

    private fun result(verdict: VerificationVerdict): VerificationResult {
        return VerificationResult(verdict = verdict, metadata = fakeMetadata())
    }

    @Test
    fun `Passed maps to Valid`() {
        val mapped = WitnessValidationResult.from(result(VerificationVerdict.Passed))
        assertThat(mapped).isInstanceOf(WitnessValidationResult.Valid::class.java)
        assertThat(mapped.isValid).isTrue
        assertThat(mapped.isInvalid).isFalse
    }

    @Test
    fun `Failed maps to Invalid`() {
        val mapped = WitnessValidationResult.from(result(VerificationVerdict.Failed))
        assertThat(mapped).isInstanceOf(WitnessValidationResult.Invalid::class.java)
        assertThat(mapped.isInvalid).isTrue
        assertThat(mapped.isValid).isFalse
    }

    @Test
    fun `Inconclusive maps to Inconclusive`() {
        val mapped = WitnessValidationResult.from(result(VerificationVerdict.Inconclusive))
        assertThat(mapped).isInstanceOf(WitnessValidationResult.Inconclusive::class.java)
        assertThat(mapped.isValid).isFalse
        assertThat(mapped.isInvalid).isFalse
    }

    @Test
    fun `Errored maps to Errored`() {
        val mapped = WitnessValidationResult.from(result(VerificationVerdict.Errored))
        assertThat(mapped).isInstanceOf(WitnessValidationResult.Errored::class.java)
        assertThat(mapped.isValid).isFalse
        assertThat(mapped.isInvalid).isFalse
    }

    @Test
    fun `from preserves the underlying verification result`() {
        val verification = result(VerificationVerdict.Passed)
        val mapped = WitnessValidationResult.from(verification)
        assertThat(mapped.verification).isSameAs(verification)
    }
}
