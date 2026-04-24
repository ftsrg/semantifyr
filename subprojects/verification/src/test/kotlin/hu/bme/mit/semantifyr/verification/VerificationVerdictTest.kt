/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VerificationVerdictTest {

    @Test
    fun `Passed and Failed are decisive`() {
        assertThat(VerificationVerdict.Passed.isDecisive).isTrue
        assertThat(VerificationVerdict.Failed.isDecisive).isTrue
    }

    @Test
    fun `Errored and Inconclusive are not decisive`() {
        assertThat(VerificationVerdict.Errored.isDecisive).isFalse
        assertThat(VerificationVerdict.Inconclusive.isDecisive).isFalse
    }

    @Test
    fun `VerificationResult helpers reflect verdict`() {
        val metadata = fakeMetadata(caseQualifiedName = "x")
        val passed = VerificationResult(VerificationVerdict.Passed, metadata)
        val failed = VerificationResult(VerificationVerdict.Failed, metadata)
        val errored = VerificationResult(VerificationVerdict.Errored, metadata)

        assertThat(passed.isPassed).isTrue
        assertThat(passed.isFailed).isFalse
        assertThat(passed.isDecisive).isTrue

        assertThat(failed.isPassed).isFalse
        assertThat(failed.isFailed).isTrue
        assertThat(failed.isDecisive).isTrue

        assertThat(errored.isPassed).isFalse
        assertThat(errored.isFailed).isFalse
        assertThat(errored.isDecisive).isFalse
    }
}
