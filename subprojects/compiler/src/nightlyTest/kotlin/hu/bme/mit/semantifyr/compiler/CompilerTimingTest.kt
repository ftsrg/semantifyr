/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConeOfInfluenceAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ReachingDefinitionsAnalysis
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// each limit is ten times the higher median of two runs at load 9 to 13 on 2026-11-01, so only a regression of an order of magnitude fails
class CompilerTimingTest {

    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()

        private val metrics by lazy {
            val caseClass = SysMLCrossroads.caseClass(injector.getInstance(SemantifyrLoader::class.java))
            val compiler = SemantifyrCompiler(injector, ArtifactConfig.NONE)
            var run = 0
            // each compilation adds its inlined model to the resource set under its output directory
            warmRepetitions {
                run++
                compiler.compile(caseClass, Path("build", "test-artifacts", "CompilerTimingTest", "$run")).compilationMetrics
            }
        }

        private fun analysisDuration(analysis: Class<*>): Duration {
            return metrics.map { compilation ->
                compilation.flatteningOptimization.analyses.single {
                    it.name == analysis.simpleName
                }.duration
            }.median()
        }
    }

    @Test
    fun `the cone of influence analysis of crossroads stays within its limit`() {
        assertThat(analysisDuration(ConeOfInfluenceAnalysis::class.java)).isLessThan(500.milliseconds)
    }

    @Test
    fun `the reaching definitions analysis of crossroads stays within its limit`() {
        assertThat(analysisDuration(ReachingDefinitionsAnalysis::class.java)).isLessThan(1500.milliseconds)
    }

    @Test
    fun `inlining crossroads stays within its limit`() {
        assertThat(metrics.map { it.inliningDuration }.median()).isLessThan(10.seconds)
    }

    @Test
    fun `flattening crossroads stays within its limit`() {
        assertThat(metrics.map { it.flatteningDuration - it.flatteningOptimization.duration }.median()).isLessThan(500.milliseconds)
    }
}
