/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VerificationResultTest {

    @Test
    fun `a result is passed, failed and decisive by its verdict`() {
        val passed = fakeVerificationResult(verdict = VerificationVerdict.Passed)
        val failed = fakeVerificationResult(verdict = VerificationVerdict.Failed)
        val errored = fakeVerificationResult(verdict = VerificationVerdict.Errored)

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
