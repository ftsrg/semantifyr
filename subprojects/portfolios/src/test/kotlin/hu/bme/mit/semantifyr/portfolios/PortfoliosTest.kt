/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backend.SoundnessDirection
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test

class PortfoliosTest {

    @Test
    fun `every portfolio lists its legs`() {
        for (portfolio in Portfolios.all) {
            assertThat(portfolio.legs).describedAs(portfolio.id).isNotEmpty
        }
    }

    @Test
    fun `a single-backend portfolio lists its configuration as its leg`() {
        assertThat(Portfolios.UppaalDefault.legs).extracting(
            {
                it.id
            },
            {
                it.config.soundness
            },
        ).containsExactly(
            tuple("uppaal:default", SoundnessDirection.UnderApproximating),
        )
    }
}
