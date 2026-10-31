/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioLeg
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PortfoliosTest {

    @Test
    fun `every portfolio lists its legs`() {
        for (portfolio in Portfolios.all) {
            assertThat(portfolio.legs).describedAs(portfolio.id).isNotEmpty
        }
    }

    @Test
    fun `a racing portfolio lists each leg with its soundness direction`() {
        assertThat(Portfolios.SmartFull.legs).contains(
            PortfolioLeg("theta:cegar-expl-pred-combined", SoundnessDirection.Exact),
            PortfolioLeg("spin:safe-dfs", SoundnessDirection.UnderApproximating),
        )
    }

    @Test
    fun `a single-backend portfolio lists its configuration as its leg`() {
        assertThat(Portfolios.UppaalDefault.legs).containsExactly(
            PortfolioLeg("uppaal:default", SoundnessDirection.UnderApproximating),
        )
    }
}
