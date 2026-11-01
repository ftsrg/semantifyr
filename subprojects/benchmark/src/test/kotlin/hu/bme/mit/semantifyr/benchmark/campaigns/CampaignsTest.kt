/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.campaigns

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.benchmark.verification.Engine
import hu.bme.mit.semantifyr.benchmark.verification.OptimizationLevel
import hu.bme.mit.semantifyr.benchmark.verification.VerificationAxes
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

@InjectWithOxsts
class CampaignsTest {

    @Inject
    private lateinit var injector: Injector

    private val sources by lazy {
        BenchmarkSources(injector, Path("../frontends/sysml/models/examples"), Path("../../oxsts-test-models"))
    }

    @Test
    fun `optimizer-passes runs every crossroads cell at every leave-one-out level`() {
        val crossroads = Campaigns.crossroads.create(sources).cells()
        val passes = Campaigns.optimizerPasses.create(sources).cells()

        assertThat(passes).hasSize(crossroads.size * OptimizationLevel.leaveOneOut.size)
        assertThat(passes.map { it.coordinates - VerificationAxes.optimization.name }.distinct())
            .containsExactlyElementsOf(crossroads.map { it.coordinates - VerificationAxes.optimization.name })
    }

    @Test
    fun `backends runs every corpus case but the full spacecraft's on all four engines`() {
        val cells = Campaigns.backends.create(sources).cells()
        val models = sources.sysml.models.filter { it.name != "spacecraft.sysml" }

        assertThat(cells).hasSize(Engine.entries.size * models.sumOf { it.cases.size })
        assertThat(cells.map { it[VerificationAxes.engine] }.distinct()).containsExactlyElementsOf(Engine.entries)
    }

    @Test
    fun `smoke is one run of the smoke model`() {
        val smoke = Campaigns.smoke.create(sources)

        assertThat(smoke.cells().map { it.key }).containsExactly(
            "model=smoke.oxsts/case=CounterReachesThree/engine=theta/backend=theta-cegar-expl/optimization=all",
        )
        assertThat(smoke.repetitions).isEqualTo(1)
        assertThat(smoke.warmups).isZero()
    }

    @Test
    fun `campaigns have distinct names`() {
        assertThat(Campaigns.all.map { it.name }).doesNotHaveDuplicates()
        assertThat(Campaigns.byName("optimizer-passes")).isSameAs(Campaigns.optimizerPasses)
    }
}
