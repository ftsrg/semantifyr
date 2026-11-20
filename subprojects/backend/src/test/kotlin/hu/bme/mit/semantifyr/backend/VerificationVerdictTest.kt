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
    fun `every other verdict is not decisive`() {
        val undecided = VerificationVerdict.entries - VerificationVerdict.Passed - VerificationVerdict.Failed
        for (verdict in undecided) {
            assertThat(verdict.isDecisive).describedAs(verdict.name).isFalse
        }
    }
}
