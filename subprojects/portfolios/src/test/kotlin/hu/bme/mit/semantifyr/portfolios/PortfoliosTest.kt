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
    fun `a racing portfolio lists each leg with its soundness direction`() {
        assertThat(Portfolios.SmartFull.legs).extracting("id", "soundness").contains(
            tuple("theta:cegar-expl", SoundnessDirection.Exact),
            tuple("theta:cegar-expl-pred-combined", SoundnessDirection.Exact),
            tuple("theta:chc", SoundnessDirection.OverApproximating),
            tuple("nuxmv:kinduction-invar", SoundnessDirection.Exact),
            tuple("nuxmv:ic3-invar", SoundnessDirection.Exact),
            tuple("spin:safe-dfs", SoundnessDirection.UnderApproximating),
            tuple("uppaal:default", SoundnessDirection.UnderApproximating),
            tuple("uppaal:over-approximation", SoundnessDirection.OverApproximating),
        )
    }

    @Test
    fun `a single-backend portfolio lists its configuration as its leg`() {
        assertThat(Portfolios.UppaalDefault.legs).extracting("id", "soundness").containsExactly(
            tuple("uppaal:default", SoundnessDirection.UnderApproximating),
        )
    }
}
