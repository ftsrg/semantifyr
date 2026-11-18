/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

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
}
