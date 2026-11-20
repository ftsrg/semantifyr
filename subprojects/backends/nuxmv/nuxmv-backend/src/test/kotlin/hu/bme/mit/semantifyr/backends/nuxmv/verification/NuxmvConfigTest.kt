/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.verification

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class NuxmvConfigTest {

    @Test
    fun `a bound must be positive`() {
        assertThatThrownBy {
            NuxmvConfig.KInductionInvar.copy(bound = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
